package fr.nylerp.launcher.ui.kit;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Bounds;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.ImagePattern;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Line;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Les titres du launcher, en Northura Black SemExp au style Aniimo (décision owner du 16/10 ; références
 * {@code ~/sites/pokenyle-v2/fournis/polices-titres/references/aniimo-titre-*}) :
 * <ul>
 *   <li>{@link Style#SECONDAIRE} (sections, classement, panneaux) : lettres blanches à peine teintées d'un dégradé pastel
 *       (rose, lilas, jaune pâle, bleu glacier), léger biseau bleuté en bas, ombre bleutée douce ;</li>
 *   <li>{@link Style#PRINCIPAL} (gros titres : le nom de l'univers…) : même police, plus grande, en capitales, lettres
 *       blanches, reflets irisés rose → lilas → cyan sur la première lettre et cyan sur la dernière, halo blanc et petits
 *       traits d'éclaboussure autour du début et de la fin.</li>
 * </ul>
 * Le remplissage est une texture peinte une fois (Canvas) et posée sur UN seul nœud texte : le crénage de la police est
 * conservé. En option, les éclats du site ({@link #MOTIFS}, un motif par titre) peuvent accompagner un titre.
 */
public class TitreEclats extends Pane {

    public enum Style { SECONDAIRE, PRINCIPAL }

    // ── éclats du site (src/titres.mjs) : [forme, position, largeur em, hauteur em, couleur « a>b » = dégradé 135°, rotation] ──
    private static final Map<String, double[]> FORMES = Map.of(
            "etoile", new double[]{50, 0, 62, 38, 100, 50, 62, 62, 50, 100, 38, 62, 0, 50, 38, 38},
            "fine", new double[]{50, 0, 55, 45, 100, 50, 55, 55, 50, 100, 45, 55, 0, 50, 45, 45},
            "eclat", new double[]{0, 100, 30, 20, 100, 0, 60, 60},
            "losange", new double[]{50, 0, 100, 50, 50, 100, 0, 50},
            "etoile5", new double[]{50, 0, 61, 35, 98, 35, 68, 57, 79, 91, 50, 70, 21, 91, 32, 57, 2, 35, 39, 35},
            "croix", new double[]{42, 0, 58, 0, 58, 42, 100, 42, 100, 58, 58, 58, 58, 100, 42, 100, 42, 58, 0, 58, 0, 42, 42, 42});

    /** Un éclat : forme, côté horizontal (« left »/« right »), valeur em, côté vertical (« top »/« bottom »), valeur em, l, h, couleur, rotation. */
    public record Eclat(String forme, String cx, double vx, String cy, double vy, double l, double h, String couleur, double rotation) {}

    /** Les motifs du site, plus ceux propres au launcher (compte, réglages, univers, nylerp) dans le même esprit. */
    public static final Map<String, List<Eclat>> MOTIFS = Map.ofEntries(
            Map.entry("classements", List.of(new Eclat("etoile", "right", 0.02, "top", 0, 0.2, 0.24, "#ff8fd4", 0), new Eclat("eclat", "right", -0.3, "top", -0.12, 0.42, 0.34, "#ffb3e6>#e66bd0", 0), new Eclat("etoile", "left", -0.22, "bottom", 0.12, 0.14, 0.17, "#ff8fd4", 0))),
            Map.entry("cadeau", List.of(new Eclat("etoile5", "left", -0.34, "top", -0.18, 0.3, 0.3, "#ffd75e", -12), new Eclat("fine", "right", -0.34, "top", 0.26, 0.26, 0.3, "#ffffff", 0), new Eclat("point", "right", 0.08, "bottom", -0.06, 0.08, 0.08, "#ffe9a0", 0), new Eclat("point", "right", -0.14, "bottom", 0.1, 0.055, 0.055, "#ffffff", 0))),
            Map.entry("comparer", List.of(new Eclat("losange", "right", -0.2, "top", -0.1, 0.2, 0.27, "#8fe3ff", 0), new Eclat("croix", "left", -0.26, "top", 0.02, 0.14, 0.14, "#ffffff", 0), new Eclat("croix", "left", -0.1, "top", -0.22, 0.09, 0.09, "#bff1ff", 0), new Eclat("eclat", "right", -0.3, "bottom", 0.02, 0.3, 0.24, "#ffffff>#6fd6ff", 90))),
            Map.entry("boutique", List.of(new Eclat("etoile", "right", -0.14, "top", -0.22, 0.3, 0.36, "#e9d5ff", 0), new Eclat("point", "right", -0.38, "top", 0.24, 0.08, 0.08, "#ffffff", 0), new Eclat("eclat", "left", -0.34, "top", 0.12, 0.3, 0.24, "#ffffff>#c9a8ff", -90))),
            Map.entry("pokenyle", List.of(new Eclat("fine", "right", -0.26, "bottom", 0.18, 0.24, 0.28, "#b8ffe0", 0), new Eclat("losange", "left", -0.2, "top", -0.04, 0.12, 0.16, "#ffe9a8", 0), new Eclat("point", "right", 0.12, "top", -0.14, 0.07, 0.07, "#ffffff", 0), new Eclat("point", "left", 0.3, "bottom", -0.12, 0.05, 0.05, "#b8ffe0", 0))),
            Map.entry("serveur", List.of(new Eclat("etoile5", "right", -0.4, "top", -0.24, 0.36, 0.36, "#ffe08a", 10), new Eclat("fine", "left", -0.3, "bottom", 0.1, 0.24, 0.3, "#bff3ff", 0), new Eclat("point", "right", -0.12, "top", 0.3, 0.07, 0.07, "#ffffff", 0), new Eclat("point", "left", -0.08, "top", -0.12, 0.06, 0.06, "#ffe08a", 0), new Eclat("eclat", "right", 0.3, "bottom", -0.2, 0.24, 0.2, "#ffffff>#9be8ff", 180))),
            Map.entry("raisons", List.of(new Eclat("etoile", "left", -0.3, "top", -0.12, 0.22, 0.26, "#ffa36b", 0), new Eclat("losange", "right", -0.16, "top", -0.02, 0.12, 0.16, "#ffc46b", 0), new Eclat("point", "right", 0.06, "bottom", -0.08, 0.08, 0.08, "#ff7f6b", 0))),
            Map.entry("differences", List.of(new Eclat("croix", "right", -0.22, "top", -0.14, 0.18, 0.18, "#9b8cff", 0), new Eclat("eclat", "left", -0.3, "bottom", 0.06, 0.32, 0.26, "#c3b8ff>#6f7cf6", 180), new Eclat("fine", "right", -0.4, "top", 0.3, 0.16, 0.2, "#7fb2ff", 0))),
            Map.entry("rejoindre", List.of(new Eclat("etoile5", "left", -0.38, "top", -0.2, 0.3, 0.3, "#ffcf5a", -8), new Eclat("etoile5", "left", -0.1, "top", -0.3, 0.14, 0.14, "#ffffff", 14), new Eclat("eclat", "right", -0.32, "top", 0.1, 0.32, 0.26, "#ffe7a0>#ff9f4a", 30))),
            Map.entry("compte", List.of(new Eclat("fine", "left", -0.28, "top", -0.1, 0.22, 0.26, "#9fe0ff", 0), new Eclat("point", "left", -0.06, "top", -0.2, 0.06, 0.06, "#ffffff", 0), new Eclat("losange", "right", -0.22, "bottom", 0.06, 0.14, 0.18, "#ffb3e6", 0))),
            Map.entry("reglages", List.of(new Eclat("croix", "right", -0.26, "top", -0.12, 0.16, 0.16, "#bff1ff", 0), new Eclat("croix", "right", -0.06, "top", -0.26, 0.09, 0.09, "#ffffff", 0), new Eclat("point", "left", -0.16, "bottom", 0.08, 0.07, 0.07, "#9fe0ff", 0))),
            Map.entry("univers", List.of(new Eclat("etoile5", "right", -0.36, "top", -0.22, 0.32, 0.32, "#ffe08a", -10), new Eclat("fine", "left", -0.32, "top", 0.04, 0.22, 0.28, "#ffffff", 0), new Eclat("eclat", "left", -0.1, "bottom", -0.16, 0.26, 0.2, "#ffffff>#ff9fd0", 200))),
            Map.entry("nylerp", List.of(new Eclat("losange", "right", -0.22, "top", -0.08, 0.16, 0.22, "#ffc46b", 0), new Eclat("etoile", "left", -0.26, "bottom", 0.1, 0.18, 0.22, "#ff9a6b", 0), new Eclat("point", "right", -0.06, "bottom", -0.1, 0.07, 0.07, "#ffe9a0", 0))));

    private final Style style;
    private final Text texte = new Text();
    private final Group eclats = new Group();
    private final Group eclaboussures = new Group();
    private final double taille;

    public TitreEclats(String libelle, Style style) {
        this(libelle, style, style == Style.PRINCIPAL ? Jetons.rem(3.2) : Jetons.rem(1.75), null);
    }

    /**
     * @param taille corps de la police en pixels (voir {@link Jetons#rem})
     * @param motif  clé de {@link #MOTIFS} pour accompagner le titre des éclats du site, ou null
     */
    public TitreEclats(String libelle, Style style, double taille, String motif) {
        Polices.charger();
        this.style = style;
        this.taille = taille;
        getStyleClass().add("kit-titre");
        setPickOnBounds(false);
        setMouseTransparent(true);
        texte.setFont(Polices.titre(taille));
        texte.setTextOrigin(VPos.TOP);
        texte.setTextAlignment(TextAlignment.CENTER);
        texte.setBoundsType(javafx.scene.text.TextBoundsType.LOGICAL);
        getChildren().addAll(eclaboussures, texte, eclats);
        setTexte(libelle, motif);
    }

    public void setTexte(String libelle, String motif) {
        texte.setText(style == Style.PRINCIPAL ? libelle.toUpperCase(Locale.FRENCH) : libelle);
        Bounds b = texte.getLayoutBounds();
        double l = b.getWidth(), h = b.getHeight();
        texte.setFill(texture(l, h));
        texte.setEffect(ombre());
        eclaboussures.getChildren().clear();
        if (style == Style.PRINCIPAL) dessinerEclaboussures();
        eclats.getChildren().clear();
        if (motif != null && MOTIFS.containsKey(motif)) poserEclats(MOTIFS.get(motif), l, h);
        setPrefSize(l, h);
        setMinSize(l, h);
        setMaxSize(l, h);
    }

    public String getTexte() { return texte.getText(); }

    // ── remplissage ───────────────────────────────────────────────────────────────────────────────────
    private Paint texture(double l, double h) {
        int k = 2;
        Canvas c = new Canvas(Math.max(1, Math.ceil(l * k)), Math.max(1, Math.ceil(h * k)));
        GraphicsContext g = c.getGraphicsContext2D();
        g.scale(k, k);
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, l, h);
        if (style == Style.SECONDAIRE) {
            // voile pastel en travers des lettres (rose, lilas, jaune pâle, glacier), très léger
            double[][] taches = {{0.10, 0.2, 255, 224, 244, 0.55}, {0.30, 0.8, 236, 214, 255, 0.55}, {0.52, 0.3, 214, 236, 255, 0.5},
                    {0.72, 0.7, 255, 246, 200, 0.6}, {0.92, 0.3, 214, 248, 236, 0.55}};
            for (double[] t : taches) {
                Color co = Color.rgb((int) t[2], (int) t[3], (int) t[4], t[5]);
                g.setFill(new RadialGradient(0, 0, t[0] * l, t[1] * h, Math.max(h * 1.1, l * 0.16), false, CycleMethod.NO_CYCLE,
                        new Stop(0, co), new Stop(1, Calques.transparent(co))));
                g.fillRect(0, 0, l, h);
            }
            // biseau : le bas des lettres bleuit un peu (le relief des titres Aniimo)
            g.setFill(new LinearGradient(0, 0, 0, h, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.rgb(255, 255, 255, 0)), new Stop(0.62, Color.rgb(255, 255, 255, 0)), new Stop(1, Color.rgb(196, 214, 244, 0.85))));
            g.fillRect(0, 0, l, h);
        } else {
            g.setFill(new LinearGradient(0, 0, 0, h, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.rgb(255, 255, 255, 0)), new Stop(0.7, Color.rgb(255, 255, 255, 0)), new Stop(1, Color.rgb(222, 234, 252, 0.8))));
            g.fillRect(0, 0, l, h);
            Bounds premiere = boiteLettre(true), derniere = boiteLettre(false);
            if (premiere != null) irisPremiere(g, premiere);
            if (derniere != null) irisDerniere(g, derniere);
        }
        SnapshotParameters p = new SnapshotParameters();
        p.setFill(Color.TRANSPARENT);
        WritableImage img = c.snapshot(p, null);
        Bounds b = texte.getLayoutBounds();
        return new ImagePattern(img, b.getMinX(), b.getMinY(), l, h, false);
    }

    /** Reflet holographique de la première lettre : rose en haut à gauche, lilas, cyan en bas, point de lumière blanc. */
    private void irisPremiere(GraphicsContext g, Bounds r) {
        g.setFill(new LinearGradient(r.getMinX(), r.getMinY(), r.getMaxX(), r.getMaxY(), false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#ffc2e4")), new Stop(0.42, Color.web("#ffb0d8")), new Stop(0.7, Color.web("#d7c6ff")), new Stop(1, Color.web("#a9ecff"))));
        g.fillRect(r.getMinX(), r.getMinY(), r.getWidth(), r.getHeight());
        g.setFill(new RadialGradient(0, 0, r.getMinX() + r.getWidth() * 0.35, r.getMinY() + r.getHeight() * 0.3, r.getWidth() * 0.45, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 255, 255, 0.85)), new Stop(1, Color.rgb(255, 255, 255, 0))));
        g.fillRect(r.getMinX(), r.getMinY(), r.getWidth(), r.getHeight());
    }

    /** Reflet de la dernière lettre : blanc en haut, cyan glacier puis cyan franc en bas, une touche rose. */
    private void irisDerniere(GraphicsContext g, Bounds r) {
        g.setFill(new LinearGradient(r.getMinX(), r.getMinY(), r.getMaxX() * 0.98, r.getMaxY(), false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#f4feff")), new Stop(0.35, Color.web("#c6f6ff")), new Stop(0.75, Color.web("#8fe7ff")), new Stop(1, Color.web("#ffc8ec"))));
        g.fillRect(r.getMinX(), r.getMinY(), r.getWidth(), r.getHeight());
    }

    /** Boîte de la première (ou dernière) lettre visible, dans le repère du texte. */
    private Bounds boiteLettre(boolean premiere) {
        String s = texte.getText();
        int i = -1;
        if (premiere) { for (int k = 0; k < s.length(); k++) if (!Character.isWhitespace(s.charAt(k))) { i = k; break; } }
        else { for (int k = s.length() - 1; k >= 0; k--) if (!Character.isWhitespace(s.charAt(k))) { i = k; break; } }
        if (i < 0) return null;
        PathElement[] forme = texte.rangeShape(i, i + 1);
        if (forme == null || forme.length == 0) return null;
        javafx.scene.shape.Path p = new javafx.scene.shape.Path(forme);
        return p.getLayoutBounds();
    }

    // ── ombres et halo ────────────────────────────────────────────────────────────────────────────────
    private DropShadow ombre() {
        double em = taille;
        if (style == Style.SECONDAIRE) {
            DropShadow serree = new DropShadow(BlurType.GAUSSIAN, Color.rgb(52, 98, 176, 0.45), Math.max(1, em * 0.05), 0, 0, em * 0.035);
            DropShadow douce = new DropShadow(BlurType.GAUSSIAN, Color.rgb(46, 104, 196, 0.32), em * 0.28, 0, 0, em * 0.07);
            douce.setInput(serree);
            return douce;
        }
        DropShadow halo = new DropShadow(BlurType.GAUSSIAN, Color.rgb(255, 255, 255, 0.85), em * 0.22, 0.25, 0, 0);
        DropShadow bleue = new DropShadow(BlurType.GAUSSIAN, Color.rgb(40, 96, 190, 0.35), em * 0.2, 0, 0, em * 0.05);
        bleue.setInput(halo);
        return bleue;
    }

    /** Petits traits arrondis qui jaillissent du début (en haut à gauche) et de la fin (à droite) — titres principaux Aniimo. */
    private void dessinerEclaboussures() {
        Bounds p = boiteLettre(true), d = boiteLettre(false);
        double em = taille, ep = Math.max(2, em * 0.075);
        if (p != null) {
            // trois tirets qui rayonnent du coin haut-gauche de la première lettre, avec un vide au centre
            double cx = p.getMinX() + em * 0.04, cy = p.getMinY() + em * 0.16;
            trait(cx, cy, 172, em * 0.17, em * 0.17, ep, Color.WHITE);
            trait(cx, cy, 134, em * 0.19, em * 0.19, ep, Color.WHITE);
            trait(cx, cy, 98, em * 0.20, em * 0.13, ep * 0.85, Color.web("#ffd3ec"));
        }
        if (d != null) {
            // deux tirets à droite de la dernière lettre et une goutte en dessous
            double cx = d.getMaxX() - em * 0.02, cy = d.getMinY() + d.getHeight() * 0.42;
            trait(cx, cy, 24, em * 0.16, em * 0.17, ep, Color.WHITE);
            trait(cx, cy, -12, em * 0.18, em * 0.15, ep, Color.web("#a8f0ff"));
            double a = Math.toRadians(-46);
            javafx.scene.shape.Circle goutte = new javafx.scene.shape.Circle(cx + Math.cos(a) * em * 0.28, cy - Math.sin(a) * em * 0.28, ep * 0.62, Color.web("#a8f0ff"));
            eclaboussures.getChildren().add(goutte);
        }
        eclaboussures.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(40, 96, 190, 0.3), em * 0.12, 0, 0, em * 0.03));
    }

    private void trait(double cx, double cy, double angleDeg, double depart, double longueur, double epaisseur, Color c) {
        double a = Math.toRadians(angleDeg);
        Line l = new Line(cx + Math.cos(a) * depart, cy - Math.sin(a) * depart,
                cx + Math.cos(a) * (depart + longueur), cy - Math.sin(a) * (depart + longueur));
        l.setStroke(c);
        l.setStrokeWidth(epaisseur);
        l.setStrokeLineCap(StrokeLineCap.ROUND);
        eclaboussures.getChildren().add(l);
    }

    // ── éclats du site ────────────────────────────────────────────────────────────────────────────────
    private void poserEclats(List<Eclat> motif, double l, double h) {
        double em = taille;
        double padD = 0.35 * em, padH = 0.12 * em;   // .titre { padding: 0.12em 0.35em 0 0 }
        double boiteL = l + padD, boiteH = h + padH;
        for (Eclat e : motif) {
            double w = e.l() * em, hh = e.h() * em;
            double x = "left".equals(e.cx()) ? e.vx() * em : boiteL - e.vx() * em - w;
            double y = "top".equals(e.cy()) ? e.vy() * em - padH : boiteH - e.vy() * em - hh - padH;
            javafx.scene.shape.Shape f;
            if ("point".equals(e.forme())) {
                f = new javafx.scene.shape.Ellipse(x + w / 2, y + hh / 2, w / 2, hh / 2);
            } else {
                double[] pts = FORMES.get(e.forme());
                Polygon poly = new Polygon();
                for (int i = 0; i < pts.length; i += 2) poly.getPoints().addAll(x + pts[i] / 100 * w, y + pts[i + 1] / 100 * hh);
                f = poly;
            }
            if (e.couleur().contains(">")) {
                String[] c = e.couleur().split(">");
                f.setFill(Jetons.degradeCss(135, c[0], c[1]));
            } else {
                f.setFill(Color.web(e.couleur()));
            }
            if (e.rotation() != 0) f.setRotate(e.rotation());
            eclats.getChildren().add(f);
        }
    }

    /** Fait naître les éclats (échelle 0 → 1,35 → 1 en 300 ms, comme l'entrée active du menu du site). */
    public void naitre() {
        eclats.getChildren().forEach(n -> {
            n.setScaleX(0);
            n.setScaleY(0);
            new Timeline(new KeyFrame(Duration.millis(150), new KeyValue(n.scaleXProperty(), 1.35), new KeyValue(n.scaleYProperty(), 1.35)),
                    new KeyFrame(Duration.millis(300), new KeyValue(n.scaleXProperty(), 1), new KeyValue(n.scaleYProperty(), 1))).play();
        });
    }

    public Style getStyleTitre() { return style; }
}
