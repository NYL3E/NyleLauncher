package fr.nylerp.launcher.update;

import fr.nylerp.launcher.config.AppPaths;
import fr.nylerp.launcher.config.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Raccordement du mod optionnel « Punchy! ». Le défaut classique de ce registre n'est pas la
 * compilation — une entrée orpheline compile parfaitement — mais le fil coupé quelque part entre
 * les quatre points de branchement : le champ {@link Settings}, l'{@link OptionalMods.Entry}, le
 * lambda {@code enabled} qui doit viser CE champ-là, et la ligne d'UI qui doit réellement finir
 * dans la VBox rendue.
 *
 * <p>Chaque test ci-dessous casse si l'un de ces quatre fils est coupé.
 *
 * <p>Isolation : {@code user.home} est redirigé vers un dossier temporaire (AppPaths le relit à
 * chaque appel) et le singleton {@code Settings.current} est remis à zéro entre les tests — aucun
 * fichier de la vraie installation du joueur n'est lu ni écrit.
 */
class OptionalPunchyWiringTest {

    private static final String PUNCHY_JAR = "punchy-2.6.2-fabric-1.21.1.jar";

    private String realHome;
    private Path tempHome;

    @BeforeEach
    void setup() throws Exception {
        realHome = System.getProperty("user.home");
        tempHome = Files.createTempDirectory("nyle-launcher-punchy-test");
        System.setProperty("user.home", tempHome.toString());
        resetSettingsSingleton();
    }

    @AfterEach
    void teardown() throws Exception {
        System.setProperty("user.home", realHome);
        resetSettingsSingleton();
    }

    /** Remet {@code Settings.current} à null pour forcer une relecture disque au prochain get(). */
    private static void resetSettingsSingleton() throws Exception {
        Field f = Settings.class.getDeclaredField("current");
        f.setAccessible(true);
        f.set(null, null);
    }

