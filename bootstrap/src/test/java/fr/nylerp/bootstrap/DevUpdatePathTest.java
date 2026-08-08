package fr.nylerp.bootstrap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Garde-fous du chemin de mise à jour du canal <b>DEV</b>, et preuve que la <b>production</b> n'a
 * pas bougé.
 *
 * <p><b>Pourquoi ce test existe.</b> Le canal DEV a passé plusieurs semaines sans aucun chemin de
 * mise à jour : le workflow empaquetait le payload directement au lieu du socle, si bien qu'un
 * launcher DEV n'allait jamais chercher quoi que ce soit et que chaque itération coûtait une
 * réinstallation à toute l'équipe de test. Rien, dans la chaîne de construction, ne pouvait
 * signaler cette régression — c'est exactement ce trou-là que ce test bouche. Il est branché sur le
 * job {@code garde} de {@code dev-launcher.yml} : casser le chemin de mise à jour casse le build.
 *
 * <p>Les assertions se répartissent en trois familles :
 * <ol>
 *   <li><b>Production intacte</b> — l'URL de manifeste et le dossier de cache de prod sont
 *       comparés à leurs valeurs littérales historiques, et on vérifie qu'aucune propriété système
 *       ne peut détourner un socle de production ;</li>
 *   <li><b>Le canal DEV se met bien à jour</b> — canal distinct, manifeste distinct, cache
 *       distinct, et un workflow qui empaquette le SOCLE tout en publiant une CHARGE ;</li>
 *   <li><b>Pièges de version</b> — les deux échecs classiques : une charge qui ne se met jamais à
 *       jour, et une version de socle si basse que la charge afficherait l'écran bloquant
 *       « Launcher obsolète » à tous les testeurs.</li>
 * </ol>
 */
class DevUpdatePathTest {

    // ── Famille 1 : la production ne bouge pas ────────────────────────────────────────────────

    /**
     * Valeur littérale et historique du manifeste de production. Elle est recopiée ici EXPRÈS
     * plutôt que référencée : c'est un témoin. Si quelqu'un modifie la constante du socle, ce test
     * tombe et force à se demander si les socles déjà installés chez les joueurs suivront.
     */
    private static final String MANIFESTE_PROD_HISTORIQUE =
            "https://nyle-mc-server.pages.dev/launcher/manifest.json";

    @Test
    @DisplayName("PROD : le manifeste reste exactement celui des socles déjà installés")
    void manifesteProdInchange() {
        assertEquals(MANIFESTE_PROD_HISTORIQUE, Bootstrap.manifestUrlFor("prod"));
        assertEquals(MANIFESTE_PROD_HISTORIQUE, Bootstrap.PROD_MANIFEST_URL);
    }

    @Test
    @DisplayName("PROD : aucune propriété système ne peut détourner un socle de production")
    void prodNonDetournable() {
        String avant = System.getProperty("nylerp.devManifestUrl");
        try {
            System.setProperty("nylerp.devManifestUrl", "http://127.0.0.1:9/pirate.json");
            assertEquals(MANIFESTE_PROD_HISTORIQUE, Bootstrap.manifestUrlFor("prod"),
                    "la dérivation de manifeste doit rester réservée au canal DEV");
            // ... alors que le canal DEV, lui, DOIT être détournable : c'est ce qui rend le
            // chemin de mise à jour éprouvable en local, sans rien publier.
            assertEquals("http://127.0.0.1:9/pirate.json", Bootstrap.manifestUrlFor("dev"));
        } finally {
            if (avant == null) System.clearProperty("nylerp.devManifestUrl");
            else System.setProperty("nylerp.devManifestUrl", avant);
        }
    }

    @Test
    @DisplayName("PROD : le dossier de cache reste NyleRP/payload")
    void cacheProdInchange() {
        Path prod = Bootstrap.cacheDirFor("prod");
        assertEquals("payload", prod.getFileName().toString());
        assertEquals("NyleRP", prod.getParent().getFileName().toString());
    }

    @Test
    @DisplayName("Canal inconnu, vide ou absent : repli en production")
    void canalFailSafeProd() {
        for (String douteux : new String[] { null, "", "   ", "DEV_", "beta", "PROD", "Dev " }) {
            String attendu = "Dev ".equals(douteux) ? "dev" : "prod";  // trim + minuscules
            assertEquals(attendu, Bootstrap.normalizeChannel(douteux),
                    "canal douteux mal normalisé : " + douteux);
        }
        assertEquals(MANIFESTE_PROD_HISTORIQUE, Bootstrap.manifestUrlFor(null));
        assertEquals(MANIFESTE_PROD_HISTORIQUE, Bootstrap.manifestUrlFor("n'importe quoi"));
    }

