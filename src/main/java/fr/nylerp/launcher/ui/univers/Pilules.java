package fr.nylerp.launcher.ui.univers;

import fr.nylerp.launcher.ui.kit.Curseurs;
import fr.nylerp.launcher.ui.kit.Jetons;
import fr.nylerp.launcher.ui.kit.Polices;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Text;
import javafx.util.Duration;

/** Petite pilule claire du kit (même gabarit que le bouton de compte du site) : icône au trait + libellé. */
public final class Pilules {

    public static final String SVG_CLOCHE = "M12 4.2a5 5 0 0 0-5 5v3.1L5.4 15h13.2L17 12.3V9.2a5 5 0 0 0-5-5ZM10 18.2a2 2 0 0 0 4 0";

    private Pilules() { }

    public static HBox claire(String libelle, String svg) {
        SVGPath icone = new SVGPath();
        icone.setContent(svg);
        icone.setFill(null);
        icone.setStroke(Color.web("#3d5a8a"));
        icone.setStrokeWidth(2.1);
        icone.setStrokeLineCap(StrokeLineCap.ROUND);
        Group g = new Group(new Rectangle(24, 24, Color.TRANSPARENT), icone);
        double ti = Jetons.rem(0.95);
        g.setScaleX(ti / 24);
        g.setScaleY(ti / 24);
        StackPane cadre = new StackPane(g);
        cadre.setPrefSize(ti, ti);
        cadre.setMaxSize(ti, ti);
        Text t = new Text(libelle);
        t.setFont(Polices.extra(Jetons.rem(0.6)));
        t.setFill(Color.web("#3d5a8a"));
        HBox h = new HBox(Jetons.rem(0.3), cadre, t);
        h.setAlignment(Pos.CENTER_LEFT);
        double hh = Jetons.rem(1.95);
        h.setPrefHeight(hh);
        h.setMinHeight(hh);
        h.setMaxHeight(hh);
        h.setMaxWidth(Region.USE_PREF_SIZE);
        h.setPadding(new Insets(0, Jetons.rem(0.75), 0, Jetons.rem(0.6)));
        h.setCursor(Curseurs.main());
        String repos = "-fx-background-color: rgba(255,255,255,0.7); -fx-background-radius: " + Jetons.rem(0.55)
                + "; -fx-border-color: rgba(255,255,255,0.95); -fx-border-radius: " + Jetons.rem(0.55) + "; -fx-border-width: 1.2;";
        h.setStyle(repos);
        h.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(40, 80, 150, 0.15), Jetons.rem(0.45), 0, 0, Jetons.rem(0.08)));
        h.hoverProperty().addListener((o, a, v) -> {
            h.setStyle(v ? repos.replace("0.7)", "1.0)") : repos);
            new Timeline(new KeyFrame(Duration.millis(250),
                    new KeyValue(h.translateYProperty(), v ? -Jetons.rem(0.08) : 0, Jetons.REBOND))).play();
            icone.setStroke(v ? Jetons.ACTIF : Color.web("#3d5a8a"));
        });
        return h;
    }
}
