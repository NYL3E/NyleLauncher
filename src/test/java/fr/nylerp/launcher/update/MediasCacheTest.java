package fr.nylerp.launcher.update;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.nylerp.launcher.util.Hashing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Le paquet de médias distant contre un serveur HTTP local : téléchargement, SHA faux, reprise
 * d'un .part, serveur sans Range, mode hors ligne, média différé, rangement du cache.
 */
@Timeout(60)
class MediasCacheTest {

    private static final Gson GSON = new Gson();

    private HttpServer serveur;
    private String racine;
    private Path dossier;
    private MediasCache cache;

    /** Contenu servi par nom d'asset. */
    private final Map<String, byte[]> assets = new ConcurrentHashMap<>();
    /** Contenu du manifeste servi. */
    private final AtomicReference<String> manifeste = new AtomicReference<>("");
    /** Journal des requêtes d'assets : "nom|Range". */
    private final List<String> requetes = new CopyOnWriteArrayList<>();
    private volatile boolean rangeAccepte = true;

    @BeforeEach
    void demarrer() throws IOException {
        dossier = Files.createTempDirectory("nyle-medias-test");
        serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext("/medias.json", ex -> repondre(ex, 200, manifeste.get().getBytes(StandardCharsets.UTF_8)));
        serveur.createContext("/assets/", this::servirAsset);
        serveur.start();
        racine = "http://127.0.0.1:" + serveur.getAddress().getPort();
    }

    @AfterEach
    void arreter() {
        if (cache != null) cache.fermer();
        serveur.stop(0);
    }

    private void servirAsset(HttpExchange ex) throws IOException {
        String nom = ex.getRequestURI().getPath().substring("/assets/".length());
        String range = ex.getRequestHeaders().getFirst("Range");
        requetes.add(nom + "|" + range);
        byte[] b = assets.get(nom);
        if (b == null) { repondre(ex, 404, new byte[0]); return; }
        if (range != null && rangeAccepte) {
            long debut = Long.parseLong(range.replace("bytes=", "").replace("-", ""));
            byte[] reste = java.util.Arrays.copyOfRange(b, (int) debut, b.length);
            ex.getResponseHeaders().add("Content-Range", "bytes " + debut + "-" + (b.length - 1) + "/" + b.length);
            repondre(ex, 206, reste);
            return;
        }
        repondre(ex, 200, b);
    }

    private static void repondre(HttpExchange ex, int code, byte[] corps) throws IOException {
        ex.sendResponseHeaders(code, corps.length == 0 ? -1 : corps.length);
        try (OutputStream o = ex.getResponseBody()) { if (corps.length > 0) o.write(corps); }
    }

    private static byte[] contenu(String graine, int taille) {
        byte[] b = new byte[taille];
        for (int i = 0; i < taille; i++) b[i] = (byte) (graine.hashCode() * 31 + i * 7);
        return b;
    }

    /** Publie un asset et renvoie son entrée de manifeste. */
    private Map<String, Object> publier(String chemin, byte[] octets, Boolean prechargement) {
        String sha = Hashing.sha256(octets);
        // Même assainissement que scripts/publier-medias.py (GitHub réécrit tout caractère hors [A-Za-z0-9._-]).
        String asset = sha.substring(0, 12) + "-" + chemin.substring(chemin.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]+", ".");
        assets.put(asset, octets);
        Map<String, Object> e = new java.util.LinkedHashMap<>();
        e.put("chemin", chemin);
        e.put("sha256", sha);
        e.put("taille", octets.length);
        e.put("asset", asset);
        if (prechargement != null) e.put("prechargement", prechargement);
        return e;
    }