    // ── Famille 2 : le canal DEV se met bien à jour ───────────────────────────────────────────

    @Test
    @DisplayName("DEV : manifeste et cache STRICTEMENT distincts de la production")
    void devEstIsoleDeLaProd() {
        assertNotEquals(Bootstrap.manifestUrlFor("prod"), Bootstrap.manifestUrlFor("dev"),
                "un testeur DEV recevrait la charge des joueurs");
        assertTrue(Bootstrap.DEV_MANIFEST_URL.contains("dev-payload"),
                "le manifeste DEV doit être porté par le tag dev-payload : " + Bootstrap.DEV_MANIFEST_URL);

        Path prod = Bootstrap.cacheDirFor("prod");
        Path dev = Bootstrap.cacheDirFor("dev");
        assertNotEquals(prod, dev,
                "caches partagés : le repli hors-ligne d'un socle de PROD pourrait démarrer une "
                        + "charge DEV et envoyer un joueur sur le serveur de développement");
        assertEquals("payload-dev", dev.getFileName().toString());
    }

    @Test
    @DisplayName("DEV : le paquet distribué est le SOCLE, pas la charge empaquetée")
    void leWorkflowDevEmpaqueteLeSocle() {
        // On lit le workflow SANS ses lignes de commentaire : l'en-tête du fichier raconte
        // volontairement l'ancien montage (« --main-class fr.nylerp.launcher.Main ») pour que la
        // régression soit reconnaissable, et cette prose ne doit pas faire tomber le test.
        String yml = lireSansCommentaires(".github/workflows/dev-launcher.yml");

        assertTrue(yml.contains("--main-class fr.nylerp.bootstrap.Bootstrap"),
                "REGRESSION : l'installeur DEV doit empaqueter le socle. Sans lui, le launcher DEV "
                        + "n'a aucun moyen d'aller chercher une nouvelle version et les testeurs "
                        + "doivent réinstaller à chaque itération.");
        assertFalse(yml.contains("--main-class fr.nylerp.launcher.Main"),
                "REGRESSION : le workflow DEV empaquette de nouveau la charge directement "
                        + "(--main-class fr.nylerp.launcher.Main). C'est précisément le "
                        + "court-circuit qui privait le canal DEV de mise à jour.");
        assertTrue(yml.contains(":bootstrap:shadowJar"),
                "l'installeur DEV doit être construit à partir du sous-projet bootstrap");
        assertTrue(yml.contains("-Pchannel=dev"),
                "le socle DEV doit être construit en canal dev, sinon il lit le manifeste de PROD");
    }

    @Test
    @DisplayName("DEV : une charge est bien publiée, sinon le socle n'a rien à télécharger")
    void leWorkflowDevPublieUneCharge() {
        String yml = lire(".github/workflows/dev-launcher.yml");

        assertTrue(yml.contains("tag_name: dev-payload"),
                "REGRESSION : plus aucune charge DEV n'est publiée sur le tag dev-payload — "
                        + "le socle DEV démarrerait éternellement sur sa charge en cache.");
        assertTrue(yml.contains("manifest.json"),
                "la publication de la charge doit inclure son manifest.json");
        assertTrue(yml.contains("\"main_class\": \"fr.nylerp.launcher.Main\""),
                "le manifeste DEV doit désigner la classe principale de la charge");
        assertTrue(yml.contains("sha256"),
                "le manifeste DEV doit porter un SHA-256, seul critère de retéléchargement du socle");
        assertTrue(yml.contains("draft: false"),
                "une release restée en brouillon rend ses actifs introuvables (404)");
        assertTrue(yml.contains(":bootstrap:test"),
                "ce test doit rester branché sur le workflow DEV, sinon il ne garde plus rien");
    }

    @Test
    @DisplayName("PROD : le workflow de production empaquette toujours le socle")
    void leWorkflowProdResteUnSocle() {
        String yml = lire(".github/workflows/bootstrap.yml");
        assertTrue(yml.contains("--main-class fr.nylerp.bootstrap.Bootstrap"));
        assertFalse(yml.contains("-Pchannel=dev"),
                "le socle de PRODUCTION ne doit jamais être construit en canal dev");
    }

    // ── Famille 3 : les deux pièges de version ────────────────────────────────────────────────

