package fr.nylerp.launcher.discord;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Où chercher le canal de Discord, selon le système et l'environnement (aucun accès disque). */
class CheminsIpcTest {

    private static List<String> pour(Map<String, String> env, String os) {
        return CheminsIpc.candidats(env::get, os);
    }

    @Test
    void laVariableDEssaiRemplaceTout() {
        Map<String, String> env = Map.of(CheminsIpc.VARIABLE_ESSAI, "/tmp/essai/discord-ipc-0", "TMPDIR", "/var/folders/x/T/");
        assertEquals(List.of("/tmp/essai/discord-ipc-0"), pour(env, "Mac OS X"));
        assertEquals(List.of("\\\\.\\pipe\\nyle-essai-discord"),
                pour(Map.of(CheminsIpc.VARIABLE_ESSAI, "\\\\.\\pipe\\nyle-essai-discord"), "Windows 11"));
    }

    @Test
    void windowsDixTubesNommes() {
        List<String> c = pour(Map.of("TMP", "C:\\Temp"), "Windows 10");
        assertEquals(10, c.size());
        assertEquals("\\\\?\\pipe\\discord-ipc-0", c.get(0));
        assertEquals("\\\\?\\pipe\\discord-ipc-9", c.get(9));
        assertTrue(CheminsIpc.estTubeWindows(c.get(0)));
        assertTrue(CheminsIpc.estTubeWindows("\\\\.\\pipe\\nyle-essai-discord"));
        assertFalse(CheminsIpc.estTubeWindows("/tmp/discord-ipc-0"));
    }

    @Test
    void macTmpdirPuisTmp() {
        List<String> c = pour(Map.of("TMPDIR", "/var/folders/ab/cd/T/"), "Mac OS X");
        assertEquals("/var/folders/ab/cd/T/discord-ipc-0", c.get(0));
        assertTrue(c.indexOf("/tmp/discord-ipc-0") > c.indexOf("/var/folders/ab/cd/T/discord-ipc-9"));
        assertTrue(c.stream().noneMatch(p -> p.contains("snap") || p.contains("/app/")), "Flatpak et Snap : Linux seulement");
        assertEquals(20, c.size());
    }

    @Test
    void linuxOrdreDeLaDocPuisFlatpakEtSnap() {
        List<String> c = pour(Map.of("XDG_RUNTIME_DIR", "/run/user/1000", "TMPDIR", "/var/tmp", "TEMP", "/tmp"), "Linux");
        assertEquals("/run/user/1000/discord-ipc-0", c.get(0));
        assertTrue(c.indexOf("/run/user/1000/discord-ipc-9") < c.indexOf("/var/tmp/discord-ipc-0"));
        assertTrue(c.indexOf("/var/tmp/discord-ipc-9") < c.indexOf("/tmp/discord-ipc-0"));
        assertTrue(c.contains("/run/user/1000/app/com.discordapp.Discord/discord-ipc-0"));
        assertTrue(c.contains("/run/user/1000/snap.discord/discord-ipc-3"));
        assertEquals(c.size(), c.stream().distinct().count(), "« /tmp » donné deux fois n'est essayé qu'une fois");
    }

    @Test
    void sansVariableSeulTmp() {
        List<String> c = pour(Map.of(), "Linux");
        assertEquals(10, c.size());
        assertEquals("/tmp/discord-ipc-0", c.get(0));
    }
}
