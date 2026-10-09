package fr.nylerp.launcher.update;

import fr.nylerp.launcher.config.AppPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Installation neuve, 1er clic sur « JOUER » : la migration de touches passait AVANT le sync et créait
 * un options.txt d'une ligne. options.txt étant « firstInstallOnly » dans le manifeste, celui du pack
 * (packs de ressources, touches, réglages) n'était alors jamais posé. Constaté par la répétition de la
 * mise à jour pokényle 1.8.1 (09/10/2026).
 *
 * <p>Isolation : {@code user.home} redirigé vers un dossier temporaire (AppPaths le lit à chaque appel).
 */
class KeybindDefaultsTest {

    private String realHome;
    private Path tempHome;

    @BeforeEach
    void setUp() throws Exception {
        realHome = System.getProperty("user.home");
        tempHome = Files.createTempDirectory("nyle-keybinds-test");
        System.setProperty("user.home", tempHome.toString());
    }

    @AfterEach
    void tearDown() {
        System.setProperty("user.home", realHome);
    }

    private static Path options() { return AppPaths.gameDir().resolve("options.txt"); }

    private static Path marqueur() { return AppPaths.launcherState().resolve("kbfix-cnb-zoom-v1.done"); }

    @Test
    void installationNeuve_neCreeNiOptionsNiMarqueur() {
        assertFalse(Files.exists(options()));
        KeybindDefaults.applyOnce();
        assertFalse(Files.exists(options()), "options.txt créé avant le sync : celui du pack ne serait jamais posé");
        assertFalse(Files.exists(marqueur()), "marqueur posé sans migration : elle ne se ferait plus jamais");
    }

    @Test
    void optionsPresent_toucheDesassigneeUneSeuleFois() throws Exception {
        Files.writeString(options(), "lang:fr_fr\nkey_mod.chiselsandbits.keys.key.zoom:key.keyboard.z\n", StandardCharsets.UTF_8);
        KeybindDefaults.applyOnce();
        String apres = Files.readString(options(), StandardCharsets.UTF_8);
        assertTrue(apres.contains("lang:fr_fr"), "le reste d'options.txt est conservé");
        assertTrue(apres.contains("key_mod.chiselsandbits.keys.key.zoom:key.keyboard.unknown"), apres);
        assertFalse(apres.contains("key.keyboard.z"), "la ligne est REMPLACÉE, pas doublée : " + apres);
        assertTrue(Files.exists(marqueur()));

        // Le joueur remet sa touche : plus jamais retouchée.
        Files.writeString(options(), "key_mod.chiselsandbits.keys.key.zoom:key.keyboard.x\n", StandardCharsets.UTF_8);
        KeybindDefaults.applyOnce();
        assertEquals("key_mod.chiselsandbits.keys.key.zoom:key.keyboard.x\n", Files.readString(options(), StandardCharsets.UTF_8));
    }
}