    @Test
    @DisplayName("Piège 1 — la version du socle DEV vient d'une seule source, jamais d'un littéral figé")
    void versionDuSocleDevNonDerivante() {
        String yml = lire(".github/workflows/dev-launcher.yml");
        assertTrue(yml.contains("-PbootstrapVersion=\"${DEV_BOOTSTRAP_VERSION}\""),
                "sans -PbootstrapVersion, le socle DEV embarque le littéral de bootstrap/build.gradle "
                        + "et dérive en silence — c'est l'incident de juillet.");
        assertTrue(yml.contains("--app-version \"$APP_VER\""),
                "jpackage doit recevoir la version dérivée de la MÊME variable");
    }

    @Test
    @DisplayName("Piège 2 — la version du socle DEV ne doit pas déclencher l'écran « Launcher obsolète »")
    void socleDevAuDessusDuMinimumExige() {
        String minimum = extraire(
                lire("src/main/java/fr/nylerp/launcher/update/BrokenBootstrapDialog.java"),
                "MIN_REQUIRED_BOOTSTRAP\\s*=\\s*\"([^\"]+)\"");
        String devVersion = extraire(
                lire(".github/workflows/dev-launcher.yml"),
                "DEV_BOOTSTRAP_VERSION:\\s*\"([^\"]+)\"");

        assertTrue(comparer(devVersion, minimum) >= 0,
                "le socle DEV (" + devVersion + ") est sous le minimum exigé par la charge ("
                        + minimum + ") : tous les testeurs DEV verraient l'écran bloquant "
                        + "« Launcher obsolète », qui les envoie télécharger le launcher de PRODUCTION.");
    }

    @Test
    @DisplayName("Piège 3 — une charge inchangée ne doit PAS être retéléchargée en boucle")
    void pasDeBoucleDeTelechargement() {
        // Le socle nomme sa charge en cache launcher-<version>.jar et ne retélécharge que si le
        // fichier manque OU si son SHA-256 diffère du manifeste. Deux invariants en découlent, et
        // ce sont eux qui empêchent la boucle de mise à jour perpétuelle : le nom du fichier doit
        // être dérivé de la version du manifeste, et la comparaison doit porter sur le hachage.
        String src = lire("bootstrap/src/main/java/fr/nylerp/bootstrap/Bootstrap.java");
        assertTrue(src.contains("cache.resolve(\"launcher-\" + manifest.version + \".jar\")"),
                "le nom de la charge en cache doit être dérivé de la version du manifeste");
        assertTrue(src.contains("sha256(target).equalsIgnoreCase(manifest.sha256)"),
                "le retéléchargement doit être décidé par le SHA-256, pas par une comparaison de "
                        + "numéros de version (qui reboucle dès que les numéros divergent)");
    }

    // ── Outils ───────────────────────────────────────────────────────────────────────────────

    /** Racine du dépôt, trouvée en remontant depuis le répertoire de travail du test. */
    private static Path racine() {
        Path p = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && p != null; i++) {
            if (Files.exists(p.resolve("settings.gradle"))) return p;
            p = p.getParent();
        }
        throw new IllegalStateException("racine du dépôt introuvable depuis "
                + System.getProperty("user.dir"));
    }

    private static String lire(String relatif) {
        Path f = racine().resolve(relatif);
        assertTrue(Files.exists(f), "fichier attendu absent : " + f);
        try {
            return new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("lecture impossible : " + f, e);
        }
    }

    /** Le fichier privé de ses lignes de commentaire — pour n'asserter que sur le YAML effectif. */
    private static String lireSansCommentaires(String relatif) {
        return lire(relatif).lines()
                .filter(l -> !l.stripLeading().startsWith("#"))
                .reduce(new StringBuilder(), (sb, l) -> sb.append(l).append('\n'), StringBuilder::append)
                .toString();
    }

    private static String extraire(String texte, String regex) {
        Matcher m = Pattern.compile(regex).matcher(texte);
        assertTrue(m.find(), "motif introuvable : " + regex);
        return m.group(1);
    }

    /** Comparaison de versions « 0.3.22 » façon semver simplifié. */
    private static int comparer(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int ia = i < pa.length ? entier(pa[i]) : 0;
            int ib = i < pb.length ? entier(pb[i]) : 0;
            if (ia != ib) return Integer.compare(ia, ib);
        }
        return 0;
    }

    private static int entier(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); }
        catch (Exception e) { return 0; }
    }
}
