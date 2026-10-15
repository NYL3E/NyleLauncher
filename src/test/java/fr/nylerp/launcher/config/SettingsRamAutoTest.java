package fr.nylerp.launcher.config;

import fr.nylerp.launcher.launch.MinecraftLauncher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mémoire « Auto » (15/10/2026) : 6 Go si le PC a ≥ 16 Go, 5 Go si ≥ 12 Go, sinon 4 Go, toujours
 * sous le plafond physique (RAM − 2 Go, plancher 2 Go).
 *
 * <p>Isolation : {@code user.home} redirigé vers un dossier temporaire, singleton remis à zéro.
 */
class SettingsRamAutoTest {

    private String realHome;

    @BeforeEach
    void avant() throws Exception {
        realHome = System.getProperty("user.home");
        System.setProperty("user.home", Files.createTempDirectory("nyle-ram-auto").toString());
        reset();
    }

    @AfterEach
    void apres() throws Exception {
        System.setProperty("user.home", realHome);
        reset();
    }

    private static void reset() throws Exception {
        Field f = Settings.class.getDeclaredField("current");
        f.setAccessible(true);
        f.set(null, null);
    }

    // ── Choix selon la RAM ──────────────────────────────────────────────────────────────────

    @Test
    void paliers() {
        assertEquals(6144, Settings.autoRamMb(16384));   // 16 Go
        assertEquals(6144, Settings.autoRamMb(32768));
        assertEquals(5120, Settings.autoRamMb(12288));   // 12 Go
        assertEquals(4096, Settings.autoRamMb(8192));    // 8 Go
        assertEquals(4096, Settings.autoRamMb(6144));    // 6 Go
        assertEquals(4096, Settings.autoRamMb(-1));      // détection ratée
        assertEquals(4096, Settings.autoRamMb(0));
    }

    /** Windows rapporte la mémoire UTILISABLE : un PC « 16 Go » remonte ~15,9 Go. */
    @Test
    void pcSeizeGoVuParWindows() {
        assertEquals(6144, Settings.autoRamMb(16_286));
        assertEquals(6144, Settings.autoRamMb(15_800));
        assertEquals(5120, Settings.autoRamMb(12_150));
        assertEquals(5120, Settings.autoRamMb(11_900));
    }

    /** Chaîne complète Auto → plafond physique, telle que lancée. */
    @Test
    void xmxEffectifAuto() {
        assertEquals(6144, MinecraftLauncher.clampHeapMb(Settings.autoRamMb(16384), 16384));
        assertEquals(5120, MinecraftLauncher.clampHeapMb(Settings.autoRamMb(12288), 12288));
        assertEquals(4096, MinecraftLauncher.clampHeapMb(Settings.autoRamMb(8192), 8192));
        assertEquals(4096, MinecraftLauncher.clampHeapMb(Settings.autoRamMb(6144), 6144));
        assertEquals(2048, MinecraftLauncher.clampHeapMb(Settings.autoRamMb(4096), 4096));
        assertEquals(4096, MinecraftLauncher.clampHeapMb(Settings.autoRamMb(-1), -1));
    }

    @Test
    void autoIgnoreLeCurseur_manuelLeSuit() {
        Settings s = new Settings();
        assertTrue(s.ramAuto, "défaut : Auto");
        s.ramMb = 8192;
        assertEquals(6144, s.requestedRamMb(16384));
        s.ramAuto = false;
        assertEquals(8192, s.requestedRamMb(16384));
    }

    // ── Réglages existants (Gson) ───────────────────────────────────────────────────────────

    @Test
    void installationNeuve_auto() {
        assertTrue(Settings.get().ramAuto);
        assertEquals(4096, Settings.get().ramMb);
    }

    @Test
    void ancienFichierAuDefaut_passeEnAuto() {
        Settings s = Settings.parse("{\"ramMb\":4096,\"optionalIris\":true}");
        assertTrue(s.ramAuto, "clé absente + 4 Go (défaut historique) → Auto");
        assertTrue(s.optionalIris, "les autres réglages sont gardés");
    }

    @Test
    void ancienFichierSansRamMb_passeEnAuto() {
        assertTrue(Settings.parse("{\"modeDeJeu\":\"pokenyle\"}").ramAuto);
    }

    @Test
    void ancienFichierAvecChoixDuJoueur_gardeSonChoix() {
        Settings s = Settings.parse("{\"ramMb\":8192}");
        assertFalse(s.ramAuto, "8 Go choisis au curseur : on ne les reprend pas");
        assertEquals(8192, s.requestedRamMb(16384));
        assertEquals(2048, Settings.parse("{\"ramMb\":2048}").requestedRamMb(8192));
    }

    @Test
    void cleExplicite_faitFoi() {
        assertTrue(Settings.parse("{\"ramMb\":8192,\"ramAuto\":true}").ramAuto);
        assertFalse(Settings.parse("{\"ramMb\":4096,\"ramAuto\":false}").ramAuto);
    }

    @Test
    void allerRetourDisque() throws Exception {
        Settings.get().ramAuto = false;
        Settings.get().ramMb = 6144;
        Settings.get().save();
        String json = Files.readString(AppPaths.settingsFile());
        assertTrue(json.replaceAll("\\s+", "").contains("\"ramAuto\":false"), json);
        reset();
        assertFalse(Settings.get().ramAuto);
        assertEquals(6144, Settings.get().ramMb);

        Settings.get().ramAuto = true;
        Settings.get().save();
        reset();
        assertTrue(Settings.get().ramAuto, "retour en Auto persisté, même avec ramMb ≠ 4 Go");
    }

    @Test
    void fichierIllisible_defautAuto() throws Exception {
        Path f = AppPaths.settingsFile();
        Files.writeString(f, "{ pas du json");
        reset();
        assertTrue(Settings.get().ramAuto);
    }
}
