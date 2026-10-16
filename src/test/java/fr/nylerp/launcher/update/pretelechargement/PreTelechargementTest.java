package fr.nylerp.launcher.update.pretelechargement;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.nylerp.launcher.config.AppPaths;
import fr.nylerp.launcher.config.ModeDeJeu;
import fr.nylerp.launcher.univers.PreTelechargeur.Etat;
import fr.nylerp.launcher.update.ModpackUpdater;
import fr.nylerp.launcher.util.Hashing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pré-téléchargement contre un serveur HTTP local : reprise, serveur sans reprise, empreinte
 * fausse, annulation, plafond de débit, fond qui cède au jeu, et surtout « pas de double
 * téléchargement » : un pack pré-téléchargé puis synchronisé normalement ne retélécharge RIEN.
 *
 * <p>Isolation : {@code user.home} pointe sur un dossier temporaire (AppPaths le relit à chaque
 * appel), les manifestes et les fichiers sont servis par {@code 127.0.0.1}.
 */
class PreTelechargementTest {

    private static final Gson GSON = new Gson();

    private String vraiHome;
    private Path home;
    private HttpServer serveur;
    private String base;

    /** Fichiers servis : chemin → contenu. */
    private final Map<String, byte[]> fichiers = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> requetes = new ConcurrentHashMap<>();
    private final List<String> plages = new CopyOnWriteArrayList<>();
    private final AtomicLong octetsServis = new AtomicLong();
    private volatile boolean repriseAcceptee = true;
    private volatile long pauseParBlocMs = 0;
    private PreTelechargeurPack pack;

    @BeforeEach
    void preparer() throws Exception {
        vraiHome = System.getProperty("user.home");
        home = Files.createTempDirectory("nyle-pretelechargement-test");
        System.setProperty("user.home", home.toString());
        serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext("/", this::servir);
        serveur.setExecutor(Executors.newCachedThreadPool());
        serveur.start();
        base = "http://127.0.0.1:" + serveur.getAddress().getPort() + "/";
        PlanPack.baseManifestesTest = base;
        TelechargeurFichier.attentesMs = new long[]{50, 50, 50};
    }

    @AfterEach
    void nettoyer() throws Exception {
        if (pack != null) pack.fermer();
        serveur.stop(0);
        PlanPack.baseManifestesTest = null;
        TelechargeurFichier.attentesMs = new long[]{2_000, 5_000, 15_000};
        champManifesteSync().set(null, null);
        System.setProperty("user.home", vraiHome);
    }

    // ---- Serveur local ---------------------------------------------------------------------

