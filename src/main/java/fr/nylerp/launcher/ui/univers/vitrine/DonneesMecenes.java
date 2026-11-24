package fr.nylerp.launcher.ui.univers.vitrine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LES MÉCÈNES DU MOIS — lecture de {@code GET <site>/api/dons} (contrat du chantier « dons ») et des planches de la vitrine
 * v2 (/vitrine/v2/ : planche du Pokémon + couches de peau ; peau du mécène par /api/dons/peau/&lt;empreinte&gt;.png, repli
 * Steve / Alex), composées ICI une fois ({@link Composition}), HORS du fil JavaFX, délais courts.
 *
 * <ul>
 *   <li>Rien ne s'affiche si le site ne répond pas : l'affichage suit une réponse FRAÎCHE de /api/dons, jamais une copie.</li>
 *   <li>Cache disque ({@code <racine>/vitrine/}) : le catalogue (repli si le site rend /api/dons mais pas le catalogue) et
 *       les planches PNG, vérifiées par SHA-256 (une planche altérée ou tronquée est retéléchargée, jamais affichée).</li>
 *   <li>Aucune donnée de joueur envoyée ; seulement des GET publics.</li>
 * </ul>
 */
public final class DonneesMecenes {

    static final String SITE = System.getProperty("nyle.vitrine.site", "https://pokerayou.academy");
    private static final Duration DELAI = Duration.ofSeconds(4), DELAI_PLANCHE = Duration.ofSeconds(12);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final ExecutorService FIL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "vitrine-mecenes");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    /** Un mécène prêt à afficher : sa fiche, sa planche COMPOSÉE (avec sa peau), la boîte opaque de chaque image, et la carte du
     *  survol (nom français du Pokémon, mode « monte » / « cote », chromatique). */
    public record Entree(int rang, String pseudo, LoiVitrine.Fiche fiche, Path planche, javafx.scene.image.Image image, int[][] boites,
                         String nom, String mode, boolean shiny, String cle) {}

    /** Ce que la vitrine affiche : les mécènes prêts, l'avant-plan des coffres (fichier + boîte dans le repère). */
    /** {@code echelle} : la taille des Pokémon réglée par le site ({@code reglages.echelle} du catalogue, 0,6 à 1,4). */
    public record Resultat(List<Entree> mecenes, Path avantPlan, double[] boiteAvantPlan, double echelle, String mois, Path police) {}

    private DonneesMecenes() {}

    /** Lance la lecture (fil de fond) ; le futur rend null si rien ne doit s'afficher. */
    public static CompletableFuture<Resultat> charger(Path dossier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return lire(dossier);
            } catch (Throwable t) {
                System.err.println("[Vitrine] " + t);
                return null;
            }
        }, FIL);
    }

    private static Resultat lire(Path dossier) throws Exception {
        Files.createDirectories(dossier.resolve("p"));
        JsonObject dons = json(SITE + "/api/dons");
        if (dons == null || !dons.has("podium")) return null;                        // le site ne répond pas : rien
        JsonObject cat = json(SITE + "/vitrine/v2/catalogue.json");
        Path cCat = dossier.resolve("catalogue.json");
        if (cat != null) ecrire(cCat, cat.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        else if (Files.isRegularFile(cCat)) cat = JsonParser.parseString(Files.readString(cCat)).getAsJsonObject();
        if (cat == null) return null;
        JsonObject especes = cat.getAsJsonObject("especes");
        List<Entree> out = new ArrayList<>();
        for (JsonElement e : dons.getAsJsonArray("podium")) {
            JsonObject p = e.getAsJsonObject();
            // sans Pokémon (pas encore choisi, ou espèce absente de la vitrine) : le mécène passe À PIED, seul (planche « _seul »)
            JsonObject pk = p.has("pokemon") && p.get("pokemon").isJsonObject() ? p.getAsJsonObject("pokemon") : null;
            String esp = pk != null && especes.has(pk.get("espece").getAsString()) ? pk.get("espece").getAsString() : "_seul";
            if (!especes.has(esp)) continue;
            JsonObject v = especes.getAsJsonObject(esp);
            boolean shiny = pk != null && !"_seul".equals(esp) && pk.has("shiny") && pk.get("shiny").getAsBoolean() && v.has("shiny");
            JsonObject fj = v.getAsJsonObject(shiny ? "shiny" : "normal");
            LoiVitrine.Fiche f = LoiVitrine.Fiche.lire(fj);
            Path planche = fichier(dossier, f.fichierPng, f.sha256Png);
            if (planche == null) continue;
            // la peau : celle du mécène (empreinte Mojang, servie par le site), sinon Steve / Alex du catalogue
            JsonObject pe = p.has("peau") && p.get("peau").isJsonObject() ? p.getAsJsonObject("peau") : null;
            String modele = pe != null && pe.has("modele") && "fin".equals(pe.get("modele").getAsString()) ? "fin" : "classique";
            String tex = pe != null && pe.has("texture") ? pe.get("texture").getAsString() : null;
            JsonObject co = fj.getAsJsonObject("couches").getAsJsonObject(modele);
            Path ci = fichier(dossier, co.getAsJsonObject("int").getAsJsonObject("png").get("fichier").getAsString(), co.getAsJsonObject("int").getAsJsonObject("png").get("sha256").getAsString());
            Path ce = fichier(dossier, co.getAsJsonObject("ext").getAsJsonObject("png").get("fichier").getAsString(), co.getAsJsonObject("ext").getAsJsonObject("png").get("sha256").getAsString());
            if (ci == null || ce == null) continue;
            Path peau = tex != null && tex.matches("[0-9a-f]{64}") ? peau(dossier, tex) : null;
            if (peau == null) {
                JsonObject d = cat.getAsJsonObject("peaux_defaut").getAsJsonObject(modele);
                peau = fichier(dossier, d.get("fichier").getAsString(), d.get("sha256").getAsString());
            }
            if (peau == null) continue;
            javafx.scene.image.Image im = composer(planche, ci, ce, peau);
            if (im == null) continue;
            int n = f.principal.n() + (f.arret == null ? 0 : f.arret.n()) + (f.pause == null ? 0 : f.pause.n()) + (f.depart == null ? 0 : f.depart.n()) + (f.demitour == null ? 0 : f.demitour.n());
            int[][] boites = boitesDe(im, f, n);
            String nom = v.has("nom") ? v.get("nom").getAsString() : esp, mode = v.has("mode") ? v.get("mode").getAsString() : "cote";
            out.add(new Entree(p.get("rang").getAsInt(), p.get("pseudo").getAsString(), f, planche, im, boites, nom, mode, shiny,
                    f.fichierPng + "|" + modele + "|" + peau.getFileName()));
        }
        if (out.isEmpty()) return null;
        JsonObject ap = cat.getAsJsonObject("avant_plan").getAsJsonObject("fond");
        Path avant = fichier(dossier, ap.get("fichier").getAsString(), ap.get("sha256").getAsString());
        if (avant == null) return null;                                                // sans les coffres, un marcheur passerait devant eux
        JsonArray b = ap.getAsJsonArray("boite");
        ranger(dossier.resolve("p"), out, avant);
        double echelle = 1;
        try { if (cat.has("reglages")) echelle = Math.max(0.6, Math.min(1.4, cat.getAsJsonObject("reglages").get("echelle").getAsDouble())); } catch (Exception ignore) { }
        String mois = dons.has("mois") ? dons.get("mois").getAsString() : "";
        // le nametag du jeu : la police bitmap de Minecraft (ascii.png), publiée avec la vitrine ; absente -> plaque Kanit
        Path police = cat.has("police") ? fichier(dossier, cat.getAsJsonObject("police").get("fichier").getAsString(), cat.getAsJsonObject("police").get("sha256").getAsString()) : null;
        return new Resultat(out, avant, new double[]{b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble(), b.get(3).getAsDouble()}, echelle, mois, police);
    }

    /** La peau d'un mécène : cache {@code p/peau-<empreinte>.png}, sinon {@code GET <site>/api/dons/peau/<empreinte>.png} ;
     *  gardée seulement si c'est un PNG 64 × 64 ou 64 × 32 de 20 Ko au plus. */
    private static Path peau(Path dossier, String tex) {
        Path p = dossier.resolve("p").resolve("peau-" + tex + ".png");
        try {
            if (Files.isRegularFile(p) && pngPeau(Files.readAllBytes(p))) return p;
            HttpRequest q = HttpRequest.newBuilder(URI.create(SITE + "/api/dons/peau/" + tex + ".png")).timeout(DELAI).GET().build();
            HttpResponse<byte[]> r = HTTP.send(q, HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() != 200 || !pngPeau(r.body())) return null;
            ecrire(p, r.body());
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    static boolean pngPeau(byte[] b) {
        if (b == null || b.length < 33 || b.length > 20480) return false;
        int[] sig = {137, 80, 78, 71, 13, 10, 26, 10};
        for (int i = 0; i < 8; i++) if ((b[i] & 0xff) != sig[i]) return false;
        if (b[12] != 'I' || b[13] != 'H' || b[14] != 'D' || b[15] != 'R') return false;
        int w = ((b[16] & 0xff) << 24) | ((b[17] & 0xff) << 16) | ((b[18] & 0xff) << 8) | (b[19] & 0xff);
        int h = ((b[20] & 0xff) << 24) | ((b[21] & 0xff) << 16) | ((b[22] & 0xff) << 8) | (b[23] & 0xff);
        return w == 64 && (h == 64 || h == 32);
    }

    private static int[] pixels(javafx.scene.image.Image im) {
        int w = (int) im.getWidth(), h = (int) im.getHeight();
        int[] a = new int[w * h];
        im.getPixelReader().getPixels(0, 0, w, h, javafx.scene.image.PixelFormat.getIntArgbInstance(), a, 0, w);
        return a;
    }

    /** La planche finale (fil de fond : images chargées sans fil JavaFX, puis composées). */
    private static javafx.scene.image.Image composer(Path planche, Path ci, Path ce, Path peau) {
        try {
            javafx.scene.image.Image pl = new javafx.scene.image.Image(planche.toUri().toString(), false), i1 = new javafx.scene.image.Image(ci.toUri().toString(), false),
                    i2 = new javafx.scene.image.Image(ce.toUri().toString(), false), pe = new javafx.scene.image.Image(peau.toUri().toString(), false);
            if (pl.isError() || i1.isError() || i2.isError() || pe.isError()) return null;
            int w = (int) pl.getWidth(), h = (int) pl.getHeight();
            if ((int) i1.getWidth() != w || (int) i1.getHeight() != h || (int) i2.getWidth() != w || (int) i2.getHeight() != h) return null;
            int[] P = Composition.peau64(pixels(pe), (int) pe.getWidth(), (int) pe.getHeight());
            if (P == null) return null;
            int[] a = pixels(pl);
            Composition.composer(a, pixels(i1), pixels(i2), P);
            javafx.scene.image.WritableImage out = new javafx.scene.image.WritableImage(w, h);
            out.getPixelWriter().setPixels(0, 0, w, h, javafx.scene.image.PixelFormat.getIntArgbInstance(), a, 0, w);
            return out;
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    private static int[][] boitesDe(javafx.scene.image.Image im, LoiVitrine.Fiche f, int n) {
        return Composition.boites(pixels(im), (int) im.getWidth(), f.largeurCellule, f.hauteurCellule, f.colonnes, n);
    }

    private static JsonObject json(String url) {
        try {
            HttpRequest q = HttpRequest.newBuilder(URI.create(url)).timeout(DELAI).header("Accept", "application/json").GET().build();
            HttpResponse<String> r = HTTP.send(q, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) return null;
            return JsonParser.parseString(r.body()).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    /** Le fichier publié {@code chemin} (relatif à /vitrine/v2/), depuis le cache s'il y est intact, sinon téléchargé et vérifié. */
    private static Path fichier(Path dossier, String chemin, String sha) {
        String nom = chemin.substring(chemin.lastIndexOf('/') + 1);
        if (!nom.matches("[a-z0-9_.-]+")) return null;
        Path p = dossier.resolve("p").resolve(nom);
        try {
            if (Files.isRegularFile(p) && sha.equalsIgnoreCase(sha256(Files.readAllBytes(p)))) return p;
            HttpRequest q = HttpRequest.newBuilder(URI.create(SITE + "/vitrine/v2/" + chemin)).timeout(DELAI_PLANCHE).GET().build();
            HttpResponse<byte[]> r = HTTP.send(q, HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() != 200 || !sha.equalsIgnoreCase(sha256(r.body()))) return null;
            ecrire(p, r.body());
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    private static void ecrire(Path p, byte[] octets) throws IOException {
        Path t = p.resolveSibling(p.getFileName() + ".part");
        Files.write(t, octets);
        Files.move(t, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Le cache ne garde que les planches en vitrine et les 9 plus récentes (quelques Mo au plus). */
    private static void ranger(Path p, List<Entree> gardees, Path avant) {
        try (var s = Files.list(p)) {
            List<Path> tous = new ArrayList<>(s.filter(Files::isRegularFile).toList());
            tous.sort((a, b) -> Long.compare(b.toFile().lastModified(), a.toFile().lastModified()));
            int i = 0;
            for (Path f : tous) {
                boolean utile = f.equals(avant) || f.getFileName().toString().startsWith("police-") || gardees.stream().anyMatch(e -> e.planche().equals(f));
                if (!utile && ++i > 30) Files.deleteIfExists(f);            // 3 mécènes × (planche, 2 couches, peau) + réserve
            }
        } catch (IOException ignore) { }
    }

    private static String sha256(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }
}