    private void servirManifeste(String version, List<Map<String, Object>> fichiers) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("format", 1);
        m.put("version", version);
        m.put("base", racine + "/assets/");
        m.put("fichiers", fichiers);
        manifeste.set(GSON.toJson(m));
    }

    private MediasCache nouveauCache(String urlManifeste) {
        cache = new MediasCache(dossier, urlManifeste, 3);
        cache.chargerLocal();
        return cache;
    }

    @Test
    void telechargeLesMediasPrechargesEtLesServent() throws Exception {
        byte[] musique = contenu("musique", 300_000);
        byte[] fond = contenu("fond", 50_000);
        servirManifeste("1", List.of(publier("nylerp/music.mp3", musique, null),
                                     publier("pokenyle/fond-pokenyle.jpg", fond, true)));
        MediasCache c = nouveauCache(racine + "/medias.json");
        assertNull(c.fichier("nylerp/music.mp3"), "rien avant la synchronisation");

        c.synchroniser().get(30, TimeUnit.SECONDS);

        Path p = c.fichier("nylerp/music.mp3");
        assertNotNull(p);
        assertArrayEquals(musique, Files.readAllBytes(p));
        assertTrue(p.getFileName().toString().endsWith(".mp3"), "extension gardée pour JavaFX Media");
        assertArrayEquals(fond, Files.readAllBytes(c.fichier("pokenyle/fond-pokenyle.jpg")));
        assertTrue(Files.isRegularFile(dossier.resolve("medias.json")), "manifeste local enregistré");
        assertEquals("1", c.version());
    }

    @Test
    void horsLigneLeCacheLocalSertCeQuIlADeja() throws Exception {
        byte[] musique = contenu("musique", 120_000);
        servirManifeste("1", List.of(publier("nylerp/music.mp3", musique, null)));
        nouveauCache(racine + "/medias.json").synchroniser().get(30, TimeUnit.SECONDS);
        cache.fermer();

        int portMort;
        try (ServerSocket s = new ServerSocket(0)) { portMort = s.getLocalPort(); }
        MediasCache horsLigne = nouveauCache("http://127.0.0.1:" + portMort + "/medias.json");
        horsLigne.synchroniser().get(30, TimeUnit.SECONDS);   // ne lève pas

        Path p = horsLigne.fichier("nylerp/music.mp3");
        assertNotNull(p, "le média déjà téléchargé reste servi hors ligne");
        assertArrayEquals(musique, Files.readAllBytes(p));
        assertTrue(horsLigne.dernierEtat().startsWith("hors ligne"), horsLigne.dernierEtat());
        assertNull(horsLigne.fichier("pokenyle/inconnu.png"));
    }

    @Test
    void horsLigneSansRienEnCacheRienNeCasse() throws Exception {
        int portMort;
        try (ServerSocket s = new ServerSocket(0)) { portMort = s.getLocalPort(); }
        MediasCache c = nouveauCache("http://127.0.0.1:" + portMort + "/medias.json");
        c.synchroniser().get(30, TimeUnit.SECONDS);
        assertNull(c.fichier("nylerp/music.mp3"));
        assertFalse(c.connait("nylerp/music.mp3"));
    }

    @Test
    void shaFauxRefuseSansBoucler() throws Exception {
        byte[] annonce = contenu("annonce", 40_000);
        Map<String, Object> e = publier("nylerp/music.mp3", annonce, null);
        assets.put((String) e.get("asset"), contenu("autre-chose", 40_000));   // même taille, autre contenu
        servirManifeste("1", List.of(e));

        MediasCache c = nouveauCache(racine + "/medias.json");
        c.synchroniser().get(30, TimeUnit.SECONDS);

        assertNull(c.fichier("nylerp/music.mp3"), "un contenu faux n'est jamais servi");
        try (var f = Files.list(dossier)) {
            assertTrue(f.noneMatch(p -> p.getFileName().toString().endsWith(".part")), "pas de .part corrompu gardé");
        }
        assertEquals(2, requetes.size(), "un seul nouvel essai depuis zéro, puis abandon : " + requetes);
    }

    @Test
    void repriseDUnPartAvecRange() throws Exception {
        byte[] musique = contenu("musique", 200_000);
        Map<String, Object> e = publier("nylerp/music.mp3", musique, null);
        servirManifeste("1", List.of(e));
        Files.createDirectories(dossier);
        String sha = (String) e.get("sha256");
        Files.write(dossier.resolve(sha + ".mp3.part"), java.util.Arrays.copyOf(musique, 80_000));

        MediasCache c = nouveauCache(racine + "/medias.json");
        c.synchroniser().get(30, TimeUnit.SECONDS);

        assertArrayEquals(musique, Files.readAllBytes(c.fichier("nylerp/music.mp3")));
        assertEquals(List.of(e.get("asset") + "|bytes=80000-"), requetes, "reprise au bon octet, une seule requête");
    }

    @Test
    void serveurSansRangeRepartDeZero() throws Exception {
        rangeAccepte = false;
        byte[] musique = contenu("musique", 150_000);
        Map<String, Object> e = publier("nylerp/music.mp3", musique, null);
        servirManifeste("1", List.of(e));
        Files.createDirectories(dossier);
        Files.write(dossier.resolve(e.get("sha256") + ".mp3.part"), java.util.Arrays.copyOf(musique, 60_000));

        MediasCache c = nouveauCache(racine + "/medias.json");
        c.synchroniser().get(30, TimeUnit.SECONDS);

        assertArrayEquals(musique, Files.readAllBytes(c.fichier("nylerp/music.mp3")),
                "une réponse 200 réécrit le fichier entier au lieu de l'ajouter au .part");
    }

    @Test
    void mediaDiffereTelechargeALaDemandeEtPrevientLAppelant() throws Exception {
        byte[] carte = contenu("carte", 30_000);
        servirManifeste("1", List.of(publier("univers/carte-nylerp@2x.jpg", carte, false)));
        MediasCache c = nouveauCache(racine + "/medias.json");
        c.synchroniser().get(30, TimeUnit.SECONDS);
        assertTrue(requetes.isEmpty(), "un média différé n'est pas préchargé");

        CountDownLatch pret = new CountDownLatch(1);
        AtomicReference<Path> recu = new AtomicReference<>();
        c.quandDisponible("univers/carte-nylerp@2x.jpg", p -> { recu.set(p); pret.countDown(); });
        assertTrue(pret.await(20, TimeUnit.SECONDS), "le rappel arrive à la fin du téléchargement");
        assertArrayEquals(carte, Files.readAllBytes(recu.get()));
    }

    @Test
    void attenteServieQuandUnManifestePlusRecentApporteLeMedia() throws Exception {
        servirManifeste("1", List.of());
        MediasCache c = nouveauCache(racine + "/medias.json");
        c.synchroniser().get(30, TimeUnit.SECONDS);

        CountDownLatch pret = new CountDownLatch(1);
        c.quandDisponible("nylerp/music.mp3", p -> pret.countDown());

        servirManifeste("2", List.of(publier("nylerp/music.mp3", contenu("m", 10_000), null)));
        c.synchroniser().get(30, TimeUnit.SECONDS);
        assertTrue(pret.await(10, TimeUnit.SECONDS));
    }

    @Test
    void rangeLesAnciennesVersions() throws Exception {
        byte[] v1 = contenu("v1", 20_000);
        servirManifeste("1", List.of(publier("nylerp/music.mp3", v1, null)));
        MediasCache c = nouveauCache(racine + "/medias.json");
        c.synchroniser().get(30, TimeUnit.SECONDS);
        Path ancien = c.fichier("nylerp/music.mp3");
        Path etranger = Files.writeString(dossier.resolve("note-perso.txt"), "à ne pas toucher");

        byte[] v2 = contenu("v2", 25_000);
        servirManifeste("2", List.of(publier("nylerp/music.mp3", v2, null)));
        c.synchroniser().get(30, TimeUnit.SECONDS);

        assertArrayEquals(v2, Files.readAllBytes(c.fichier("nylerp/music.mp3")));
        assertFalse(Files.exists(ancien), "l'ancienne version quitte le cache");
        assertTrue(Files.exists(etranger), "un fichier qui n'est pas au format du cache n'est jamais supprimé");
    }

    @Test
    void deuxSynchronisationsSimultaneesPartagentLaMemePasse() throws Exception {
        servirManifeste("1", List.of(publier("nylerp/music.mp3", contenu("m", 400_000), null)));
        MediasCache c = nouveauCache(racine + "/medias.json");
        var a = c.synchroniser();
        var b = c.synchroniser();
        assertSame(a, b, "une passe en cours est partagée");
        assertTrue(a.get(30, TimeUnit.SECONDS), "true : manifeste lu et médias préchargés en cache");
        assertEquals(1, requetes.size(), "un seul téléchargement");
    }

    @Test
    void manifesteValide() {
        String sha = "a".repeat(64);
        String json = "{\"format\":1,\"version\":\"3\",\"fichiers\":["
                + "{\"chemin\":\"nylerp/music.mp3\",\"sha256\":\"" + sha + "\",\"taille\":10,\"asset\":\"x-music.mp3\"},"
                + "{\"chemin\":\"../../etc/passwd\",\"sha256\":\"" + sha + "\",\"taille\":10,\"asset\":\"x\"},"
                + "{\"chemin\":\"/absolu.png\",\"sha256\":\"" + sha + "\",\"taille\":10,\"asset\":\"x\"},"
                + "{\"chemin\":\"ok.png\",\"sha256\":\"PAS-UN-SHA\",\"taille\":10,\"asset\":\"x\"},"
                + "{\"chemin\":\"ok2.png\",\"sha256\":\"" + sha + "\",\"taille\":10,\"asset\":\"a/b\"},"
                + "{\"chemin\":\"vide.png\",\"sha256\":\"" + sha + "\",\"taille\":0,\"asset\":\"x\"},"
                + "{\"chemin\":\"nylerp/music.mp3\",\"sha256\":\"" + sha + "\",\"taille\":10,\"asset\":\"doublon\"}"
                + "]}";
        MediasCache.Manifeste m = MediasCache.lire(json);
        assertNotNull(m);
        List<String> chemins = new ArrayList<>();
        m.fichiers().forEach(e -> chemins.add(e.chemin()));
        assertEquals(List.of("nylerp/music.mp3"), chemins, "traversée, absolu, SHA, asset et doublon rejetés");
        assertNull(MediasCache.lire("{\"format\":2,\"fichiers\":[]}"), "format futur ignoré");
        assertNull(MediasCache.lire("pas du json"), "JSON invalide ignoré");
    }

    @Test
    void manifesteDistantInvalideGardeLeLocal() throws Exception {
        byte[] musique = contenu("musique", 10_000);
        servirManifeste("1", List.of(publier("nylerp/music.mp3", musique, null)));
        MediasCache c = nouveauCache(racine + "/medias.json");
        c.synchroniser().get(30, TimeUnit.SECONDS);

        manifeste.set("{\"format\":9,\"fichiers\":[]}");
        c.synchroniser().get(30, TimeUnit.SECONDS);
        assertNotNull(c.fichier("nylerp/music.mp3"), "un manifeste distant refusé ne vide pas l'index");
        assertEquals("1", c.version());
    }
}