    private void servir(HttpExchange ex) throws IOException {
        String chemin = ex.getRequestURI().getPath();
        requetes.computeIfAbsent(chemin, k -> new AtomicInteger()).incrementAndGet();
        byte[] corps = fichiers.get(chemin);
        if (corps == null) {
            ex.sendResponseHeaders(404, -1);
            ex.close();
            return;
        }
        int debut = 0;
        String range = ex.getRequestHeaders().getFirst("Range");
        if (range != null) plages.add(chemin + " " + range);
        if (range != null && repriseAcceptee && range.startsWith("bytes=")) {
            debut = Integer.parseInt(range.substring(6, range.indexOf('-')));
            if (debut >= corps.length) {
                ex.sendResponseHeaders(416, -1);
                ex.close();
                return;
            }
            ex.getResponseHeaders().add("Content-Range", "bytes " + debut + "-" + (corps.length - 1) + "/" + corps.length);
            ex.sendResponseHeaders(206, corps.length - debut);
        } else {
            ex.sendResponseHeaders(200, corps.length);
        }
        try (OutputStream out = ex.getResponseBody()) {
            int bloc = 32 * 1024;
            for (int i = debut; i < corps.length; i += bloc) {
                int n = Math.min(bloc, corps.length - i);
                out.write(corps, i, n);
                out.flush();
                octetsServis.addAndGet(n);
                if (pauseParBlocMs > 0) Thread.sleep(pauseParBlocMs);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (IOException coupe) {
            // client parti (annulation) : normal
        }
    }

    private int requetesSur(String chemin) {
        AtomicInteger n = requetes.get(chemin);
        return n == null ? 0 : n.get();
    }

    private static byte[] aleatoire(int taille, long graine) {
        byte[] b = new byte[taille];
        new Random(graine).nextBytes(b);
        return b;
    }

    /** Publie un manifeste pour l'univers et ses fichiers ; rend les chemins servis. */
    private Map<String, byte[]> publier(ModeDeJeu mode, Map<String, byte[]> contenu, String version) {
        List<Map<String, Object>> entrees = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : contenu.entrySet()) {
            String url = "/f/" + mode.id + "/" + e.getKey().replace('/', '_');
            fichiers.put(url, e.getValue());
            entrees.add(Map.of("path", e.getKey(), "sha256", Hashing.sha256(e.getValue()),
                    "size", e.getValue().length, "url", base + url.substring(1)));
        }
        Map<String, Object> manifeste = Map.of("version", version, "mcVersion", "1.21.1", "files", entrees);
        fichiers.put("/" + mode.packTag + "/manifest.json", GSON.toJson(manifeste).getBytes());
        return contenu;
    }

    private static void attendre(BooleanSupplier condition, long ms) throws InterruptedException {
        long fin = System.currentTimeMillis() + ms;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > fin) fail("délai dépassé");
            Thread.sleep(20);
        }
    }

    private static Field champManifesteSync() throws Exception {
        Field f = ModpackUpdater.class.getDeclaredField("manifestBaseUrl");
        f.setAccessible(true);
        return f;
    }

    // ---- Tests -----------------------------------------------------------------------------

    @Test
    @Timeout(30)
    void repriseDUnPartiel() throws Exception {
        byte[] contenu = aleatoire(600_000, 1);
        String sha = Hashing.sha256(contenu);
        fichiers.put("/f/a", contenu);
        Files.write(Reserve.partiel(sha), java.util.Arrays.copyOf(contenu, 250_000));

        TelechargeurFichier.telecharger(base + "f/a", sha, contenu.length, new Limiteur(0), suiviMuet());

        assertTrue(Reserve.contient(sha));
        assertFalse(Files.exists(Reserve.partiel(sha)));
        assertEquals(List.of("/f/a bytes=250000-"), plages);
        assertEquals(350_000, octetsServis.get(), "seule la fin manquante est téléchargée");
    }

    @Test
    @Timeout(30)
    void serveurSansRepriseRepartDeZero() throws Exception {
        repriseAcceptee = false;
        byte[] contenu = aleatoire(300_000, 2);
        String sha = Hashing.sha256(contenu);
        fichiers.put("/f/b", contenu);
        Files.write(Reserve.partiel(sha), java.util.Arrays.copyOf(contenu, 100_000));

        TelechargeurFichier.telecharger(base + "f/b", sha, contenu.length, new Limiteur(0), suiviMuet());

        assertTrue(Reserve.contient(sha));
        assertEquals(sha, Hashing.sha256(Reserve.fichier(sha)));
    }

    @Test
    @Timeout(30)
    void empreinteFausseFinitEnErreurSansRienGarder() throws Exception {
        byte[] contenu = aleatoire(200_000, 3);
        fichiers.put("/f/c", contenu);
        String faux = "0".repeat(64);

        assertThrows(IOException.class, () ->
                TelechargeurFichier.telecharger(base + "f/c", faux, contenu.length, new Limiteur(0), suiviMuet()));
        assertFalse(Reserve.contient(faux));
        assertFalse(Files.exists(Reserve.partiel(faux)));
        assertEquals(2, requetesSur("/f/c"), "un seul nouvel essai depuis zéro, pas de boucle");
    }

    @Test
    @Timeout(30)
    void empreinteFausseDansLeManifesteDonneEtatErreur() throws Exception {
        byte[] contenu = aleatoire(100_000, 4);
        fichiers.put("/f/d", contenu);
        Map<String, Object> manifeste = Map.of("version", "v1", "files", List.of(Map.of(
                "path", "mods/d.jar", "sha256", "1".repeat(64), "size", contenu.length, "url", base + "f/d")));
        fichiers.put("/" + ModeDeJeu.POKENYLE.packTag + "/manifest.json", GSON.toJson(manifeste).getBytes());
        pack = new PreTelechargeurPack(Conditions.jamais(), 0, () -> true);

        pack.demarrer("pokenyle");
        attendre(() -> pack.etat("pokenyle") == Etat.ERREUR, 10_000);
        assertNotNull(pack.erreur("pokenyle"));
    }

    @Test
    @Timeout(30)
    void annulationGardeLePartielPuisReprend() throws Exception {
        Map<String, byte[]> contenu = new LinkedHashMap<>();
        contenu.put("mods/gros.jar", aleatoire(2_000_000, 5));
        publier(ModeDeJeu.POKENYLE, contenu, "v1");
        pauseParBlocMs = 40;  // ~800 Ko/s : le temps d'annuler en route
        pack = new PreTelechargeurPack(Conditions.jamais(), 0, () -> true);

        pack.demarrer("pokenyle");
        attendre(() -> pack.progression("pokenyle") > 0.1, 10_000);
        pack.annuler("pokenyle");
        attendre(() -> pack.etat("pokenyle") == Etat.AUCUN, 10_000);
        String sha = Hashing.sha256(contenu.get("mods/gros.jar"));
        assertFalse(Reserve.contient(sha));
        long partiel = Files.size(Reserve.partiel(sha));
        assertTrue(partiel > 0, "le partiel est gardé");

        pauseParBlocMs = 0;
        pack.demarrer("pokenyle");
        attendre(() -> pack.etat("pokenyle") == Etat.PRET, 15_000);
        assertTrue(Reserve.contient(sha));
        assertEquals(1.0, pack.progression("pokenyle"), 1e-9);
        assertTrue(plages.stream().anyMatch(p -> p.contains("bytes=")), "la reprise utilise Range");
        assertTrue(octetsServis.get() < 2_000_000 + 600_000, "pas de retéléchargement complet");
    }

    @Test
    @Timeout(30)
    void plafondDeDebitPartageEntreFlux() throws Exception {
        byte[] a = aleatoire(400_000, 6);
        byte[] b = aleatoire(400_000, 7);
        fichiers.put("/f/pa", a);
        fichiers.put("/f/pb", b);
        Limiteur limiteur = new Limiteur(256 * 1024);
        long debut = System.nanoTime();
        Thread t1 = new Thread(() -> lancerSansErreur(base + "f/pa", Hashing.sha256(a), a.length, limiteur));
        Thread t2 = new Thread(() -> lancerSansErreur(base + "f/pb", Hashing.sha256(b), b.length, limiteur));
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        double secondes = (System.nanoTime() - debut) / 1e9;
        // 800 Ko à 256 Ko/s au total (moins une rafale de 64 Ko) ≈ 2,9 s ; sans plafond, < 0,1 s.
        assertTrue(secondes >= 2.5, "débit total plafonné (mesuré " + secondes + " s)");
        assertTrue(Reserve.contient(Hashing.sha256(a)) && Reserve.contient(Hashing.sha256(b)));
    }

    @Test
    @Timeout(30)
    void fondCedeAuJeuEtGardeLePartiel() throws Exception {
        Map<String, byte[]> contenu = new LinkedHashMap<>();
        contenu.put("mods/x.jar", aleatoire(2_000_000, 8));
        publier(ModeDeJeu.NYLERP, contenu, "v1");
        pauseParBlocMs = 40;
        AtomicBoolean jeu = new AtomicBoolean(false);
        pack = new PreTelechargeurPack(new Conditions(jeu::get, () -> false, () -> false), 0, () -> true);

        pack.demarrerAuto("nylerp");
        attendre(() -> pack.progression("nylerp") > 0.05, 10_000);
        jeu.set(true);  // le joueur lance une partie
        attendre(() -> pack.etat("nylerp") == Etat.AUCUN, 10_000);
        String sha = Hashing.sha256(contenu.get("mods/x.jar"));
        assertFalse(Reserve.contient(sha));
        assertTrue(Files.size(Reserve.partiel(sha)) > 0);

        int avant = requetesSur("/f/nylerp/mods_x.jar");
        pack.demarrerAuto("nylerp").get(10, TimeUnit.SECONDS);
        assertEquals(Etat.AUCUN, pack.etat("nylerp"));
        assertEquals(avant, requetesSur("/f/nylerp/mods_x.jar"), "rien n'est téléchargé tant que le jeu tourne");
    }

    @Test
    @Timeout(60)
    void pasDeDoubleTelechargement() throws Exception {
        assertEquals(ModeDeJeu.NYLERP, ModeDeJeu.courant(), "le sync de référence tourne sur NyleRP");
        Map<String, byte[]> contenu = new LinkedHashMap<>();
        contenu.put("mods/alpha-1.0.0.jar", aleatoire(700_000, 9));
        contenu.put("mods/beta-2.1.jar", aleatoire(500_000, 10));
        contenu.put("config/gamma.toml", "cle = 1\n".getBytes());
        contenu.put("config/gamma.toml.bak", "cle = 1\n".getBytes());  // même contenu, deux places
        publier(ModeDeJeu.NYLERP, contenu, "v-pre");
        pack = new PreTelechargeurPack(Conditions.jamais(), 0, () -> true);

        pack.demarrer("nylerp");
        attendre(() -> pack.etat("nylerp") == Etat.PRET, 15_000);
        // le contenu en double n'est téléchargé qu'une fois (sous l'une des deux adresses)
        assertEquals(1, requetesSur("/f/nylerp/config_gamma.toml") + requetesSur("/f/nylerp/config_gamma.toml.bak"));
        for (String p : List.of("mods/alpha-1.0.0.jar", "mods/beta-2.1.jar")) assertEquals(1, requetesSur("/f/nylerp/" + p.replace('/', '_')), p);
        Map<String, Integer> avantSync = new java.util.HashMap<>();
        for (String p : contenu.keySet()) avantSync.put(p, requetesSur("/f/nylerp/" + p.replace('/', '_')));
        long adoptionsAvant = Reserve.ADOPTIONS.get();

        // Le « Jouer » normal : la synchronisation telle qu'elle existe, sur le même manifeste.
        champManifesteSync().set(null, base + ModeDeJeu.NYLERP.packTag + "/manifest.json");
        List<String> journal = new CopyOnWriteArrayList<>();
        new ModpackUpdater(new ModpackUpdater.Listener() {
            @Override public void onStatus(String line) { journal.add(line); }
            @Override public void onProgress(int d, int t, long b, long bt) { }
        }).sync();

        for (Map.Entry<String, byte[]> e : contenu.entrySet()) {
            String url = "/f/nylerp/" + e.getKey().replace('/', '_');
            assertEquals(avantSync.get(e.getKey()).intValue(), requetesSur(url), "aucun téléchargement pendant la synchro : " + e.getKey());
            Path installe = AppPaths.gameDir().resolve(e.getKey());
            assertEquals(Hashing.sha256(e.getValue()), Hashing.sha256(installe), e.getKey());
        }
        assertEquals(adoptionsAvant + 4, Reserve.ADOPTIONS.get(), "les quatre fichiers viennent de la réserve");
        try (var reste = Files.list(Reserve.dossier())) {
            assertEquals(0, reste.count(), "la réserve est vidée par l'adoption");
        }
        assertTrue(journal.stream().anyMatch(l -> l.startsWith("Modpack à jour")), String.join("\n", journal));

        // Après coup, le plan ne voit plus rien à télécharger : l'univers est « prêt ».
        PlanPack.Resultat apres = new PlanPack("nylerp").evaluer();
        assertTrue(apres.rienATelecharger());
        assertEquals(0, apres.octetsTotal());
    }

    @Test
    @Timeout(30)
    void planIgnoreCeQuiEstInstalle() throws Exception {
        Map<String, byte[]> contenu = new LinkedHashMap<>();
        byte[] installe = aleatoire(1000, 11);
        byte[] nouveau = aleatoire(2000, 12);
        byte[] reglages = "perso".getBytes();
        contenu.put("mods/installe.jar", installe);
        contenu.put("mods/nouveau.jar", nouveau);
        publier(ModeDeJeu.POKENYLE, contenu, "v2");
        // un fichier « premier lancement seulement » déjà personnalisé par le joueur
        Map<String, Object> m = GSON.fromJson(new String(fichiers.get("/pack-pokenyle/manifest.json")), Map.class);
        @SuppressWarnings("unchecked") List<Map<String, Object>> f = new ArrayList<>((List<Map<String, Object>>) m.get("files"));
        f.add(Map.of("path", "options.txt", "sha256", Hashing.sha256(reglages), "size", reglages.length,
                "url", base + "f/options", "firstInstallOnly", true));
        fichiers.put("/pack-pokenyle/manifest.json", GSON.toJson(Map.of("version", "v2", "files", f)).getBytes());

        PlanPack plan = new PlanPack("pokenyle");
        Path jeu = plan.dossierJeu();
        Files.createDirectories(jeu.resolve("mods"));
        Files.write(jeu.resolve("mods/installe.jar"), installe);
        Files.writeString(jeu.resolve("options.txt"), "les réglages du joueur");
        Files.writeString(plan.cacheManifeste(), GSON.toJson(Map.of("version", "v1", "files", List.of(Map.of(
                "path", "mods/installe.jar", "sha256", Hashing.sha256(installe), "size", installe.length, "url", "x")))));

        PlanPack.Resultat r = plan.evaluer();
        assertEquals(1, r.aTelecharger().size());
        assertEquals("mods/nouveau.jar", r.aTelecharger().get(0).chemin());
        assertEquals(2000, r.octetsTotal());
        assertTrue(plan.dejaJoue());
        assertFalse(new PlanPack("nylerp").dejaJoue());
    }

    @Test
    void cheminsIdentiquesAuLauncher() {
        if (ModeDeJeu.courant() != ModeDeJeu.NYLERP) return;
        PlanPack plan = new PlanPack("nylerp");
        assertEquals(AppPaths.manifestCache(), plan.cacheManifeste());
        assertEquals(AppPaths.gameDir(), plan.dossierJeu());
        assertThrows(IllegalArgumentException.class, () -> new PlanPack("minecraft"));
        assertFalse(Reserve.valide("../../etc/passwd"));
    }

    @Test
    @Timeout(20)
    void adoptionRefuseUnFichierAbime() throws Exception {
        byte[] contenu = aleatoire(5000, 13);
        String sha = Hashing.sha256(contenu);
        Files.write(Reserve.fichier(sha), aleatoire(5000, 14));  // mauvais contenu sous le bon nom
        Path dest = home.resolve("dest.bin");
        assertFalse(Reserve.adopter(sha, dest));
        assertFalse(Files.exists(dest));
        assertFalse(Reserve.contient(sha), "le fichier abîmé est supprimé");
    }

    // ---- Outils ------------------------------------------------------------------------------

    private static TelechargeurFichier.Suivi suiviMuet() {
        return new TelechargeurFichier.Suivi() {
            @Override public void progression(long octets) { }
            @Override public boolean doitArreter() { return false; }
        };
    }

    private static void lancerSansErreur(String url, String sha, long taille, Limiteur limiteur) {
        try {
            TelechargeurFichier.telecharger(url, sha, taille, limiteur, suiviMuet());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    // ---- Pokényle avant l'ouverture : réservé à ceux qui peuvent déjà jouer (L13) ------------------

    @Test
    @Timeout(30)
    void nonTesteurNePeutPasPreTelechargerPokenyle() throws Exception {
        Map<String, byte[]> contenu = new LinkedHashMap<>();
        contenu.put("mods/gros.jar", aleatoire(500_000, 21));
        publier(ModeDeJeu.POKENYLE, contenu, "v1");
        pack = new PreTelechargeurPack(Conditions.jamais(), 0, () -> false);

        pack.demarrer("pokenyle");
        assertNull(pack.demarrerAuto("pokenyle"), "le fond non plus ne peut pas le lancer");
        Thread.sleep(400);
        assertEquals(Etat.AUCUN, pack.etat("pokenyle"), "état inchangé, sans erreur affichée");
        assertNull(pack.erreur("pokenyle"));
        assertEquals(0, requetesSur("/f/pokenyle/mods_gros.jar"), "aucun octet du pack n'est demandé");
        assertEquals(0, octetsServis.get());
    }

    @Test
    @Timeout(30)
    void nonTesteurPeutQuandMemePreTelechargerNylerp() throws Exception {
        assertEquals(ModeDeJeu.NYLERP, ModeDeJeu.courant());
        Map<String, byte[]> contenu = new LinkedHashMap<>();
        contenu.put("mods/x.jar", aleatoire(200_000, 22));
        publier(ModeDeJeu.NYLERP, contenu, "v1");
        pack = new PreTelechargeurPack(Conditions.jamais(), 0, () -> false);

        pack.demarrer("nylerp");
        attendre(() -> pack.etat("nylerp") == Etat.PRET, 15_000);
    }

    @Test
    @Timeout(30)
    void testeurPeutPreTelechargerPokenyle() throws Exception {
        Map<String, byte[]> contenu = new LinkedHashMap<>();
        contenu.put("mods/gros.jar", aleatoire(300_000, 23));
        publier(ModeDeJeu.POKENYLE, contenu, "v1");
        pack = new PreTelechargeurPack(Conditions.jamais(), 0, () -> true);

        pack.demarrer("pokenyle");
        attendre(() -> pack.etat("pokenyle") == Etat.PRET, 15_000);
        assertTrue(Reserve.contient(Hashing.sha256(contenu.get("mods/gros.jar"))));
    }

    @Test
    @Timeout(40)
    void changementDeCompteTesteurVersNonTesteurAnnuleLeTelechargement() throws Exception {
        Map<String, byte[]> contenu = new LinkedHashMap<>();
        contenu.put("mods/gros.jar", aleatoire(2_000_000, 24));
        publier(ModeDeJeu.POKENYLE, contenu, "v1");
        pauseParBlocMs = 40;
        java.util.concurrent.atomic.AtomicBoolean testeur = new java.util.concurrent.atomic.AtomicBoolean(true);
        pack = new PreTelechargeurPack(Conditions.jamais(), 0, testeur::get);

        pack.demarrer("pokenyle");
        attendre(() -> pack.progression("pokenyle") > 0.1, 10_000);
        testeur.set(false);              // le joueur bascule sur un compte qui n'est pas testeur
        pack.reverifierAcces();
        attendre(() -> pack.etat("pokenyle") == Etat.AUCUN, 10_000);
        String sha = Hashing.sha256(contenu.get("mods/gros.jar"));
        assertTrue(Files.size(Reserve.partiel(sha)) > 0, "le partiel est gardé");
        Thread.sleep(500);               // le serveur finit d'écouler ce qu'il avait déjà dans les tuyaux
        long figes = octetsServis.get();
        Thread.sleep(400);
        assertEquals(figes, octetsServis.get(), "plus aucun octet n'arrive");

        // et il ne peut plus le relancer
        pauseParBlocMs = 0;
        pack.demarrer("pokenyle");
        Thread.sleep(300);
        assertEquals(Etat.AUCUN, pack.etat("pokenyle"));
        assertEquals(figes, octetsServis.get());
    }

    @Test
    @Timeout(40)
    void ouvertureGeneraleRedonneLeDroitATous() throws Exception {
        Map<String, byte[]> contenu = new LinkedHashMap<>();
        contenu.put("mods/gros.jar", aleatoire(200_000, 25));
        publier(ModeDeJeu.POKENYLE, contenu, "v1");
        java.util.concurrent.atomic.AtomicBoolean ouvert = new java.util.concurrent.atomic.AtomicBoolean(false);
        pack = new PreTelechargeurPack(Conditions.jamais(), 0, ouvert::get);

        pack.demarrer("pokenyle");
        Thread.sleep(300);
        assertEquals(Etat.AUCUN, pack.etat("pokenyle"));
        ouvert.set(true);                // pokenyleOuvert = true dans la config du Studio
        pack.demarrer("pokenyle");
        attendre(() -> pack.etat("pokenyle") == Etat.PRET, 15_000);
    }
}
