package fr.nylerp.launcher.ui.kit;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.transform.Shear;
import javafx.util.Duration;

/**
 * La tuile des plateformes du site ({@code .plateforme} : Windows, macOS, Discord, Aide…) : rectangle blanc légèrement
 * PENCHÉ (skewX −6°), rayon 0,3 rem, blanc 94 % → bleuté 86 %, liseré intérieur blanc, ombre courte et petit biseau
 * bleuté en bas ; texte Nunito 800 0,62 rem #262a33 avec son icône. Survol : la tuile se soulève (−0,1 rem, rebond
 * 0,25 s), s'éclaircit, l'icône monte et grossit (×1,14).
 */
public class Tuile extends StackPane implements Etat.Apercu {

    private final Rectangle forme = new Rectangle();
    private final Node icone;
    private final ColorAdjust lumiere = new ColorAdjust();
    private EventHandler<ActionEvent> surAction;
    private Timeline anim;

    /** @param icone un nœud de 0,95 rem environ (ou null) */
    public Tuile(String libelle, Node icone, double largeur, double hauteur) {
        Polices.charger();
        this.icone = icone;
        setPrefSize(largeur, hauteur);
        setMinSize(largeur, hauteur);
        setMaxSize(largeur, hauteur);
        setFocusTraversable(true);
        setCursor(Curseurs.main());
        getStyleClass().add("kit-tuile");

        forme.setWidth(largeur);
        forme.setHeight(hauteur);
        forme.setArcWidth(2 * Jetons.rem(0.3));
        forme.setArcHeight(2 * Jetons.rem(0.3));
        forme.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 255, 255, 0.94)), new Stop(1, Color.rgb(240, 246, 255, 0.86))));
        forme.setStroke(Color.rgb(255, 255, 255, 0.95));
        forme.setStrokeWidth(Jetons.rem(0.05));
        forme.getTransforms().add(new Shear(-Math.tan(Math.toRadians(6)), 0, largeur / 2, hauteur / 2));
        DropShadow ombre = new DropShadow(BlurType.GAUSSIAN, Color.rgb(70, 120, 180, 0.18), Calques.flou(Jetons.rem(0.2)), 0, 0, Jetons.rem(0.06));
        ombre.setInput(Calques.ombreInterne(0, -Jetons.rem(0.06), Jetons.rem(0.08), Color.rgb(170, 200, 235, 0.35)));
        forme.setEffect(ombre);
        Pane cadre = new Pane(forme);
        cadre.setMouseTransparent(true);

        Text t = new Text(libelle);
        t.setFont(Polices.extra(Jetons.rem(0.62)));
        t.setFill(Color.web("#262a33"));
        HBox ligne = new HBox(Jetons.rem(0.32));
        ligne.setAlignment(Pos.CENTER);
        if (icone != null) ligne.getChildren().add(icone);
        if (libelle != null && !libelle.isEmpty()) ligne.getChildren().add(t);
        ligne.setMouseTransparent(true);
        getChildren().addAll(cadre, ligne);

        hoverProperty().addListener((o, a, v) -> survoler(v));
        setOnMouseClicked(e -> { if (e.getButton() == MouseButton.PRIMARY && surAction != null && !isDisabled()) surAction.handle(new ActionEvent(this, this)); });
        setOnKeyPressed(e -> { if ((e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) && surAction != null) { surAction.handle(new ActionEvent(this, this)); e.consume(); } });
        disabledProperty().addListener((o, a, v) -> setOpacity(v ? 0.5 : 1));
    }

    private void survoler(boolean v) {
        if (anim != null) anim.stop();
        setEffect(lumiere);
        anim = new Timeline(new KeyFrame(Duration.millis(250),
                new KeyValue(translateYProperty(), v ? -Jetons.rem(0.1) : 0, Jetons.REBOND),
                new KeyValue(lumiere.brightnessProperty(), v ? 0.03 : 0)));
        if (icone != null) anim.getKeyFrames().add(new KeyFrame(Duration.millis(300),
                new KeyValue(icone.translateYProperty(), v ? -Jetons.rem(0.04) : 0, Jetons.REBOND),
                new KeyValue(icone.scaleXProperty(), v ? 1.14 : 1, Jetons.REBOND),
                new KeyValue(icone.scaleYProperty(), v ? 1.14 : 1, Jetons.REBOND)));
        if (!v) anim.setOnFinished(e -> setEffect(null));
        anim.play();
    }

    public void setOnAction(EventHandler<ActionEvent> h) { surAction = h; }

    /** Une icône vectorielle du site (viewBox 24) à la taille des tuiles (0,95 rem). */
    public static Node icone(String svg, Color couleur) {
        javafx.scene.shape.SVGPath p = new javafx.scene.shape.SVGPath();
        p.setContent(svg);
        p.setFill(couleur);
        Group g = new Group(new Rectangle(24, 24, Color.TRANSPARENT), p);
        double t = Jetons.rem(0.95) / 24;
        g.setScaleX(t);
        g.setScaleY(t);
        return new Group(g);
    }

    @Override
    public void apercu(Etat etat) {
        setDisable(etat == Etat.DESACTIVE);
        setTranslateY(etat == Etat.SURVOL ? -Jetons.rem(0.1) : etat == Etat.PRESSE ? Jetons.rem(0.04) : 0);
        setEffect(etat == Etat.SURVOL ? lumiere : null);
        lumiere.setBrightness(etat == Etat.SURVOL ? 0.03 : 0);
        if (icone != null) {
            double s = etat == Etat.SURVOL ? 1.14 : 1;
            icone.setScaleX(s);
            icone.setScaleY(s);
            icone.setTranslateY(etat == Etat.SURVOL ? -Jetons.rem(0.04) : 0);
        }
    }
}
