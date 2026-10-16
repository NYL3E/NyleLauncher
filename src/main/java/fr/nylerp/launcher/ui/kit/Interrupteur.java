package fr.nylerp.launcher.ui.kit;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Interrupteur (réglages « Lancer au démarrage », « Notifications »…) dans la langue du site : rail en verre blanc
 * (le blanc à 60 % des onglets) quand il est coupé, rail NACRÉ (--nacre) au liseré irisé quand il est actif, pastille
 * blanche avec la même ombre douce que les onglets. Bascule : glissement {@code --sortie} 0,3 s, la pastille s'étire
 * un instant (rebond du site).
 */
public class Interrupteur extends Pane implements Etat.Apercu {

    private final BooleanProperty actif = new SimpleBooleanProperty(this, "actif", false);
    private final double l, h, d, marge;
    private final Rectangle rail = new Rectangle();
    private final Rectangle nacre = new Rectangle();
    private final Circle pastille = new Circle();
    private final Rectangle anneauFocus = new Rectangle();
    private Timeline bascule;

    public Interrupteur() { this(false); }

    public Interrupteur(boolean valeur) {
        l = Jetons.rem(2.1);
        h = Jetons.rem(1.2);
        marge = Jetons.rem(0.14);
        d = h - 2 * marge;
        setPrefSize(l, h);
        setMinSize(l, h);
        setMaxSize(l, h);
        setFocusTraversable(true);
        setCursor(Curseurs.main());
        getStyleClass().add("kit-interrupteur");

        rail.setWidth(l);
        rail.setHeight(h);
        rail.setArcWidth(h);
        rail.setArcHeight(h);
        rail.setFill(Color.rgb(255, 255, 255, 0.6));
        rail.setStroke(Color.rgb(255, 255, 255, 0.4));
        rail.setStrokeWidth(Jetons.rem(0.05));
        rail.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0.1), Calques.flou(Jetons.rem(0.19)), Jetons.rem(0.09) / Calques.flou(Jetons.rem(0.19)), 0, 0));

        nacre.setWidth(l);
        nacre.setHeight(h);
        nacre.setArcWidth(h);
        nacre.setArcHeight(h);
        nacre.setFill(Jetons.nacre());
        nacre.setStroke(Jetons.lisereIrise());
        nacre.setStrokeWidth(Jetons.rem(0.08));
        nacre.setEffect(Calques.ombreInterne(0, Jetons.rem(0.06), Jetons.rem(0.12), Color.rgb(60, 120, 200, 0.25)));

        pastille.setRadius(d / 2);
        pastille.setCenterY(h / 2);
        pastille.setFill(Color.WHITE);
        DropShadow ombre = new DropShadow(BlurType.GAUSSIAN, Color.rgb(40, 80, 150, 0.28), Calques.flou(Jetons.rem(0.25)), 0, 0, Jetons.rem(0.06));
        ombre.setInput(Calques.ombreInterne(0, -Jetons.rem(0.05), Jetons.rem(0.1), Color.rgb(170, 205, 245, 0.45)));
        pastille.setEffect(ombre);

        double o = Jetons.rem(0.1);
        anneauFocus.setX(-o * 1.5);
        anneauFocus.setY(-o * 1.5);
        anneauFocus.setWidth(l + 3 * o);
        anneauFocus.setHeight(h + 3 * o);
        anneauFocus.setArcWidth(h + 3 * o);
        anneauFocus.setArcHeight(h + 3 * o);
        anneauFocus.setFill(null);
        anneauFocus.setStroke(Jetons.FOCUS);
        anneauFocus.setStrokeWidth(o);
        anneauFocus.setVisible(false);
        anneauFocus.setMouseTransparent(true);

        getChildren().addAll(rail, nacre, pastille, anneauFocus);
        actif.set(valeur);
        poser(valeur);
        actif.addListener((o2, a, b) -> animer(b));
        setOnMouseClicked(e -> { if (e.getButton() == MouseButton.PRIMARY && !isDisabled()) actif.set(!actif.get()); });
        setOnKeyPressed(e -> { if ((e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER) && !isDisabled()) { actif.set(!actif.get()); e.consume(); } });
        focusVisibleProperty().addListener((o2, a, b) -> anneauFocus.setVisible(b));
        hoverProperty().addListener((o2, a, b) -> { if (!isDisabled()) rail.setFill(Color.rgb(255, 255, 255, b ? 0.85 : 0.6)); });
        disabledProperty().addListener((o2, a, b) -> { setOpacity(b ? 0.5 : 1); setCursor(b ? Curseurs.fleche() : Curseurs.main()); });
    }

    private double xPour(boolean v) { return v ? l - marge - d / 2 : marge + d / 2; }

    private void poser(boolean v) {
        pastille.setCenterX(xPour(v));
        nacre.setOpacity(v ? 1 : 0);
    }

    private void animer(boolean v) {
        if (bascule != null) bascule.stop();
        bascule = new Timeline(
                new KeyFrame(Duration.millis(120), new KeyValue(pastille.scaleXProperty(), 1.18, Interpolator.EASE_OUT)),
                new KeyFrame(Duration.millis(300), new KeyValue(pastille.centerXProperty(), xPour(v), Jetons.SORTIE),
                        new KeyValue(nacre.opacityProperty(), v ? 1 : 0, Interpolator.EASE_BOTH),
                        new KeyValue(pastille.scaleXProperty(), 1, Jetons.REBOND)));
        bascule.play();
    }

    public BooleanProperty actifProperty() { return actif; }
    public boolean isActif() { return actif.get(); }
    public void setActif(boolean v) { actif.set(v); }

    @Override
    public void apercu(Etat etat) {
        setDisable(etat == Etat.DESACTIVE);
        rail.setFill(Color.rgb(255, 255, 255, etat == Etat.SURVOL ? 0.85 : 0.6));
        pastille.setScaleX(etat == Etat.PRESSE ? 1.18 : 1);
    }
}
