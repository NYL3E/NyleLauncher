package fr.nylerp.launcher.ui.kit;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.util.Locale;

/**
 * Bouton rond en verre clair, icône pleine au centre (viewBox 24) : le groupe discret du bas de la
 * fenêtre (son, pause, réglages). Survol doux : le verre s'éclaircit, l'icône passe au bleu actif et
 * peut tourner légèrement ({@link #setRotationSurvol}) — la roue des réglages.
 */
public class BoutonRond extends StackPane implements Etat.Apercu {

    /** Roue crantée nette : 8 dents trapézoïdales, trou central (règle pair-impair). */
    public static final String ROUE = roue(8, 12, 12, 10.2, 7.4, 3.3);
    public static final String SON = "M3.5 9.2h3.6L12.4 5v14l-5.3-4.2H3.5z"
            + "M15.3 8.6a1 1 0 0 1 1.4 0 4.8 4.8 0 0 1 0 6.8 1 1 0 0 1-1.4-1.4 2.8 2.8 0 0 0 0-4 1 1 0 0 1 0-1.4z"
            + "M17.9 6a1 1 0 0 1 1.4 0 8.5 8.5 0 0 1 0 12 1 1 0 0 1-1.4-1.4 6.5 6.5 0 0 0 0-9.2 1 1 0 0 1 0-1.4z";
    public static final String SON_COUPE = "M3.5 9.2h3.6L12.4 5v14l-5.3-4.2H3.5z"
            + "M15.6 9.1a1 1 0 0 1 1.4 0l1.6 1.6 1.6-1.6a1 1 0 1 1 1.4 1.4L20 12.1l1.6 1.6a1 1 0 1 1-1.4 1.4l-1.6-1.6-1.6 1.6a1 1 0 1 1-1.4-1.4l1.6-1.6-1.6-1.6a1 1 0 0 1 0-1.4z";
    public static final String PAUSE = "M7 5.5h3.2v13H7zM13.8 5.5H17v13h-3.2z";
    public static final String LECTURE = "M8 5.2v13.6L19 12z";

    private final SVGPath icone = new SVGPath();
    private final Group groupe;
    private final double cote;
    private EventHandler<ActionEvent> surAction;
    private Timeline anim;
    private double rotationSurvol;

    public BoutonRond(String dessin) {
        cote = Jetons.rem(1.9);
        setPrefSize(cote, cote);
        setMinSize(cote, cote);
        setMaxSize(cote, cote);
        setAlignment(Pos.CENTER);
        setFocusTraversable(true);
        setCursor(Curseurs.main());
        appliquerFond(false);
        setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(30, 60, 120, 0.22), 14, 0.05, 0, 3));
        icone.setContent(dessin);
        icone.setFillRule(FillRule.EVEN_ODD);
        icone.setFill(Jetons.ICONE);
        double ti = Jetons.rem(1.0);
        groupe = new Group(new Rectangle(24, 24, Color.TRANSPARENT), icone);
        groupe.setScaleX(ti / 24);
        groupe.setScaleY(ti / 24);
        groupe.setMouseTransparent(true);
        getChildren().add(groupe);
        hoverProperty().addListener((o, a, b) -> { if (!isDisabled()) survoler(b); });
        pressedProperty().addListener((o, a, b) -> { setScaleX(b ? 0.93 : 1); setScaleY(b ? 0.93 : 1); });
        setOnMouseClicked(e -> { if (e.getButton() == MouseButton.PRIMARY && !isDisabled()) declencher(); });
        setOnKeyPressed(e -> {
            if ((e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) && !isDisabled()) { declencher(); e.consume(); }
        });
        disabledProperty().addListener((o, a, b) -> setOpacity(b ? 0.45 : 1));
    }

    private void declencher() { if (surAction != null) surAction.handle(new ActionEvent(this, this)); }

    public void setOnAction(EventHandler<ActionEvent> h) { surAction = h; }

    /** Change l'icône (son ↔ son coupé, pause ↔ lecture). */
    public void setIcone(String dessin) { icone.setContent(dessin); }

    /** Angle de rotation de l'icône au survol (0 : aucune). */
    public void setRotationSurvol(double degres) { rotationSurvol = degres; }

    private void appliquerFond(boolean dessus) {
        String a = dessus ? "0.96" : "0.74";
        setStyle("-fx-background-color: rgba(255,255,255," + a + "); -fx-background-radius: 999;"
                + " -fx-border-color: rgba(255,255,255,0.98); -fx-border-radius: 999; -fx-border-width: 1.2;");
    }

    private void survoler(boolean dessus) {
        appliquerFond(dessus);
        if (anim != null) anim.stop();
        anim = new Timeline(new KeyFrame(Duration.millis(260),
                new KeyValue(icone.fillProperty(), dessus ? Jetons.ACTIF : Jetons.ICONE, Interpolator.EASE_OUT),
                new KeyValue(groupe.rotateProperty(), dessus ? rotationSurvol : 0, Interpolator.EASE_BOTH)));
        anim.play();
    }

    @Override
    public void apercu(Etat etat) {
        setDisable(etat == Etat.DESACTIVE);
        boolean dessus = etat == Etat.SURVOL || etat == Etat.PRESSE;
        appliquerFond(dessus);
        icone.setFill(dessus ? Jetons.ACTIF : Jetons.ICONE);
        groupe.setRotate(dessus ? rotationSurvol : 0);
    }

    /** Tracé d'une roue crantée : {@code n} dents trapézoïdales entre {@code rDent} et {@code rCorps}, trou de rayon {@code rTrou}. */
    static String roue(int n, double cx, double cy, double rDent, double rCorps, double rTrou) {
        StringBuilder s = new StringBuilder();
        double pas = 2 * Math.PI / n;
        double demiPied = pas * 0.27, demiTete = pas * 0.17;
        for (int i = 0; i < n; i++) {
            double a = i * pas - Math.PI / 2;
            point(s, i == 0 ? "M" : "L", cx, cy, rCorps, a - demiPied);
            point(s, "L", cx, cy, rDent, a - demiTete);
            point(s, "L", cx, cy, rDent, a + demiTete);
            point(s, "L", cx, cy, rCorps, a + demiPied);
            // arc du corps jusqu'à la dent suivante
            double b = (i + 1) * pas - Math.PI / 2 - demiPied;
            s.append(String.format(Locale.ROOT, "A%.3f %.3f 0 0 1 %.3f %.3f",
                    rCorps, rCorps, cx + rCorps * Math.cos(b), cy + rCorps * Math.sin(b)));
        }
        s.append("Z");
        s.append(String.format(Locale.ROOT, "M%.3f %.3fa%.3f %.3f 0 1 0 %.3f 0a%.3f %.3f 0 1 0 %.3f 0Z",
                cx - rTrou, cy, rTrou, rTrou, 2 * rTrou, rTrou, rTrou, -2 * rTrou));
        return s.toString();
    }

    private static void point(StringBuilder s, String cmd, double cx, double cy, double r, double a) {
        s.append(String.format(Locale.ROOT, "%s%.3f %.3f", cmd, cx + r * Math.cos(a), cy + r * Math.sin(a)));
    }
}
