package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.config.ModeDeJeu;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L'icône de la FENÊTRE du launcher suit l'univers : la tête de Pikachu en Pokényle, le coffre de NyleRP sinon. Le test
 * vérifie ce que le joueur verrait : deux images carrées et NETTES (256 px au moins), transparentes aux angles, et
 * DIFFÉRENTES (un chemin recopié deux fois donnerait la même icône aux deux univers sans qu'aucune erreur ne se voie).
 */
class IconeFenetreTest {

    private static BufferedImage lire(String ressource) throws Exception {
        try (InputStream in = IconeFenetre.class.getResourceAsStream(ressource)) {
            assertNotNull(in, "ressource absente du jar : " + ressource);
            return ImageIO.read(in);
        }
    }

    @Test
    void chaqueUniversALaSienne() {
        assertEquals(java.util.List.of(IconeFenetre.RESSOURCE_NYLERP), IconeFenetre.ressources(ModeDeJeu.NYLERP));
        assertEquals(IconeFenetre.TAILLES_POKENYLE.length, IconeFenetre.ressources(ModeDeJeu.POKENYLE).size());
        assertNotEquals(IconeFenetre.ressources(ModeDeJeu.POKENYLE), IconeFenetre.ressources(ModeDeJeu.NYLERP));
    }

    @Test
    void lesDeuxIconesExistentEtSontNettes() throws Exception {
        BufferedImage nyle = lire(IconeFenetre.RESSOURCE_NYLERP);
        assertEquals(nyle.getWidth(), nyle.getHeight(), "NyleRP : icône non carrée");
        assertTrue(nyle.getWidth() >= 256, "NyleRP : icône trop petite (" + nyle.getWidth() + " px)");
        // Pokényle : chaque fichier est carré ET de la taille que son nom annonce (un 64 px qui en ferait 256 serait pris tel quel)
        for (int t : IconeFenetre.TAILLES_POKENYLE) {
            BufferedImage i = lire(IconeFenetre.ressourcePokenyle(t));
            assertEquals(t, i.getWidth(), "Pokényle " + t + " : largeur");
            assertEquals(t, i.getHeight(), "Pokényle " + t + " : hauteur");
        }
    }

    @Test
    void laTeteDePikachuNEstPasLeCoffre() throws Exception {
        BufferedImage p = lire(IconeFenetre.ressourcePokenyle(256)), n = lire(IconeFenetre.RESSOURCE_NYLERP);
        // la tête est dessinée sur fond transparent : les quatre angles le sont
        int c = p.getWidth() - 1;
        for (int[] xy : new int[][]{{0, 0}, {c, 0}, {0, c}, {c, c}}) {
            assertEquals(0, p.getRGB(xy[0], xy[1]) >>> 24, "l'icône Pokényle doit être transparente aux angles");
        }
        // et le centre est jaune (R et V hauts, B bas) : la tête de Pikachu, pas le coffre rouge et or sur plaque bleue
        int px = p.getRGB(p.getWidth() / 2, p.getHeight() / 2);
        int r = (px >> 16) & 255, g = (px >> 8) & 255, b = px & 255;
        assertTrue(r > 200 && g > 170 && b < 120, "centre de l'icône Pokényle : jaune attendu, vu " + r + "," + g + "," + b);
        int nx = n.getRGB(n.getWidth() / 2, n.getHeight() / 6);
        assertTrue((nx >>> 24) == 255 && (nx & 255) > 150, "l'icône NyleRP garde sa plaque bleue");
    }

    /** Le vrai comportement : une fenêtre JavaFX réelle, un dossier personnel jetable, une bascule d'univers. */
    @Test
    void laFenetreChangeDIconeAvecLUnivers() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "pas d'écran : test JavaFX ignoré");
        String vraiHome = System.getProperty("user.home");
        Path home = Files.createTempDirectory("nyle-icone-home");
        System.setProperty("user.home", home.toString());
        try {
            CountDownLatch pret = new CountDownLatch(1);
            try { Platform.startup(pret::countDown); } catch (IllegalStateException dejaDemarre) { pret.countDown(); }
            assertTrue(pret.await(15, TimeUnit.SECONDS), "JavaFX démarre");
            fr.nylerp.launcher.config.Settings.get().modeDeJeu = "nylerp";
            AtomicReference<Stage> fenetre = new AtomicReference<>();
            surFx(() -> { Stage st = new Stage(); fenetre.set(st); IconeFenetre.suivre(st); });
            assertEquals(1, fenetre.get().getIcons().size(), "NyleRP : une icône posée au démarrage");

            int avant = centre(fenetre.get().getIcons().get(0));
            assertTrue(((avant >> 16) & 255) > 150 && (avant & 255) < 60, "NyleRP : le coffre brun-orangé au centre (pas du jaune de Pikachu)");
            ModeDeJeu.definir(ModeDeJeu.POKENYLE);
            attendre(() -> fenetre.get().getIcons().size() == IconeFenetre.TAILLES_POKENYLE.length);
            assertEquals(IconeFenetre.TAILLES_POKENYLE.length, fenetre.get().getIcons().size(), "Pokényle : toutes les tailles posées");
            for (Image i : fenetre.get().getIcons()) {
                int pok = centre(i);
                assertTrue(((pok >> 16) & 255) > 190 && (pok & 255) < 140, "Pokényle " + (int) i.getWidth() + " : la tête de Pikachu jaune (vu " + Integer.toHexString(pok) + ")");
            }
            ModeDeJeu.definir(ModeDeJeu.NYLERP);
            attendre(() -> fenetre.get().getIcons().size() == 1 && centre(fenetre.get().getIcons().get(0)) == avant);
        } finally {
            ModeDeJeu.surChangement(null);
            fr.nylerp.launcher.config.Settings.get().modeDeJeu = "nylerp";
            System.setProperty("user.home", vraiHome);
        }
    }

    /** Pixel du centre de l'icône (ARGB), lu sur le fil JavaFX. */
    private static int centre(Image i) {
        return i.getPixelReader().getArgb((int) i.getWidth() / 2, (int) i.getHeight() / 2);
    }

    private static void surFx(Runnable r) throws Exception {
        CountDownLatch fin = new CountDownLatch(1);
        Platform.runLater(() -> { try { r.run(); } finally { fin.countDown(); } });
        assertTrue(fin.await(15, TimeUnit.SECONDS), "le fil JavaFX répond");
    }

    private static void attendre(java.util.function.BooleanSupplier condition) throws Exception {
        long fin = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < fin) {
            AtomicReference<Boolean> ok = new AtomicReference<>(false);
            surFx(() -> ok.set(condition.getAsBoolean()));
            if (ok.get()) return;
            Thread.sleep(50);
        }
        assertTrue(condition.getAsBoolean(), "l'icône n'a pas changé en 10 s");
    }
}
