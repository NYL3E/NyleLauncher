package fr.nylerp.launcher.ui.compte;

import fr.nylerp.launcher.update.CheminsMedias;
import fr.nylerp.launcher.update.Medias;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.util.Duration;

/**
 * Le ciel du site (prairie aux trois coffres, sans oiseaux) derrière les écrans de compte et
 * d'accueil, recadré comme un {@code background-size: cover}. Ordre de source :
 * <ol>
 *   <li>le fond 2x du paquet de médias, s'il est déjà en cache ;</li>
 *   <li>sinon la copie compacte embarquée dans le jar ({@code /images/compte/fond-connexion.jpg},
 *       ~95 Ko) : c'est le tout premier écran d'un nouveau joueur, il ne doit jamais attendre un
 *       téléchargement ;</li>
 *   <li>sinon un dégradé de ciel. Le fond 2x remplace la copie en fondu dès qu'il arrive.</li>
 * </ol>
 * Un voile très léger en haut et en bas porte les titres blancs et le pied de page.
 */
public final class FondCiel extends Pane {

    static final String COPIE_JAR = "/images/compte/fond-connexion.jpg";

    private final ImageView vue = new ImageView();
    private final Region voile = new Region();

    public FondCiel() {
        getStyleClass().add("compte-fond");
        setBackground(new Background(new BackgroundFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#7cc0f2")), new Stop(0.62, Color.web("#cfeafc")), new Stop(1, Color.web("#9fd18a"))), null, null)));
        vue.setSmooth(true);
        vue.setPreserveRatio(false);
        voile.setBackground(new Background(new BackgroundFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(24, 70, 140, 0.22)), new Stop(0.32, Color.rgb(24, 70, 140, 0.0)),
                new Stop(0.78, Color.rgb(255, 255, 255, 0.0)), new Stop(1, Color.rgb(20, 50, 40, 0.28))), null, null)));
        voile.setMouseTransparent(true);
        getChildren().addAll(vue, voile);
        setMouseTransparent(true);

        String u = Medias.urlOuClasspath(CheminsMedias.POKENYLE_FOND_2X, COPIE_JAR);
        if (u != null) poser(new Image(u, true), false);
        if (Medias.fichier(CheminsMedias.POKENYLE_FOND_2X) == null) {
            Medias.quandDisponible(CheminsMedias.POKENYLE_FOND_2X, p -> Platform.runLater(() ->
                    poser(new Image(p.toUri().toString(), true), true)));
        }
    }

    private void poser(Image img, boolean fondu) {
        Runnable appliquer = () -> {
            if (img.isError()) return;
            vue.setImage(img);
            requestLayout();
            if (fondu) {
                vue.setOpacity(0);
                FadeTransition f = new FadeTransition(Duration.millis(600), vue);
                f.setToValue(1);
                f.play();
            }
        };
        if (img.getProgress() >= 1) appliquer.run();
        else img.progressProperty().addListener((o, a, b) -> { if (b.doubleValue() >= 1) appliquer.run(); });
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth(), h = getHeight();
        voile.resizeRelocate(0, 0, w, h);
        Image img = vue.getImage();
        vue.setFitWidth(w);
        vue.setFitHeight(h);
        vue.relocate(0, 0);
        if (img == null || img.getWidth() <= 0 || w <= 0 || h <= 0) return;
        // cover : on garde le rapport de l'image et on rogne ce qui dépasse, centré horizontalement,
        // calé un peu vers le bas (les coffres et l'herbe portent la scène).
        double iw = img.getWidth(), ih = img.getHeight();
        double k = Math.max(w / iw, h / ih);
        double vw = w / k, vh = h / k;
        double x = (iw - vw) / 2, y = (ih - vh) * 0.62;
        vue.setViewport(new Rectangle2D(x, y, vw, vh));
    }
}
