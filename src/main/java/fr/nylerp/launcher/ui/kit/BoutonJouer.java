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
import javafx.scene.effect.ColorAdjust;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import javafx.scene.transform.Shear;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Le gros bouton du site ({@code .lanceur-principal} : « Installer le launcher » du héros, « Rejoindre le Discord »),
 * devenu « Jouer » dans le launcher. Reproduit tel quel (base.css, mesures en rem du site) :
 * <ul>
 *   <li>pilule à bord irisé de 0,13 rem, intérieur givré blanc → bleuté, lueur rose à gauche, trame de points à droite ;</li>
 *   <li>halo bleu ciel : deux lueurs latérales (±0,9 rem, flou 1,1 rem, étalement −0,3 rem), une lueur globale, un
 *       liseré de lumière blanche ; ombre interne en bas ;</li>
 *   <li>texte Nunito 900 en dégradé #5980c3 → #79b2da, petit triangle sous le texte ;</li>
 *   <li>survol : la pilule se soulève (−0,12 rem, rebond 0,3 s), un reflet la balaie (0,85 s), le triangle saute ;
 *       appui : 98 % et un rien plus sombre.</li>
 * </ul>
 */
public class BoutonJouer extends Pane implements Etat.Apercu {

    public enum Style { LAUNCHER, DISCORD }

    /** Dessin de l'icône Discord du site (MANETTE, viewBox 24). */
    static final String SVG_DISCORD = "M19.6 5.3A17 17 0 0 0 15.4 4l-.5 1a15.7 15.7 0 0 0-5.8 0l-.5-1a17 17 0 0 0-4.2 1.3C1.7 9.3 1 13.2 1.3 17a17 17 0 0 0 5.2 2.6l1.1-1.8a11 11 0 0 1-1.7-.8l.4-.3a12.2 12.2 0 0 0 11.4 0l.4.3c-.5.3-1.1.6-1.7.8l1.1 1.8a17 17 0 0 0 5.2-2.6c.4-4.4-.6-8.3-3.1-11.7Z";
    static final String SVG_DISCORD_YEUX = "M8.7 10.5a1.9 2.1 0 1 0 0 4.2a1.9 2.1 0 1 0 0-4.2ZM15.3 10.5a1.9 2.1 0 1 0 0 4.2a1.9 2.1 0 1 0 0-4.2Z";

    private final Style style;
    private final double largeur, hauteur;
    private final double px, py, pw, ph, bord;
    private final Text texte = new Text();
    private final Group contenu = new Group();
    private final Polygon triangle = new Polygon();
    private final Rectangle bande = new Rectangle();
    private final Rectangle anneauFocus = new Rectangle();
    private final ColorAdjust lumiere = new ColorAdjust();
    private Node icone;
    private Group filigrane;
    private final List<Timeline> particules = new ArrayList<>();
    private Timeline survol, balayage, saut, appui;
    private EventHandler<ActionEvent> surAction;

    public BoutonJouer(String libelle) { this(libelle, Style.LAUNCHER, Jetons.rem(15.6771)); }

    public BoutonJouer(String libelle, Style style) {
        this(libelle, style, style == Style.DISCORD ? Jetons.rem(13.4) : Jetons.rem(15.6771));
    }

    /** @param largeur largeur totale de l'élément (halo compris) ; la pilule fait largeur − 2 × 0,62 rem. */
    public BoutonJouer(String libelle, Style style, double largeur) {
        Polices.charger();
        this.style = style;
        this.largeur = largeur;
        this.hauteur = Jetons.rem(4.375);
        this.px = Jetons.rem(0.62);
        this.py = Jetons.rem(0.82);
        this.pw = largeur - 2 * px;
        this.ph = hauteur - 2 * py;
        this.bord = Jetons.rem(0.13);
        setPrefSize(largeur, hauteur);
        setMinSize(largeur, hauteur);
        setMaxSize(largeur, hauteur);
        setPickOnBounds(false);
        setFocusTraversable(true);
        setCursor(Curseurs.main());
        getStyleClass().add("kit-bouton-jouer");
        construire(libelle);
        brancher();
    }

