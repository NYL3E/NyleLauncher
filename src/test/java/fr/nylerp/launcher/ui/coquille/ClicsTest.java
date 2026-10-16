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
 * Non-régression « bouton Retour mal cliquable » (refonte 2026-10, lot L15) : lance {@link EssaiClics} dans une JVM à
 * part, avec un dossier personnel jetable. Il envoie de VRAIS clics de souris dans une vraie fenêtre : ignoré sans
 * écran (CI Linux) ou si {@code -Dnyle.sansClics=true}.
 */
class ClicsTest {

    @Test
    void lesBoutonsRetourEtBoutiqueRepondentSurToutLeurSurface() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "pas d'écran : essai de clics ignoré");
        Assumptions.assumeFalse(Boolean.getBoolean("nyle.sansClics"), "essai de clics désactivé");
        Path home = Files.createTempDirectory("nyle-clics-home");
        File journal = home.resolve("essai.log").toFile();
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        Process p = new ProcessBuilder(java, "-Duser.home=" + home, "-Dnyle.medias.manifest=file:///inexistant",
                "-cp", System.getProperty("java.class.path"), "fr.nylerp.launcher.ui.coquille.EssaiClics")
                .redirectErrorStream(true).redirectOutput(journal).start();
        boolean fini = p.waitFor(240, TimeUnit.SECONDS);
        if (!fini) p.destroyForcibly();
        String sortie = Files.readString(journal.toPath());
        assertTrue(fini, "l'essai n'a pas fini en 240 s :\n" + sortie);
        assertEquals(0, p.exitValue(), "un bouton est resté mort ou la boutique s'est mal ouverte :\n" + sortie);
    }
}