    private static OptionalMods.Entry punchy() {
        return OptionalMods.ENTRIES.stream()
                .filter(e -> PUNCHY_JAR.equals(e.fileName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Aucune entrée " + PUNCHY_JAR + " dans OptionalMods.ENTRIES"));
    }

    // ── 1. L'entrée existe et est bien formée ────────────────────────────────────────────────

    @Test
    void entryIsRegisteredAndWellFormed() {
        OptionalMods.Entry e = punchy();

        // Le fichier doit atterrir dans game/mods sous CE nom exact (applyOne resolve dessus).
        assertEquals(PUNCHY_JAR, e.fileName());
        assertTrue(e.url().endsWith(PUNCHY_JAR),
                "L'URL doit servir exactement le fichier attendu, sinon le SHA ne matchera jamais : " + e.url());
        assertTrue(e.url().startsWith("https://cdn.modrinth.com/data/8aoMKplv/"),
                "URL CDN Modrinth du projet Punchy! (8aoMKplv) attendue : " + e.url());

        // SHA-512 = 128 caractères hex. Un hash tronqué/mal collé ne se voit pas à la lecture,
        // mais applyOne supprimerait le jar en silence à chaque lancement.
        assertEquals(128, e.sha512().length(), "Un SHA-512 hex fait 128 caractères");
        assertTrue(e.sha512().matches("[0-9a-fA-F]{128}"), "SHA-512 non hexadécimal");
    }

    /** Une seule occurrence dans filenames() = présent dans ENTRIES et ABSENT de
     *  DEPRECATED_FILENAMES. S'il était dans les deux, applyAll() le supprimerait
     *  inconditionnellement au début de chaque lancement, juste avant de le réinstaller. */
    @Test
    void punchyIsNotAlsoMarkedDeprecated() {
        List<String> all = OptionalMods.filenames();
        long n = all.stream().filter(PUNCHY_JAR::equals).count();
        assertEquals(1, n,
                "Punchy doit apparaître exactement une fois dans filenames() (ENTRIES seulement, "
                        + "jamais DEPRECATED_FILENAMES) — trouvé " + n + " fois dans " + all);
    }

    // ── 2. Le champ Settings survit à un aller-retour disque ─────────────────────────────────

    @Test
    void settingsFieldSurvivesDiskRoundTrip() throws Exception {
        assertFalse(Settings.get().optionalPunchy, "Défaut attendu : désactivé");

        Settings.get().optionalPunchy = true;
        Settings.get().save();

        // (a) le champ est bien SÉRIALISÉ, sous ce nom exact, dans le settings.json du joueur
        Path file = AppPaths.settingsFile();
        assertTrue(Files.exists(file), "settings.json non écrit : " + file);
        String json = Files.readString(file);
        assertTrue(json.contains("\"optionalPunchy\""),
                "Le champ n'apparaît pas dans le JSON écrit — Gson ne le sérialise pas :\n" + json);
        assertTrue(json.replaceAll("\\s+", "").contains("\"optionalPunchy\":true"),
                "Valeur non persistée :\n" + json);

        // (b) et il est bien DÉSÉRIALISÉ : relecture à froid, comme au prochain démarrage
        resetSettingsSingleton();
        assertTrue(Settings.get().optionalPunchy,
                "Le toggle est perdu au redémarrage du launcher (désérialisation cassée)");

        // (c) aller-retour inverse : le retour à false doit persister aussi
        Settings.get().optionalPunchy = false;
        Settings.get().save();
        resetSettingsSingleton();
        assertFalse(Settings.get().optionalPunchy, "Le désactivage n'est pas persisté");
    }

    /** Un settings.json d'un joueur qui met à jour depuis une version SANS Punchy ne contient pas
     *  la clé : elle doit prendre la valeur par défaut (false) sans faire exploser le parsing. */
    @Test
    void olderSettingsFileWithoutTheKeyStillLoads() throws Exception {
        Path file = AppPaths.settingsFile();
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"ramMb\":6144,\"optionalLitematica\":true}");
        resetSettingsSingleton();

        Settings s = Settings.get();
        assertEquals(6144, s.ramMb, "Les réglages existants doivent être préservés");
        assertTrue(s.optionalLitematica);
        assertFalse(s.optionalPunchy, "Clé absente → défaut false, pas de crash");
    }

    // ── 3. Le lambda `enabled` vise CE champ, et lui seul ────────────────────────────────────

    @Test
    void enabledSupplierIsBoundToOptionalPunchy() {
        OptionalMods.Entry e = punchy();
        Settings s = Settings.get();

        s.optionalPunchy = false;
        assertFalse(e.enabled().getAsBoolean(), "OFF doit donner false (sinon installation forcée)");

        s.optionalPunchy = true;
        assertTrue(e.enabled().getAsBoolean(),
                "ON doit donner true — le lambda ne lit pas optionalPunchy");
    }

    /** Le lambda ne doit pas être branché par erreur sur le champ d'un autre mod (copier-coller). */
    @Test
    void enabledSupplierIgnoresTheOtherToggles() {
        OptionalMods.Entry e = punchy();
        Settings s = Settings.get();

        s.optionalPunchy = false;
        s.optionalLitematica = true;
        s.optionalIris = true;
        s.optionalDistantHorizons = true;
        assertFalse(e.enabled().getAsBoolean(),
                "Punchy s'active alors que seuls les AUTRES toggles sont ON — lambda mal branché");

        // et réciproquement : activer Punchy ne doit contaminer aucune autre entrée
        s.optionalPunchy = true;
        s.optionalLitematica = false;
        s.optionalIris = false;
        s.optionalDistantHorizons = false;
        for (OptionalMods.Entry other : OptionalMods.ENTRIES) {
            if (PUNCHY_JAR.equals(other.fileName())) continue;
            assertFalse(other.enabled().getAsBoolean(),
                    "Activer Punchy active aussi " + other.fileName());
        }
    }

    // ── 4. La ligne d'UI est réellement dans la VBox rendue ──────────────────────────────────

    /**
     * Le bug le plus bête et le plus probable : {@code punchyRow} est construit mais jamais ajouté
     * au {@code new VBox(...)} retourné par {@code modsSection()} — ça compile, et le toggle est
     * simplement invisible pour le joueur.
     *
     * <p>Vérifié sur la SOURCE plutôt qu'en instanciant la vue : construire un SettingsView
     * exigerait un toolkit JavaFX (polices, skins) qu'aucun runner headless ne garantit, alors que
     * la question posée ici est purement structurelle.
     */
    @Test
    void punchyRowIsBothBuiltAndAddedToTheModsSection() throws Exception {
        Path src = Path.of("src/main/java/fr/nylerp/launcher/ui/SettingsView.java");
        Assumptions.assumeTrue(Files.exists(src),
                "source SettingsView.java introuvable depuis " + Path.of("").toAbsolutePath());
        String code = Files.readString(src);

        assertTrue(code.contains("HBox punchyRow = optionalModRow("),
                "La ligne d'UI Punchy n'est pas construite dans SettingsView");

        // La VBox de modsSection() est celle qui liste litematicaRow : c'est la seule de la classe.
        String vbox = code.lines()
                .filter(l -> l.contains("new VBox(") && l.contains("litematicaRow"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "VBox de modsSection() introuvable — le test doit être remis à jour"));

        assertTrue(vbox.contains("punchyRow"),
                "punchyRow est construit mais JAMAIS ajouté à la VBox → toggle invisible en jeu :\n"
                        + vbox.trim());
    }
}
