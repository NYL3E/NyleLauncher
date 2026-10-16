package fr.nylerp.launcher.ui.kit;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.util.Duration;

/**
 * Les petits boutons d'icône de la barre du site ({@code .barre-icone} : 1,5617 rem, icône de 1,0938 rem en #5879b3,
 * bleu actif #10a7e6 au survol en 0,2 s). Sert aux commandes de la fenêtre (réduire, fermer), au son, aux réglages.
 * Icônes en traits arrondis (viewBox 24), comme celles du site.
 */
public class BoutonIcone extends Pane implements Etat.Apercu {

    public static final String REDUIRE = "M6 12.5h12";
    public static final String FERMER = "M7 7l10 10M17 7 7 17";
    public static final String REGLAGES = "M12 8.6a3.4 3.4 0 1 0 0 6.8 3.4 3.4 0 0 0 0-6.8ZM12 2.8v2.4M12 18.8v2.4M2.8 12h2.4M18.8 12h2.4M5.5 5.5l1.7 1.7M16.8 16.8l1.7 1.7M5.5 18.5l1.7-1.7M16.8 7.2l1.7-1.7";
    public static final String SON = "M4 9.5h3.2L12 5.6v12.8l-4.8-3.9H4zM15.6 9.2a4 4 0 0 1 0 5.6M18.2 6.8a7.4 7.4 0 0 1 0 10.4";

    private final SVGPath trait = new SVGPath();
    private final Circle fondSurvol = new Circle();
    private final Rectangle anneauFocus = new Rectangle();
    private EventHandler<ActionEvent> surAction;
    private Timeline anim;

    public BoutonIcone(String dessin) {
        double c = Jetons.rem(1.5617), ti = Jetons.rem(1.0938);
        setPrefSize(c, c);
        setMinSize(c, c);
        setMaxSize(c, c);
        setFocusTraversable(true);
        setCursor(Curseurs.main());
        getStyleClass().add("kit-bouton-icone");
        fondSurvol.setCenterX(c / 2);
        fondSurvol.setCenterY(c / 2);
        fondSurvol.setRadius(c / 2);
        fondSurvol.setFill(Color.rgb(255, 255, 255, 0.55));
        fondSurvol.setOpacity(0);
        trait.setContent(dessin);
        trait.setFill(null);
        trait.setStroke(Jetons.ICONE);
        trait.setStrokeWidth(2.2);
        trait.setStrokeLineCap(StrokeLineCap.ROUND);
        trait.setStrokeLineJoin(StrokeLineJoin.ROUND);
        Group g = new Group(new Rectangle(24, 24, Color.TRANSPARENT), trait);
        g.setScaleX(ti / 24);
        g.setScaleY(ti / 24);
        g.setLayoutX(c / 2 - 12);
        g.setLayoutY(c / 2 - 12);
        g.setMouseTransparent(true);
        anneauFocus.setWidth(c);
        anneauFocus.setHeight(c);
        anneauFocus.setArcWidth(Jetons.rem(0.6));
        anneauFocus.setArcHeight(Jetons.rem(0.6));
        anneauFocus.setFill(null);
        anneauFocus.setStroke(Jetons.FOCUS);
        anneauFocus.setStrokeWidth(Jetons.rem(0.1));
        anneauFocus.setVisible(false);
        anneauFocus.setMouseTransparent(true);
        getChildren().addAll(fondSurvol, g, anneauFocus);
        hoverProperty().addListener((o, a, b) -> survoler(b));
        focusVisibleProperty().addListener((o, a, b) -> anneauFocus.setVisible(b));
        setOnMouseClicked(e -> { if (e.getButton() == MouseButton.PRIMARY && surAction != null && !isDisabled()) surAction.handle(new ActionEvent(this, this)); });
        setOnKeyPressed(e -> {
            if ((e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) && surAction != null && !isDisabled()) { surAction.handle(new ActionEvent(this, this)); e.consume(); }
        });
        disabledProperty().addListener((o, a, b) -> setOpacity(b ? 0.45 : 1));
    }

    private void survoler(boolean dessus) {
        if (anim != null) anim.stop();
        anim = new Timeline(new KeyFrame(Duration.millis(200),
                new KeyValue(trait.strokeProperty(), dessus ? Jetons.ACTIF : Jetons.ICONE),
                new KeyValue(fondSurvol.opacityProperty(), dessus ? 1 : 0)));
        anim.play();
    }

    public void setOnAction(EventHandler<ActionEvent> h) { surAction = h; }

    /** Couleur de l'icône au repos (blanc sur une image, par exemple : la barre « sombre » du site). */
    public void setCouleur(Color c) { trait.setStroke(c); }

    @Override
    public void apercu(Etat etat) {
        setDisable(etat == Etat.DESACTIVE);
        trait.setStroke(etat == Etat.SURVOL || etat == Etat.PRESSE ? Jetons.ACTIF : Jetons.ICONE);
        fondSurvol.setOpacity(etat == Etat.SURVOL ? 1 : 0);
        setScaleX(etat == Etat.PRESSE ? 0.92 : 1);
        setScaleY(etat == Etat.PRESSE ? 0.92 : 1);
    }
}
