package fr.nylerp.launcher.ui.kit;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import javafx.util.Duration;

/**
 * Le compte dans l'en-tête du site ({@code .compte-entete}) : le buste du skin dans un carré arrondi bleu pâle, puis le
 * pseudo (ou « Se connecter »). Blanc à 70 %, liseré intérieur blanc, ombre courte ; survol : soulèvement de 0,08 rem
 * (rebond) et fond blanc plein.
 */
public class BoutonCompte extends HBox implements Etat.Apercu {

    private static final String SILHOUETTE = "M12 12.2a4.1 4.1 0 1 0 0-8.2 4.1 4.1 0 0 0 0 8.2Zm-7.4 7.6c.6-3.4 3.6-5.6 7.4-5.6s6.8 2.2 7.4 5.6c.1.6-.4 1.2-1 1.2H5.6c-.6 0-1.1-.6-1-1.2Z";

    private final StackPane buste = new StackPane();
    private final Text nom = new Text();
    private final CornerRadii rayon;
    private EventHandler<ActionEvent> surAction;
    private Timeline anim;

    public BoutonCompte(String pseudo) {
        Polices.charger();
        rayon = new CornerRadii(Jetons.rem(0.55));
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(Jetons.rem(0.4));
        setPadding(new Insets(0, Jetons.rem(0.7), 0, Jetons.rem(0.2)));
        double h = Jetons.rem(1.95);
        setPrefHeight(h);
        setMinHeight(h);
        setMaxHeight(h);
        setMaxWidth(USE_PREF_SIZE);
        setFocusTraversable(true);
        setCursor(Curseurs.main());
        getStyleClass().add("kit-bouton-compte");
        peindre(false);
        setBorder(new Border(new BorderStroke(Color.rgb(255, 255, 255, 0.95), BorderStrokeStyle.SOLID, rayon, new BorderWidths(Jetons.rem(0.06)))));
        setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(40, 80, 150, 0.15), Calques.flou(Jetons.rem(0.3)), 0, 0, Jetons.rem(0.08)));

        double b = Jetons.rem(1.55);
        buste.setPrefSize(b, b);
        buste.setMinSize(b, b);
        buste.setMaxSize(b, b);
        CornerRadii rb = new CornerRadii(Jetons.rem(0.38));
        buste.setBackground(new Background(new BackgroundFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#cfe6ff")), new Stop(1, Color.web("#9cc6f2"))), rb, Insets.EMPTY)));
        buste.setBorder(new Border(new BorderStroke(Color.WHITE, BorderStrokeStyle.SOLID, rb, new BorderWidths(Jetons.rem(0.06)))));
        Rectangle clip = new Rectangle(b, b);
        clip.setArcWidth(2 * Jetons.rem(0.38));
        clip.setArcHeight(2 * Jetons.rem(0.38));
        buste.setClip(clip);
        setImage(null);

        nom.setFont(Polices.extra(Jetons.rem(0.6)));
        nom.setFill(Color.web("#3d5a8a"));
        setPseudo(pseudo);
        getChildren().addAll(buste, nom);

        hoverProperty().addListener((o, a, v) -> survoler(v));
        setOnMouseClicked(e -> { if (e.getButton() == MouseButton.PRIMARY && surAction != null) surAction.handle(new ActionEvent(this, this)); });
        setOnKeyPressed(e -> { if ((e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) && surAction != null) { surAction.handle(new ActionEvent(this, this)); e.consume(); } });
    }

    private void peindre(boolean survol) {
        setBackground(new Background(new BackgroundFill(survol ? Color.WHITE : Color.rgb(255, 255, 255, 0.7), rayon, Insets.EMPTY)));
    }

    private void survoler(boolean v) {
        if (anim != null) anim.stop();
        anim = new Timeline(new KeyFrame(Duration.millis(250), new KeyValue(translateYProperty(), v ? -Jetons.rem(0.08) : 0, Jetons.REBOND)));
        anim.play();
        peindre(v);
    }

    /** Le buste du skin (pixelisé net), ou null pour la silhouette par défaut. */
    public void setImage(Image tete) {
        buste.getChildren().clear();
        double b = Jetons.rem(1.55);
        if (tete != null) {
            ImageView v = new ImageView(tete);
            v.setSmooth(false);
            v.setPreserveRatio(true);
            v.setFitWidth(b * 0.86);
            StackPane.setAlignment(v, Pos.BOTTOM_CENTER);
            buste.getChildren().add(v);
        } else {
            SVGPath s = new SVGPath();
            s.setContent(SILHOUETTE);
            s.setFill(Color.web("#3d5a8a"));
            Group g = new Group(new Rectangle(24, 24, Color.TRANSPARENT), s);
            g.setScaleX(b * 0.72 / 24);
            g.setScaleY(b * 0.72 / 24);
            buste.getChildren().add(g);
        }
    }

    public void setPseudo(String pseudo) { nom.setText(pseudo == null || pseudo.isBlank() ? "Se connecter" : pseudo); }

    public void setOnAction(EventHandler<ActionEvent> h) { surAction = h; }

    @Override
    public void apercu(Etat etat) {
        setDisable(etat == Etat.DESACTIVE);
        setOpacity(etat == Etat.DESACTIVE ? 0.55 : 1);
        setTranslateY(etat == Etat.SURVOL ? -Jetons.rem(0.08) : 0);
        peindre(etat == Etat.SURVOL);
    }
}
