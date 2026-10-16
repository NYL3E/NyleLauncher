package fr.nylerp.launcher.fond;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Entrées de démarrage automatique : écrites au bon endroit, avec {@code --fond}, jamais
 * réécrites à l'identique (macOS afficherait « Élément d'arrière-plan ajouté » à chaque fois),
 * retirées proprement. Tout se passe dans un dossier temporaire : aucune entrée réelle.
 */
class DemarrageAutoTest {

    private static final Path APP_EXE = Paths.get("/Applications/NyleLauncher.app/Contents/MacOS/NyleLauncher");

    @Test
    void mac_ecrit_un_launchagent_valide_puis_le_retire(@TempDir Path home) throws Exception {
        DemarrageAutoMac d = new DemarrageAutoMac(home, "fr.nylerp.launcher.test");
        assertFalse(d.estActif());
        d.activer(APP_EXE);
        assertTrue(d.estActif());
        assertEquals(home.resolve("Library/LaunchAgents/fr.nylerp.launcher.test.plist"), d.fichier());
        String plist = Files.readString(d.fichier());
        assertTrue(plist.contains("<string>fr.nylerp.launcher.test</string>"));
        assertTrue(plist.contains("<string>/usr/bin/open</string>"));
        assertTrue(plist.contains("<string>-g</string>"));
        assertTrue(plist.contains("<string>/Applications/NyleLauncher.app</string>"), "vise le bundle, pas l'exécutable");
        assertTrue(plist.contains("<string>--fond</string>"));
        assertTrue(plist.contains("<key>RunAtLoad</key>"));
        if (Systeme.mac()) {
            Process p = new ProcessBuilder("/usr/bin/plutil", "-lint", d.fichier().toString()).redirectErrorStream(true).start();
            assertTrue(p.waitFor(30, TimeUnit.SECONDS));
            assertEquals(0, p.exitValue(), new String(p.getInputStream().readAllBytes()));
        }
        d.desactiver();
        assertFalse(d.estActif());
        d.desactiver(); // sans effet la seconde fois
    }

    @Test
    void mac_ne_reecrit_pas_une_entree_identique(@TempDir Path home) throws Exception {
        DemarrageAutoMac d = new DemarrageAutoMac(home, "fr.nylerp.launcher.test");
        d.activer(APP_EXE);
        Files.setLastModifiedTime(d.fichier(), java.nio.file.attribute.FileTime.fromMillis(1_000_000_000L));
        d.activer(APP_EXE);
        assertEquals(1_000_000_000L, Files.getLastModifiedTime(d.fichier()).toMillis(), "aucune réécriture");
        d.activer(Paths.get("/Users/x/Applications/NyleLauncher.app/Contents/MacOS/NyleLauncher"));
        assertNotEquals(1_000_000_000L, Files.getLastModifiedTime(d.fichier()).toMillis(), "app déplacée : mise à jour");
        assertTrue(Files.readString(d.fichier()).contains("/Users/x/Applications/NyleLauncher.app"));
    }

    @Test
    void mac_echappe_le_chemin(@TempDir Path home) throws Exception {
        DemarrageAutoMac d = new DemarrageAutoMac(home, "fr.nylerp.launcher.test");
        d.activer(Paths.get("/Applications/Nyle & Co <test>.app/Contents/MacOS/NyleLauncher"));
        assertTrue(Files.readString(d.fichier()).contains("/Applications/Nyle &amp; Co &lt;test&gt;.app"));
    }

    @Test
    void mac_bundle_depuis_executable() {
        assertEquals(Paths.get("/Applications/NyleLauncher.app"), DemarrageAutoMac.bundle(APP_EXE));
        Path nu = Paths.get("/opt/nyle/bin/NyleLauncher");
        assertEquals(nu, DemarrageAutoMac.bundle(nu));
    }

    @Test
    void linux_ecrit_un_desktop_autostart_puis_le_retire(@TempDir Path config) throws Exception {
        DemarrageAutoLinux d = new DemarrageAutoLinux(config, "nylelauncher", "NyleLauncher");
        d.activer(Paths.get("/opt/nylelauncher/bin/NyleLauncher"));
        assertEquals(config.resolve("autostart/nylelauncher.desktop"), d.fichier());
        String txt = Files.readString(d.fichier());
        assertTrue(txt.startsWith("[Desktop Entry]\n"));
        assertTrue(txt.contains("Type=Application\n"));
        assertTrue(txt.contains("Exec=\"/opt/nylelauncher/bin/NyleLauncher\" --fond\n"));
        assertTrue(txt.contains("X-GNOME-Autostart-enabled=true\n"));
        Files.setLastModifiedTime(d.fichier(), java.nio.file.attribute.FileTime.fromMillis(1_000_000_000L));
        d.activer(Paths.get("/opt/nylelauncher/bin/NyleLauncher"));
        assertEquals(1_000_000_000L, Files.getLastModifiedTime(d.fichier()).toMillis());
        d.desactiver();
        assertFalse(d.estActif());
    }

    @Test
    void linux_echappe_exec_selon_la_specification() {
        assertEquals("\"/home/a b/c\\$d\\\"e\\\\f\"", DemarrageAutoLinux.guillemets("/home/a b/c$d\"e\\f"));
    }

    @Test
    void windows_commande_entre_guillemets() {
        assertEquals("\"" + Paths.get("C:/Program Files/NyleLauncher/NyleLauncher.exe").toAbsolutePath() + "\" --fond",
                DemarrageAutoWindows.commande(Paths.get("C:/Program Files/NyleLauncher/NyleLauncher.exe")));
    }

    @Test
    void windows_registre_reel_sous_un_nom_de_test() throws Exception {
        assumeTrue(Systeme.windows(), "registre : Windows uniquement");
        DemarrageAutoWindows d = new DemarrageAutoWindows("NyleLauncher-test-unitaire");
        try {
            d.activer(Paths.get("C:/Program Files/NyleLauncher/NyleLauncher.exe"));
            assertTrue(d.estActif());
        } finally {
            d.desactiver();
        }
        assertFalse(d.estActif());
    }

    @Test
    void refuse_les_emplacements_ephemeres() {
        assertFalse(DemarrageAuto.cheminInscriptible(Paths.get("/Volumes/NyleLauncher/NyleLauncher.app/Contents/MacOS/NyleLauncher")));
        assertFalse(DemarrageAuto.cheminInscriptible(Paths.get(
                "/private/var/folders/ab/T/AppTranslocation/1234/d/NyleLauncher.app/Contents/MacOS/NyleLauncher")));
        assertTrue(DemarrageAuto.cheminInscriptible(APP_EXE));
    }
}
