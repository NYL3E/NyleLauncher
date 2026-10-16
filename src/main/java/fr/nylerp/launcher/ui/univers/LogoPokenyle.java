package fr.nylerp.launcher.ui.univers;

import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.ui.kit.Jetons;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.AccessibleRole;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.util.Duration;

/**
 * Le gros logo officiel de Pokényle (POKÉ orange et jaune, NYLE argent, contour violet) : il remplace le titre
 * en texte de l'écran de l'univers. Deux PNG détourés sont embarqués dans le jar (jamais de réseau : le logo est
 * là hors ligne et au premier démarrage) ; on prend celui qui correspond à la taille réelle en pixels de
 * l'écran, chargé directement à cette largeur (filtre de qualité de {@link Image}, pas de réduction brutale).
 */
final class LogoPokenyle extends ImageView {

    private static final String LOGO_1X = "/images/univers/logo-pokenyle-titre-560.png";
    private static final String LOGO_2X = "/images/univers/logo-pokenyle-titre-1120.png";
    /** Largeur d'affichage en rem du site : 20 rem = environ 410 px dans la fenêtre de 1180 px. */
    private static final double LARGEUR_REM = 20;

    LogoPokenyle() {
        double largeur = Jetons.rem(LARGEUR_REM);
        double sortie = 1;
        try { sortie = Screen.getPrimary().getOutputScaleX(); } catch (Throwable ignored) { }
        double pixels = largeur * Math.max(1, sortie);
        // le 1x (560 px) suffit tant qu'il n'est pas agrandi ; au-delà, le 2x (1120 px)
        String source = pixels <= 560 ? LOGO_1X : LOGO_2X;
        Image image = new Image(LogoPokenyle.class.getResourceAsStream(source), Math.ceil(pixels), 0, true, true);
        setImage(image);
        setPreserveRatio(true);
        setSmooth(true);
        setFitWidth(largeur);
        setMouseTransparent(true);
        // ombre douce et sombre sous le logo, pour le détacher du ciel (ni halo blanc ni contour)
        setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(8, 30, 80, 0.42), Jetons.rem(1.1), 0, 0, Jetons.rem(0.35)));
        setAccessibleRole(AccessibleRole.IMAGE_VIEW);
        setAccessibleText("Pokényle");
    }

    /** Entrée en douceur : fondu et légère montée en 250 ms ; sans animation si les animations sont réduites. */
    void entrer() {
        if (Settings.get().animationsReduites) {
            setOpacity(1);
            setTranslateY(0);
            return;
        }
        setOpacity(0);
        setTranslateY(Jetons.rem(0.7));
        FadeTransition fondu = new FadeTransition(Duration.millis(250), this);
        fondu.setToValue(1);
        fondu.setInterpolator(Interpolator.EASE_OUT);
        TranslateTransition montee = new TranslateTransition(Duration.millis(250), this);
        montee.setToY(0);
        montee.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(fondu, montee).play();
    }
}