    private void construire(String libelle) {
        double r = ph / 2;
        // halo : derrière la pilule (box-shadow de .lanceur-principal::before)
        contenu.getChildren().addAll(
                Calques.ombre(pw, ph, r, px - Jetons.rem(0.9), py, Jetons.rem(1.1), -Jetons.rem(0.3), Calques.rgba(110, 210, 255, 0.75)),
                Calques.ombre(pw, ph, r, px + Jetons.rem(0.9), py, Jetons.rem(1.1), -Jetons.rem(0.3), Calques.rgba(110, 210, 255, 0.75)),
                Calques.ombre(pw, ph, r, px, py, Jetons.rem(0.7), 0, Calques.rgba(140, 205, 255, 0.7)),
                Calques.ombre(pw, ph, r, px, py, Jetons.rem(0.25), 0, Calques.rgba(255, 255, 255, 0.9)));

        // le liseré irisé (border-box)
        Rectangle lisere = new Rectangle(px, py, pw, ph);
        lisere.setArcWidth(ph);
        lisere.setArcHeight(ph);
        lisere.setFill(Jetons.lisereIrise());

        // l'intérieur (padding-box) : base, trame, voile blanc à gauche, lueur rose
        double iw = pw - 2 * bord, ih = ph - 2 * bord;
        Pane interieur = new Pane();
        interieur.setLayoutX(px + bord);
        interieur.setLayoutY(py + bord);
        interieur.setPrefSize(iw, ih);
        interieur.resize(iw, ih);
        Rectangle clip = new Rectangle(iw, ih);
        clip.setArcWidth(ih);
        clip.setArcHeight(ih);
        interieur.setClip(clip);
        Region base = calque(iw, ih, new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.WHITE), new Stop(1, Color.web("#eef6ff"))));
        Region trame = calque(iw, ih, Calques.trame(Jetons.rem(0.36), Jetons.rem(0.06), Calques.rgba(150, 190, 235, 0.6)));
        Region voile = calque(iw, ih, new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.WHITE), new Stop(0.5, Color.WHITE), new Stop(0.86, Color.rgb(255, 255, 255, 0))));
        Region rose = Calques.ellipse(0.22 * iw, 0.5 * ih, 0.6 * iw, 1.2 * ih, Calques.rgba(255, 214, 240, 0.55), 0.7);
        interieur.getChildren().addAll(base, trame, voile, rose);
        interieur.setEffect(Calques.ombreInterne(0, -Jetons.rem(0.18), Jetons.rem(0.35), Calques.rgba(170, 205, 245, 0.35)));

        // le reflet qui balaie (.balaye::before), détouré à la pilule
        Pane balaye = new Pane();
        balaye.setLayoutX(px);
        balaye.setLayoutY(py);
        balaye.resize(pw, ph);
        Rectangle clipB = new Rectangle(pw, ph);
        clipB.setArcWidth(ph);
        clipB.setArcHeight(ph);
        balaye.setClip(clipB);
        balaye.setMouseTransparent(true);
        double lb = 0.36 * pw;
        bande.setWidth(lb);
        bande.setHeight(ph * 1.6);
        bande.setY(-ph * 0.3);
        bande.getTransforms().add(new Shear(Math.tan(Math.toRadians(18)), 0, lb / 2, ph * 0.5));
        Color blanc = Color.WHITE;
        Color teinte = style == Style.DISCORD ? Calques.rgba(150, 160, 255, 0.45) : Calques.rgba(255, 255, 255, 0.55);
        bande.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Calques.transparent(blanc)), new Stop(0.4, teinte), new Stop(0.5, Calques.rgba(255, 255, 255, 0.95)),
                new Stop(0.6, teinte), new Stop(1, Calques.transparent(blanc))));
        bande.setTranslateX(-1.8 * lb);
        balaye.getChildren().add(bande);

        // texte en dégradé (Nunito 900, 1,0547 rem)
        texte.setText(libelle);
        texte.setFont(Polices.noir(Jetons.rem(1.0547)));
        texte.setFill(style == Style.DISCORD ? Jetons.texteDiscord() : Jetons.texteLauncher());
        texte.setTextOrigin(VPos.CENTER);
        texte.setMouseTransparent(true);

        // petit triangle sous le texte (::after) — 0,52 × 0,3 rem, #7fb3e6, bas à 1,06 rem du bas
        double tw = Jetons.rem(0.52), th = Jetons.rem(0.3), tb = hauteur - Jetons.rem(1.06);
        triangle.getPoints().setAll(largeur / 2, tb - th, largeur / 2 + tw / 2, tb, largeur / 2 - tw / 2, tb);
        triangle.setFill(Color.web("#7fb3e6"));
        triangle.setMouseTransparent(true);

        // anneau de focus (:focus-visible::before → outline 0,12 rem #4c8ff0, décalé de 0,12 rem)
        double o = Jetons.rem(0.12);
        anneauFocus.setX(px - o - o / 2);
        anneauFocus.setY(py - o - o / 2);
        anneauFocus.setWidth(pw + 3 * o);
        anneauFocus.setHeight(ph + 3 * o);
        anneauFocus.setArcWidth(ph + 3 * o);
        anneauFocus.setArcHeight(ph + 3 * o);
        anneauFocus.setFill(null);
        anneauFocus.setStroke(Jetons.FOCUS);
        anneauFocus.setStrokeWidth(o);
        anneauFocus.setVisible(false);
        anneauFocus.setMouseTransparent(true);

        if (style == Style.DISCORD) {
            double ti = Jetons.rem(1.25);
            SVGPath corps = new SVGPath();
            corps.setContent(SVG_DISCORD);
            corps.setFill(Color.web("#6f7cf5"));
            SVGPath yeux = new SVGPath();
            yeux.setContent(SVG_DISCORD_YEUX);
            yeux.setFill(Color.WHITE);
            Group g = new Group(new Rectangle(24, 24, Color.TRANSPARENT), corps, yeux);   // boîte 0..24 : centre = (12, 12)
            g.setScaleX(ti / 24);
            g.setScaleY(ti / 24);
            g.setEffect(new javafx.scene.effect.DropShadow(0, 0, Jetons.rem(0.04), Color.WHITE));
            g.setMouseTransparent(true);
            icone = g;
        }

        Rectangle zone = new Rectangle(px, py, pw, ph);   // zone cliquable = la pilule
        zone.setArcWidth(ph);
        zone.setArcHeight(ph);
        zone.setFill(Color.TRANSPARENT);

        contenu.getChildren().addAll(lisere, interieur, balaye, zone, texte, triangle, anneauFocus);
        if (icone != null) contenu.getChildren().add(icone);
        getChildren().add(contenu);
        placerTexte();
    }

    private Region calque(double l, double h, javafx.scene.paint.Paint p) {
        Region r = new Region();
        r.setManaged(false);
        r.resize(l, h);
        r.setBackground(Calques.fond(p));
        r.setMouseTransparent(true);
        return r;
    }

    private void placerTexte() {
        double lt = texte.getLayoutBounds().getWidth();
        double cy = py + ph / 2 - Jetons.rem(0.03);
        if (icone != null) {
            double ti = Jetons.rem(1.25), ecart = Jetons.rem(0.45), total = ti + ecart + lt;
            double x0 = largeur / 2 - total / 2;
            // le Group est mis à l'échelle autour de son centre (viewBox 24) : on recale son centre
            icone.setLayoutX(x0 + ti / 2 - 12);
            icone.setLayoutY(cy - 12);
            texte.setX(x0 + ti + ecart);
        } else {
            texte.setX(largeur / 2 - lt / 2);
        }
        texte.setY(cy);
    }

    /** Change le libellé (« Jouer », « Mettre à jour »…) sans changer la taille de la pilule. */
    public void setTexte(String libelle) {
        texte.setText(libelle);
        placerTexte();
    }

    public String getTexte() { return texte.getText(); }

    /** Le filigrane du héros (« POKÉNYLE » en bas à droite de la pilule, 900 0,34 rem, interlettrage 0,08 em, bleu à 32 %). */
    public void setFiligrane(String mot) {
        if (filigrane != null) contenu.getChildren().remove(filigrane);
        filigrane = null;
        if (mot == null || mot.isEmpty()) return;
        filigrane = Calques.texteEspace(mot, Polices.noir(Jetons.rem(0.34)), 0.08, Calques.rgba(127, 179, 230, 0.32));
        filigrane.setLayoutX(largeur / 2 + Jetons.rem(4.2));
        filigrane.setLayoutY(Jetons.rem(2.58));
        contenu.getChildren().add(contenu.getChildren().indexOf(texte), filigrane);
    }

    /** Les particules de part et d'autre du bouton (héros du site, cycle de 4 s). */
    public void setParticules(boolean actives) {
        particules.forEach(Timeline::stop);
        contenu.getChildren().removeIf(n -> "particules".equals(n.getId()));
        particules.clear();
        if (!actives) return;
        double gauche = -Jetons.rem(3.49), droite = largeur + Jetons.rem(3.49) - Jetons.rem(6.4844);
        for (int cote = 0; cote < 2; cote++) {
            for (int couche = 0; couche < 2; couche++) {
                Group g = nuee(couche == 1);
                g.setId("particules");
                g.setLayoutX(cote == 0 ? gauche : droite);
                g.setLayoutY(Jetons.rem(2.1875) - Jetons.rem(4.0365) / 2);
                if (cote == 1) { g.setScaleX(-1); }
                g.setMouseTransparent(true);
                contenu.getChildren().add(0, g);
                double dx0 = Jetons.rem(0.8) * (cote == 1 ? -1 : 1), dx1 = -Jetons.rem(1.6) * (cote == 1 ? -1 : 1);
                Timeline t = new Timeline(
                        new KeyFrame(Duration.ZERO, new KeyValue(g.translateXProperty(), dx0, Interpolator.LINEAR),
                                new KeyValue(g.translateYProperty(), 0, Interpolator.LINEAR), new KeyValue(g.opacityProperty(), 0, Interpolator.LINEAR)),
                        new KeyFrame(Duration.millis(600), new KeyValue(g.opacityProperty(), 1, Interpolator.LINEAR)),
                        new KeyFrame(Duration.millis(2800), new KeyValue(g.opacityProperty(), 0.85, Interpolator.LINEAR)),
                        new KeyFrame(Duration.millis(4000), new KeyValue(g.translateXProperty(), dx1, Interpolator.LINEAR),
                                new KeyValue(g.translateYProperty(), -Jetons.rem(0.4), Interpolator.LINEAR), new KeyValue(g.opacityProperty(), 0, Interpolator.LINEAR)));
                t.setCycleCount(Animation.INDEFINITE);
                t.jumpTo(Duration.millis(couche == 1 ? 2000 : 0));
                t.play();
                particules.add(t);
            }
        }
    }

    private Group nuee(boolean teinte) {
        double w = Jetons.rem(6.4844), h = Jetons.rem(4.0365);
        double[][] pts = {{0.30, 0.40, 0.16}, {0.52, 0.62, 0.12}, {0.18, 0.70, 0.10}, {0.64, 0.30, 0.20}, {0.42, 0.22, 0.14}};
        String[] coul = teinte ? new String[]{"#ffffff", "#e6f0ff", "#c4e0ff", "#ffffff", "#a8d0ff"}
                : new String[]{"#ffffff", "#e3f6ff", "#bfeaff", "#ffffff", "#9fe0ff"};
        Group g = new Group();
        for (int i = 0; i < pts.length; i++) {
            double r = Jetons.rem(pts[i][2]);
            Color c = Color.web(coul[i]);
            Region p = Calques.ellipse(pts[i][0] * w, pts[i][1] * h, r, r, new Stop(0, c), new Stop(0.4, c), new Stop(1, Calques.transparent(c)));
            g.getChildren().add(p);
        }
        Rectangle cadre = new Rectangle(w, h, Color.TRANSPARENT);
        g.getChildren().add(0, cadre);
        return g;
    }

    private void brancher() {
        hoverProperty().addListener((o, a, b) -> { if (!isDisabled()) survoler(b); });
        pressedProperty().addListener((o, a, b) -> { if (!isDisabled()) presser(b); });
        focusVisibleProperty().addListener((o, a, b) -> anneauFocus.setVisible(b));
        disabledProperty().addListener((o, a, b) -> desactiver(b));
        setOnMouseClicked(e -> { if (e.getButton() == MouseButton.PRIMARY && !isDisabled()) declencher(); });
        setOnKeyPressed(e -> {
            if ((e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) && !isDisabled()) { declencher(); e.consume(); }
        });
    }

    private void declencher() { if (surAction != null) surAction.handle(new ActionEvent(this, this)); }

    public void setOnAction(EventHandler<ActionEvent> h) { surAction = h; }

    private void survoler(boolean dessus) {
        if (survol != null) survol.stop();
        survol = new Timeline(new KeyFrame(Duration.millis(300),
                new KeyValue(translateYProperty(), dessus ? -Jetons.rem(0.12) : 0, Jetons.REBOND),
                new KeyValue(lumiere.brightnessProperty(), dessus ? 0.03 : 0, Interpolator.EASE_OUT)));
        if (dessus) setEffect(lumiere);
        else survol.setOnFinished(e -> { if (!isHover() && !isPressed()) setEffect(null); });
        survol.play();
        if (dessus) {
            double lb = bande.getWidth();
            if (balayage != null) balayage.stop();
            balayage = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(bande.translateXProperty(), -1.8 * lb)),
                    new KeyFrame(Duration.millis(850), new KeyValue(bande.translateXProperty(), 3.6 * lb, Jetons.BALAYE)));
            balayage.play();
            if (saut != null) saut.stop();
            saut = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(triangle.translateYProperty(), 0)),
                    new KeyFrame(Duration.millis(240), new KeyValue(triangle.translateYProperty(), Jetons.rem(0.16), Jetons.REBOND)),
                    new KeyFrame(Duration.millis(600), new KeyValue(triangle.translateYProperty(), 0, Jetons.REBOND)));
            saut.play();
        }
    }

    private void presser(boolean bas) {
        if (appui != null) appui.stop();
        setEffect(lumiere);
        appui = new Timeline(new KeyFrame(Duration.millis(80),
                new KeyValue(scaleXProperty(), bas ? 0.98 : 1), new KeyValue(scaleYProperty(), bas ? 0.98 : 1),
                new KeyValue(lumiere.brightnessProperty(), bas ? -0.03 : (isHover() ? 0.03 : 0))));
        if (!bas) appui.setOnFinished(e -> { if (!isHover() && !isDisabled()) setEffect(null); });
        appui.play();
    }

    private void desactiver(boolean oui) {
        setOpacity(oui ? 0.55 : 1);
        lumiere.setSaturation(oui ? -0.35 : 0);
        setCursor(oui ? Curseurs.fleche() : Curseurs.main());
        if (oui) { setTranslateY(0); setScaleX(1); setScaleY(1); lumiere.setBrightness(0); }
        setEffect(oui ? lumiere : null);
    }

    @Override
    public void apercu(Etat etat) {
        setDisable(etat == Etat.DESACTIVE);
        setEffect(etat == Etat.NORMAL ? null : lumiere);
        setTranslateY(etat == Etat.SURVOL ? -Jetons.rem(0.12) : 0);
        setScaleX(etat == Etat.PRESSE ? 0.98 : 1);
        setScaleY(etat == Etat.PRESSE ? 0.98 : 1);
        lumiere.setBrightness(etat == Etat.SURVOL ? 0.03 : etat == Etat.PRESSE ? -0.03 : 0);
        // reflet figé au milieu de sa course pour le voir sur une capture
        bande.setTranslateX(etat == Etat.SURVOL ? 0.55 * pw - bande.getWidth() / 2 : -1.8 * bande.getWidth());
        triangle.setTranslateY(etat == Etat.SURVOL ? Jetons.rem(0.1) : 0);
    }

    public Style getStyleBouton() { return style; }
}
