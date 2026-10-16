package fr.nylerp.launcher.ui.kit;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Text;
import javafx.scene.transform.Shear;
import javafx.util.Duration;

/**
 * Les boutons de l'en-tête du site ({@code .bouton-entete} : « Rejoindre le Discord », « Installer le launcher ») : la
 * même pilule que le bouton du héros, en plus fin. Bord irisé de 0,1 rem, intérieur givré blanc avec lueur rose à
 * gauche, halo bleu ciel, médaille d'icône à gauche, texte Nunito 900 en dégradé.
 * Survol : la pilule se soulève (−0,1 rem, rebond 0,3 s), un reflet la traverse (0,8 s) et l'icône s'anime — la flèche
 * tombe dans le bac et l'anneau se remplit (launcher), la manette cligne des yeux et hoche la tête (Discord).
 */
public class BoutonPilule extends Pane implements Etat.Apercu {

    /** Teinte de la médaille et du texte. */
    public enum Variante {
        LAUNCHER("#5980c3", "#79b2da", "#9fd2ff", "#5b97e6", "#3f78d0"),
        DISCORD("#6577e8", "#8aa6ec", "#a5adff", "#6f7cf5", "#5865f2");

        final String texteHaut, texteBas, m0, m60, m100;

        Variante(String texteHaut, String texteBas, String m0, String m60, String m100) {
            this.texteHaut = texteHaut; this.texteBas = texteBas; this.m0 = m0; this.m60 = m60; this.m100 = m100;
        }
    }

    static final String SVG_FLECHE = "M12 5.8v7.6m-3.4-3.4 3.4 3.4 3.4-3.4";
    static final String SVG_BAC = "M7.2 16.6h9.6";

    private final Variante variante;
    private final double hauteur, bord;
    private final Text texte = new Text();
    private final Group medaille = new Group();
    private final Group icone = new Group();
    private final Rectangle bande = new Rectangle();
    private final Rectangle anneauFocus = new Rectangle();
    private final ColorAdjust lumiere = new ColorAdjust();
    private final Group contenu = new Group();
    private double largeur;
    private SVGPath fleche, bac, yeux;
    private Circle anneau;
    private Timeline survol, balayage, boucleIcone, appui, anneauAnim;
    private EventHandler<ActionEvent> surAction;

    public BoutonPilule(String libelle, Variante variante) {
        Polices.charger();
        this.variante = variante;
        this.hauteur = Jetons.rem(1.95);
        this.bord = Jetons.rem(0.1);
        setPickOnBounds(false);
        setFocusTraversable(true);
        setCursor(Curseurs.main());
        getStyleClass().add("kit-bouton-pilule");
        texte.setText(libelle);
        texte.setFont(Polices.noir(Jetons.rem(0.6)));
        texte.setFill(Jetons.vertical(variante.texteHaut, variante.texteBas));
        texte.setTextOrigin(VPos.CENTER);
        texte.setMouseTransparent(true);
        construireIcone();
        getChildren().add(contenu);
        construire();
        brancher();
    }

    private void construireIcone() {
        icone.getChildren().add(new Rectangle(24, 24, Color.TRANSPARENT));
        if (variante == Variante.LAUNCHER) {
            anneau = new Circle(12, 12, 10.4);
            anneau.setFill(null);
            anneau.setStroke(Color.WHITE);
            anneau.setStrokeWidth(1.6);
            anneau.setStrokeLineCap(StrokeLineCap.ROUND);
            anneau.getStrokeDashArray().setAll(65.3, 65.3);
            anneau.setStrokeDashOffset(65.3);
            anneau.setRotate(-90);
            fleche = trait(SVG_FLECHE);
            bac = trait(SVG_BAC);
            icone.getChildren().addAll(anneau, fleche, bac);
        } else {
            SVGPath corps = new SVGPath();
            corps.setContent(BoutonJouer.SVG_DISCORD);
            corps.setFill(Color.WHITE);
            yeux = new SVGPath();
            yeux.setContent(BoutonJouer.SVG_DISCORD_YEUX);
            yeux.setFill(Color.web(variante.m60));
            icone.getChildren().addAll(corps, yeux);
        }
    }

