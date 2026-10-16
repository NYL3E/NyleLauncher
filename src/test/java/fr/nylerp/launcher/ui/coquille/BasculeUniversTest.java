package fr.nylerp.launcher.ui.coquille;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Non-régression « zone des univers vide » (refonte 2026-10, lot L12) : lance {@link EssaiBascule} dans une JVM à part,
 * avec un dossier personnel jetable (il écrit des réglages et ouvre une vraie fenêtre : jamais dans la JVM des tests ni
 * dans les vrais réglages du joueur). Sans écran (CI Linux), le test est ignoré.
 */
class BasculeUniversTest {

    @Test
    void lAffichageDesUniversNeResteJamaisVide() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "pas d'écran : essai d'interface ignoré");
        Path home = Files.createTempDirectory("nyle-bascule-home");
        File journal = home.resolve("essai.log").toFile();
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        Process p = new ProcessBuilder(java, "-Duser.home=" + home, "-Dnyle.medias.manifest=file:///inexistant",
                "-cp", System.getProperty("java.class.path"), "fr.nylerp.launcher.ui.coquille.EssaiBascule")
                .redirectErrorStream(true).redirectOutput(journal).start();
        boolean fini = p.waitFor(240, TimeUnit.SECONDS);
        if (!fini) p.destroyForcibly();
        String sortie = Files.readString(journal.toPath());
        assertTrue(fini, "l'essai n'a pas fini en 240 s :\n" + sortie);
        assertEquals(0, p.exitValue(), "la zone des univers est restée vide ou invalide :\n" + sortie);
    }
}
