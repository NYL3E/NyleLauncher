package fr.nylerp.launcher.ui.kit;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Le choix d'univers (Pokényle / NyleRP…) dans la langue des onglets du site ({@code .bq-onglet} : 1,55 rem de haut,
 * blanc à 60 % au liseré blanc, l'onglet choisi en ardoise avec le texte bleu ciel #a5e1ff, survol : blanc à 85 % et
 * soulèvement de 0,05 rem). Ici les onglets sont réunis dans une même gélule et la pastille ardoise GLISSE d'un choix à
 * l'autre ({@code --sortie}, 0,3 s) : on voit d'où l'on part et où l'on arrive.
 * Chaque option peut porter une petite image (logo de l'univers) à gauche du texte. Clavier : flèches gauche / droite.
 */
public class Segment extends Pane implements Etat.Apercu {

    private final IntegerProperty choix = new SimpleIntegerProperty(this, "choix", 0);
    private final List<Text> textes = new ArrayList<>();
    private final List<Node> images = new ArrayList<>();
    private final List<Rectangle> survols = new ArrayList<>();
    private final double[] x, w;
    private final double h, pad, hauteurOption;
    private final Rectangle fond = new Rectangle();
    private final Rectangle pastille = new Rectangle();
    private final Rectangle anneauFocus = new Rectangle();
    private Timeline glisse;

    public Segment(String... options) { this(options, null); }

    /** @param logos une image par option (ou null), affichée à gauche du libellé, 1 rem de haut. */
    public Segment(String[] options, Node[] logos) {
        Polices.charger();
        int n = options.length;
        x = new double[n];
        w = new double[n];
        pad = Jetons.rem(0.18);
        hauteurOption = Jetons.rem(1.55);
        h = hauteurOption + 2 * pad;
        setFocusTraversable(true);
        getStyleClass().add("kit-segment");

        double cur = pad, gx = Jetons.rem(1.1), ecartLogo = Jetons.rem(0.4);
        for (int i = 0; i < n; i++) {
            Text t = new Text(options[i]);
            t.setFont(Polices.gras(Jetons.rem(0.72)));
            t.setFill(Color.web("#505064"));
            t.setTextOrigin(VPos.CENTER);
            t.setMouseTransparent(true);
            Node logo = logos != null && i < logos.length ? logos[i] : null;
            double lLogo = logo == null ? 0 : logo.getLayoutBounds().getWidth() + ecartLogo;
            w[i] = gx + lLogo + t.getLayoutBounds().getWidth() + gx;
            x[i] = cur;
            cur += w[i];
            textes.add(t);
            images.add(logo);
        }
        double l = cur + pad;
        setPrefSize(l, h);
        setMinSize(l, h);
        setMaxSize(l, h);

        fond.setWidth(l);
        fond.setHeight(h);
        fond.setArcWidth(h);
        fond.setArcHeight(h);
        fond.setFill(Color.rgb(255, 255, 255, 0.6));
        fond.setStroke(Color.rgb(255, 255, 255, 0.4));
        fond.setStrokeWidth(Jetons.rem(0.05));
        double r = Calques.flou(Jetons.rem(0.19));
        fond.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0.1), r, Math.min(1, Jetons.rem(0.09) / r), 0, 0));
        getChildren().add(fond);

        for (int i = 0; i < n; i++) {
            Rectangle s = new Rectangle(x[i], pad, w[i], hauteurOption);
            s.setArcWidth(hauteurOption);
            s.setArcHeight(hauteurOption);
            s.setFill(Color.rgb(255, 255, 255, 0.85));
            s.setOpacity(0);
            survols.add(s);
            getChildren().add(s);
        }

        pastille.setHeight(hauteurOption);
        pastille.setY(pad);
        pastille.setArcWidth(hauteurOption);
        pastille.setArcHeight(hauteurOption);
        pastille.setFill(Jetons.ARDOISE);
        pastille.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(30, 30, 50, 0.25), Calques.flou(Jetons.rem(0.25)), 0, 0, Jetons.rem(0.06)));
        getChildren().add(pastille);

        for (int i = 0; i < n; i++) {
            Node logo = images.get(i);
            Text t = textes.get(i);
            double cx = x[i] + gx;
            if (logo != null) {
                logo.setLayoutX(cx - logo.getLayoutBounds().getMinX());
                logo.setLayoutY(h / 2 - logo.getLayoutBounds().getHeight() / 2 - logo.getLayoutBounds().getMinY());
                logo.setMouseTransparent(true);
                getChildren().add(logo);
                cx += logo.getLayoutBounds().getWidth() + ecartLogo;
            }
            t.setX(cx);
            t.setY(h / 2);
            getChildren().add(t);

            Rectangle zone = new Rectangle(x[i], pad, w[i], hauteurOption);
            zone.setFill(Color.TRANSPARENT);
            zone.setCursor(Curseurs.main());
            final int k = i;
            zone.setOnMouseClicked(e -> { if (!isDisabled()) choix.set(k); });
            zone.hoverProperty().addListener((o, a, b) -> survolerOption(k, b));
            getChildren().add(zone);
        }

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
        getChildren().add(anneauFocus);

        poser(0);
        choix.addListener((obs, a, b) -> {
            boolean affiche = getScene() != null && getScene().getWindow() != null && getScene().getWindow().isShowing();
            if (affiche) animer(a.intValue(), b.intValue()); else poser(b.intValue());
        });
        focusVisibleProperty().addListener((obs, a, b) -> anneauFocus.setVisible(b));
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.RIGHT && choix.get() < textes.size() - 1) { choix.set(choix.get() + 1); e.consume(); }
            if (e.getCode() == KeyCode.LEFT && choix.get() > 0) { choix.set(choix.get() - 1); e.consume(); }
        });
        disabledProperty().addListener((obs, a, b) -> setOpacity(b ? 0.55 : 1));
    }

    private void poser(int i) {
        pastille.setX(x[i]);
        pastille.setWidth(w[i]);
        for (int k = 0; k < textes.size(); k++) textes.get(k).setFill(k == i ? Jetons.ONGLET_CHOISI : Color.web("#505064"));
    }

    private void animer(int de, int vers) {
        if (glisse != null) glisse.stop();
        glisse = new Timeline(new KeyFrame(Duration.millis(300),
                new KeyValue(pastille.xProperty(), x[vers], Jetons.SORTIE),
                new KeyValue(pastille.widthProperty(), w[vers], Jetons.SORTIE),
                new KeyValue(textes.get(vers).fillProperty(), Jetons.ONGLET_CHOISI, Interpolator.EASE_BOTH),
                new KeyValue(textes.get(de).fillProperty(), Color.web("#505064"), Interpolator.EASE_BOTH)));
        glisse.play();
        survols.get(vers).setOpacity(0);
    }

    private void survolerOption(int k, boolean dessus) {
        boolean visible = dessus && k != choix.get() && !isDisabled();
        Rectangle s = survols.get(k);
        new Timeline(new KeyFrame(Duration.millis(160),
                new KeyValue(s.opacityProperty(), visible ? 1 : 0),
                new KeyValue(textes.get(k).translateYProperty(), visible ? -Jetons.rem(0.05) : 0))).play();
    }

    public IntegerProperty choixProperty() { return choix; }
    public int getChoix() { return choix.get(); }
    public void setChoix(int i) { choix.set(i); }

    /** Vitrine : SURVOL montre le survol de la première option non choisie. */
    @Override
    public void apercu(Etat etat) {
        setDisable(etat == Etat.DESACTIVE);
        for (int k = 0; k < survols.size(); k++) {
            boolean v = etat == Etat.SURVOL && k == (choix.get() == 0 ? 1 : 0);
            survols.get(k).setOpacity(v ? 1 : 0);
            textes.get(k).setTranslateY(v ? -Jetons.rem(0.05) : 0);
        }
    }

    /** Un logo d'option : n'importe quel nœud, mis à 1 rem de haut. */
    public static Node logo(Node n) {
        double hh = n.getLayoutBounds().getHeight();
        if (hh > 0) { n.setScaleX(Jetons.rem(1) / hh); n.setScaleY(Jetons.rem(1) / hh); }
        return new Group(n);
    }
}