    private static SVGPath trait(String d) {
        SVGPath p = new SVGPath();
        p.setContent(d);
        p.setFill(null);
        p.setStroke(Color.WHITE);
        p.setStrokeWidth(2.4);
        p.setStrokeLineCap(StrokeLineCap.ROUND);
        p.setStrokeLineJoin(StrokeLineJoin.ROUND);
        return p;
    }

    private void construire() {
        contenu.getChildren().clear();
        double tw = texte.getLayoutBounds().getWidth();
        double gPad = Jetons.rem(0.22), dPad = Jetons.rem(0.95), ecart = Jetons.rem(0.38), dm = Jetons.rem(1.45);
        largeur = 2 * bord + gPad + dm + ecart + tw + dPad;
        setPrefSize(largeur, hauteur);
        setMinSize(largeur, hauteur);
        setMaxSize(largeur, hauteur);
        double r = hauteur / 2;

        contenu.getChildren().addAll(
                Calques.ombre(largeur, hauteur, r, -Jetons.rem(0.5), 0, Jetons.rem(0.7), -Jetons.rem(0.25), Calques.rgba(110, 210, 255, 0.55)),
                Calques.ombre(largeur, hauteur, r, Jetons.rem(0.5), 0, Jetons.rem(0.7), -Jetons.rem(0.25), Calques.rgba(110, 210, 255, 0.55)),
                Calques.ombre(largeur, hauteur, r, 0, 0, Jetons.rem(0.45), 0, Calques.rgba(140, 205, 255, 0.55)),
                Calques.ombre(largeur, hauteur, r, 0, 0, Jetons.rem(0.15), 0, Calques.rgba(255, 255, 255, 0.9)));

        Rectangle lisere = new Rectangle(largeur, hauteur);
        lisere.setArcWidth(hauteur);
        lisere.setArcHeight(hauteur);
        lisere.setFill(Jetons.lisereIrise());

        double iw = largeur - 2 * bord, ih = hauteur - 2 * bord;
        Pane interieur = new Pane();
        interieur.setLayoutX(bord);
        interieur.setLayoutY(bord);
        interieur.resize(iw, ih);
        Rectangle clip = new Rectangle(iw, ih);
        clip.setArcWidth(ih);
        clip.setArcHeight(ih);
        interieur.setClip(clip);
        Region base = new Region();
        base.resize(iw, ih);
        base.setBackground(Calques.fond(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.WHITE), new Stop(1, Color.web("#f1f7ff")))));
        Region rose = Calques.ellipse(0.22 * iw, 0.5 * ih, 0.6 * iw, 1.2 * ih, Calques.rgba(255, 214, 240, 0.5), 0.7);
        double lb = 0.36 * iw;
        bande.setWidth(lb);
        bande.setHeight(ih * 1.4);
        bande.setY(-ih * 0.2);
        bande.getTransforms().setAll(new Shear(Math.tan(Math.toRadians(20)), 0, lb / 2, ih / 2));
        bande.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 255, 255, 0)), new Stop(0.5, Color.rgb(255, 255, 255, 0.85)), new Stop(1, Color.rgb(255, 255, 255, 0))));
        bande.setTranslateX(-1.7 * lb);
        interieur.getChildren().addAll(base, rose, bande);
        interieur.setEffect(Calques.ombreInterne(0, -Jetons.rem(0.12), Jetons.rem(0.25), Calques.rgba(170, 205, 245, 0.35)));

        // médaille : disque en dégradé radial (50 % 30 %), reflet interne en haut, petite ombre portée
        medaille.getChildren().clear();
        Circle disque = new Circle(dm / 2, dm / 2, dm / 2);
        disque.setFill(new RadialGradient(0, 0, 0.5, 0.3, 0.75, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web(variante.m0)), new Stop(0.6, Color.web(variante.m60)), new Stop(1, Color.web(variante.m100))));
        InnerShadow reflet = new InnerShadow(BlurType.ONE_PASS_BOX, Calques.rgba(255, 255, 255, 0.55), 0, 0, 0, Jetons.rem(0.06));
        DropShadow portee = new DropShadow(BlurType.GAUSSIAN, Calques.rgba(40, 80, 160, 0.3), Calques.flou(Jetons.rem(0.15)), 0, 0, Jetons.rem(0.06));
        reflet.setInput(null);
        portee.setInput(reflet);
        disque.setEffect(portee);
        double ti = Jetons.rem(0.95);
        icone.setScaleX(ti / 24);
        icone.setScaleY(ti / 24);
        icone.setLayoutX(dm / 2 - 12);
        icone.setLayoutY(dm / 2 - 12);
        medaille.getChildren().addAll(disque, icone);
        medaille.setLayoutX(bord + gPad);
        medaille.setLayoutY(hauteur / 2 - dm / 2);
        medaille.setMouseTransparent(true);

        texte.setX(bord + gPad + dm + ecart);
        texte.setY(hauteur / 2);

        double o = Jetons.rem(0.12), e = Jetons.rem(0.1);
        anneauFocus.setX(-o - e / 2);
        anneauFocus.setY(-o - e / 2);
        anneauFocus.setWidth(largeur + 2 * o + e);
        anneauFocus.setHeight(hauteur + 2 * o + e);
        anneauFocus.setArcWidth(hauteur + 2 * o + e);
        anneauFocus.setArcHeight(hauteur + 2 * o + e);
        anneauFocus.setFill(null);
        anneauFocus.setStroke(Jetons.FOCUS);
        anneauFocus.setStrokeWidth(e);
        anneauFocus.setVisible(isFocusVisible());
        anneauFocus.setMouseTransparent(true);

        Rectangle zone = new Rectangle(largeur, hauteur, Color.TRANSPARENT);
        zone.setArcWidth(hauteur);
        zone.setArcHeight(hauteur);
        contenu.getChildren().addAll(lisere, interieur, zone, medaille, texte, anneauFocus);
    }

    public void setTexte(String libelle) { texte.setText(libelle); construire(); }

    private void brancher() {
        hoverProperty().addListener((o, a, b) -> { if (!isDisabled()) survoler(b); });
        pressedProperty().addListener((o, a, b) -> { if (!isDisabled()) presser(b); });
        focusVisibleProperty().addListener((o, a, b) -> anneauFocus.setVisible(b));
        disabledProperty().addListener((o, a, b) -> {
            setOpacity(b ? 0.55 : 1);
            lumiere.setSaturation(b ? -0.35 : 0);
            setEffect(b ? lumiere : null);
            setCursor(b ? Curseurs.fleche() : Curseurs.main());
        });
        setOnMouseClicked(e -> { if (e.getButton() == MouseButton.PRIMARY && !isDisabled() && surAction != null) surAction.handle(new ActionEvent(this, this)); });
        setOnKeyPressed(e -> {
            if ((e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) && !isDisabled() && surAction != null) {
                surAction.handle(new ActionEvent(this, this));
                e.consume();
            }
        });
    }

    public void setOnAction(EventHandler<ActionEvent> h) { surAction = h; }

    private void survoler(boolean dessus) {
        if (survol != null) survol.stop();
        survol = new Timeline(new KeyFrame(Duration.millis(300), new KeyValue(translateYProperty(), dessus ? -Jetons.rem(0.1) : 0, Jetons.REBOND)));
        survol.play();
        if (boucleIcone != null) { boucleIcone.stop(); boucleIcone = null; }
        if (anneauAnim != null) anneauAnim.stop();
        double u = 24 / Jetons.rem(0.95);   // 1 px écran → unités de la viewBox de l'icône
        if (dessus) {
            double lb = bande.getWidth();
            if (balayage != null) balayage.stop();
            balayage = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(bande.translateXProperty(), -1.7 * lb)),
                    new KeyFrame(Duration.millis(800), new KeyValue(bande.translateXProperty(), 3.3 * lb, Jetons.BALAYE)));
            balayage.play();
            if (variante == Variante.LAUNCHER) {
                anneauAnim = new Timeline(new KeyFrame(Duration.millis(1200), new KeyValue(anneau.strokeDashOffsetProperty(), 0, Jetons.DOUX)));
                anneauAnim.play();
                double haut = -Jetons.rem(0.5) * u, bas = Jetons.rem(0.06) * u;
                boucleIcone = new Timeline(
                        new KeyFrame(Duration.ZERO, new KeyValue(fleche.translateYProperty(), haut), new KeyValue(fleche.opacityProperty(), 0), new KeyValue(bac.scaleXProperty(), 1)),
                        new KeyFrame(Duration.millis(250), new KeyValue(fleche.opacityProperty(), 1)),
                        new KeyFrame(Duration.millis(550), new KeyValue(bac.scaleXProperty(), 1)),
                        new KeyFrame(Duration.millis(600), new KeyValue(fleche.translateYProperty(), bas, Jetons.TOMBE), new KeyValue(fleche.opacityProperty(), 1)),
                        new KeyFrame(Duration.millis(650), new KeyValue(bac.scaleXProperty(), 1.25, Interpolator.EASE_OUT), new KeyValue(bac.translateYProperty(), Jetons.rem(0.04) * u)),
                        new KeyFrame(Duration.millis(800), new KeyValue(fleche.opacityProperty(), 0), new KeyValue(bac.scaleXProperty(), 1, Interpolator.EASE_OUT), new KeyValue(bac.translateYProperty(), 0)),
                        new KeyFrame(Duration.millis(1000), new KeyValue(fleche.translateYProperty(), bas)));
            } else {
                boucleIcone = new Timeline(
                        new KeyFrame(Duration.ZERO, new KeyValue(yeux.scaleYProperty(), 1)),
                        new KeyFrame(Duration.millis(640), new KeyValue(yeux.scaleYProperty(), 1)),
                        new KeyFrame(Duration.millis(736), new KeyValue(yeux.scaleYProperty(), 0.1, Interpolator.EASE_BOTH)),
                        new KeyFrame(Duration.millis(832), new KeyValue(yeux.scaleYProperty(), 1, Interpolator.EASE_BOTH)),
                        new KeyFrame(Duration.millis(1600), new KeyValue(yeux.scaleYProperty(), 1)));
                Timeline hoche = new Timeline(
                        new KeyFrame(Duration.ZERO, new KeyValue(icone.rotateProperty(), 0)),
                        new KeyFrame(Duration.millis(210), new KeyValue(icone.rotateProperty(), -12, Jetons.REBOND)),
                        new KeyFrame(Duration.millis(420), new KeyValue(icone.rotateProperty(), 5, Jetons.REBOND)),
                        new KeyFrame(Duration.millis(600), new KeyValue(icone.rotateProperty(), 0, Jetons.REBOND)));
                hoche.play();
            }
            boucleIcone.setCycleCount(Animation.INDEFINITE);
            boucleIcone.play();
        } else {
            if (variante == Variante.LAUNCHER) {
                anneauAnim = new Timeline(new KeyFrame(Duration.millis(300), new KeyValue(anneau.strokeDashOffsetProperty(), 65.3)));
                anneauAnim.play();
                fleche.setTranslateY(0); fleche.setOpacity(1); bac.setScaleX(1); bac.setTranslateY(0);
            } else {
                yeux.setScaleY(1);
            }
        }
    }

    private void presser(boolean bas) {
        if (appui != null) appui.stop();
        setEffect(lumiere);
        appui = new Timeline(new KeyFrame(Duration.millis(80),
                new KeyValue(translateYProperty(), bas ? Jetons.rem(0.04) : (isHover() ? -Jetons.rem(0.1) : 0)),
                new KeyValue(lumiere.brightnessProperty(), bas ? -0.03 : 0)));
        if (!bas) appui.setOnFinished(e -> { if (!isDisabled()) setEffect(null); });
        appui.play();
    }

    @Override
    public void apercu(Etat etat) {
        setDisable(etat == Etat.DESACTIVE);
        setEffect(etat == Etat.PRESSE || etat == Etat.DESACTIVE ? lumiere : null);
        setTranslateY(etat == Etat.SURVOL ? -Jetons.rem(0.1) : etat == Etat.PRESSE ? Jetons.rem(0.04) : 0);
        lumiere.setBrightness(etat == Etat.PRESSE ? -0.03 : 0);
        bande.setTranslateX(etat == Etat.SURVOL ? 0.5 * largeur - bande.getWidth() / 2 : -1.7 * bande.getWidth());
        if (variante == Variante.LAUNCHER) anneau.setStrokeDashOffset(etat == Etat.SURVOL ? 20 : 65.3);
    }

    /** La médaille seule (réutilisable ailleurs : liste des comptes, barre système…). */
    public Node getMedaille() { return medaille; }

}
