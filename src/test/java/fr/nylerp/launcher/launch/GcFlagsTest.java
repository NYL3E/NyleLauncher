package fr.nylerp.launcher.launch;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drapeaux G1 du client (15/10/2026). Diagnostic : tas de 4 Go, ensemble vivant 3,3-3,6 Go,
 * 2 187 nettoyages complets en 3 jours. Les drapeaux « Aikar » (serveur) mangeaient la marge :
 * G1NewSizePercent=20, G1ReservePercent=20, InitiatingHeapOccupancyPercent=15.
 */
class GcFlagsTest {

    @Test
    void listeExacteDesDrapeaux() {
        assertEquals(List.of(
                "-XX:+UseG1GC",
                "-XX:MaxGCPauseMillis=50",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+ParallelRefProcEnabled",
                "-XX:G1HeapRegionSize=8M",
                "-XX:G1NewSizePercent=5",
                "-XX:G1MaxNewSizePercent=40",
                "-XX:G1ReservePercent=10",
                "-XX:G1HeapWastePercent=5",
                "-XX:G1MixedGCCountTarget=4",
                "-XX:SurvivorRatio=32",
                "-XX:+DisableExplicitGC",
                "-XX:+UseStringDeduplication",
                "-XX:+PerfDisableSharedMem",
                "-XX:ReservedCodeCacheSize=512M"
        ), MinecraftLauncher.gcFlags());
    }

    @Test
    void plusAucunDrapeauDeServeur() {
        List<String> f = MinecraftLauncher.gcFlags();
        assertTrue(f.stream().noneMatch(s -> s.startsWith("-XX:InitiatingHeapOccupancyPercent")),
                "IHOP doit rester adaptatif (défaut G1) : " + f);
        assertFalse(f.contains("-XX:G1NewSizePercent=20"), f.toString());
        assertFalse(f.contains("-XX:G1ReservePercent=20"), f.toString());
        assertFalse(f.contains("-XX:+AlwaysPreTouch"), "AlwaysPreTouch engage tout le tas au démarrage");
    }

    /** G1NewSizePercent / G1MaxNewSizePercent sont expérimentaux : sans le déverrouillage AVANT
     *  eux, la JVM refuse de démarrer (« Unrecognized VM option »). */
    @Test
    void deverrouillageAvantLesOptionsExperimentales() {
        List<String> f = MinecraftLauncher.gcFlags();
        int unlock = f.indexOf("-XX:+UnlockExperimentalVMOptions");
        assertTrue(unlock >= 0);
        for (int i = 0; i < f.size(); i++) {
            if (f.get(i).startsWith("-XX:G1NewSizePercent") || f.get(i).startsWith("-XX:G1MaxNewSizePercent")) {
                assertTrue(i > unlock, f.get(i) + " avant le déverrouillage");
            }
        }
    }

    @Test
    void chaqueDrapeauUneSeuleFois() {
        List<String> f = MinecraftLauncher.gcFlags();
        assertEquals(f.size(), f.stream().map(s -> s.split("=")[0]).distinct().count(), f.toString());
    }

    @Test
    void plafondPhysique() {
        assertEquals(6144, MinecraftLauncher.clampHeapMb(6144, 16384));   // 16 Go : rien à couper
        assertEquals(4096, MinecraftLauncher.clampHeapMb(4096, 8192));
        assertEquals(4096, MinecraftLauncher.clampHeapMb(4096, 6144));    // 6 Go − 2 Go
        assertEquals(2048, MinecraftLauncher.clampHeapMb(4096, 4096));    // plancher 2 Go
        assertEquals(2048, MinecraftLauncher.clampHeapMb(4096, 3000));
        assertEquals(5120, MinecraftLauncher.clampHeapMb(5120, -1));      // détection ratée : valeur gardée
    }
}
