package fr.nylerp.launcher.ui.kit;

import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.effect.InnerShadow;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.ImagePattern;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;

/**
 * Traduction en JavaFX des procédés CSS du site, partagée par les composants du kit :
 * <ul>
 *   <li>{@link #ombre} : un {@code box-shadow} extérieur (décalage, flou, étalement éventuellement négatif) = une forme
 *       floutée posée DERRIÈRE l'élément (JavaFX n'accepte qu'un effet par nœud et pas d'étalement négatif) ;</li>
 *   <li>{@link #ombreInterne} : un {@code box-shadow: inset …} ;</li>
 *   <li>{@link #ellipse} : un {@code radial-gradient(Rx Ry at X Y, …)} elliptique (le radial proportionnel de JavaFX
 *       s'étire sur la boîte de la région : une région de 2Rx × 2Ry donne exactement l'ellipse voulue) ;</li>
 *   <li>{@link #trame} : la trame de points {@code radial-gradient(… ) 0 0 / pas pas}.</li>
 * </ul>
 * Correspondance des flous : CSS {@code blur} B → écart-type B/2 ; {@code GaussianBlur} de rayon r → écart-type ≈ r/3,
 * donc r = 1,5 × B (plafonné à 63 par JavaFX).
 */
public final class Calques {

    /** Rayon de {@link GaussianBlur} équivalent à un flou CSS. */
    public static double flou(double flouCss) { return Math.min(63, Math.max(0, flouCss * 1.5)); }

    /**
     * Un {@code box-shadow: ox oy flou etalement couleur} d'une forme arrondie de largeur l, hauteur h, rayon r.
     * Le nœud rendu est positionné dans le repère de l'élément (à poser dans le même conteneur, juste avant lui).
     */
    public static Node ombre(double l, double h, double rayon, double ox, double oy, double flouCss, double etalement, Color couleur) {
        double w = Math.max(0, l + 2 * etalement), hh = Math.max(0, h + 2 * etalement);
        double r = Math.max(0, Math.min(Math.min(w, hh) / 2, rayon + etalement));
        Rectangle f = new Rectangle(w, hh);
        f.setArcWidth(2 * r);
        f.setArcHeight(2 * r);
        f.setFill(couleur);
        f.setLayoutX(ox - etalement);
        f.setLayoutY(oy - etalement);
        f.setManaged(false);
        f.setMouseTransparent(true);
        if (flouCss > 0) f.setEffect(new GaussianBlur(flou(flouCss)));
        f.setCache(true);   // forme floutée immobile : rendue une fois
        return f;
    }

    /** Un {@code box-shadow: inset ox oy flou couleur}. */
    public static InnerShadow ombreInterne(double ox, double oy, double flouCss, Color couleur) {
        InnerShadow s = new InnerShadow(BlurType.GAUSSIAN, couleur, flou(flouCss), 0, ox, oy);
        return s;
    }

    /**
     * Une tache {@code radial-gradient(rx ry at cx cy, couleur, transparent fin%)} : région de 2rx × 2ry centrée en
     * (cx, cy), à poser dans un conteneur détouré à la forme de l'élément.
     */
    public static Region ellipse(double cx, double cy, double rx, double ry, Color couleur, double fin) {
        return ellipse(cx, cy, rx, ry, new Stop(0, couleur), new Stop(fin, transparent(couleur)));
    }

    public static Region ellipse(double cx, double cy, double rx, double ry, Stop... arrets) {
        Region e = new Region();
        e.setManaged(false);
        e.setMouseTransparent(true);
        e.resizeRelocate(cx - rx, cy - ry, 2 * rx, 2 * ry);
        e.setBackground(fond(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE, arrets)));
        return e;
    }

    /** La même couleur, opacité nulle (les dégradés CSS vers « transparent » restent dans la teinte). */
    public static Color transparent(Color c) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), 0); }

    public static Color rgba(int r, int g, int b, double a) { return Color.rgb(r, g, b, a); }

    public static Background fond(Paint p) { return new Background(new BackgroundFill(p, null, null)); }

    /**
     * Trame de points : un point de rayon {@code rayon} tous les {@code pas} pixels (dessinée à 4× pour rester nette sur
     * les écrans Retina).
     */
    public static ImagePattern trame(double pas, double rayon, Color couleur) {
        int k = 4, cote = Math.max(2, (int) Math.round(pas * k));
        Canvas c = new Canvas(cote, cote);
        GraphicsContext g = c.getGraphicsContext2D();
        g.setFill(new RadialGradient(0, 0, cote / 2.0, cote / 2.0, rayon * k, false, CycleMethod.NO_CYCLE,
                new Stop(0, couleur), new Stop(0.99, couleur), new Stop(1, transparent(couleur))));
        g.fillOval(cote / 2.0 - rayon * k, cote / 2.0 - rayon * k, 2 * rayon * k, 2 * rayon * k);
        SnapshotParameters p = new SnapshotParameters();
        p.setFill(Color.TRANSPARENT);
        WritableImage img = c.snapshot(p, null);
        return new ImagePattern(img, 0, 0, pas, pas, false);
    }

    /**
     * Un texte avec interlettrage ({@code letter-spacing} du site, en em) : JavaFX n'en a pas, on pose chaque lettre à
     * son avance réelle (mesurée par paire pour garder le crénage) plus l'espacement.
     */
    public static javafx.scene.Group texteEspace(String s, javafx.scene.text.Font font, double espacementEm, Paint couleur) {
        javafx.scene.Group g = new javafx.scene.Group();
        double x = 0, esp = espacementEm * font.getSize();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i), n = Character.charCount(cp);
            String c = s.substring(i, i + n);
            javafx.scene.text.Text t = new javafx.scene.text.Text(c);
            t.setFont(font);
            t.setFill(couleur);
            t.setTextOrigin(javafx.geometry.VPos.TOP);
            t.setX(x);
            g.getChildren().add(t);
            String suite = i + n < s.length() ? s.substring(i + n, i + n + Character.charCount(s.codePointAt(i + n))) : "";
            x += largeur(c + suite, font) - largeur(suite, font) + esp;
            i += n;
        }
        g.setMouseTransparent(true);
        return g;
    }

    private static double largeur(String s, javafx.scene.text.Font f) {
        if (s.isEmpty()) return 0;
        javafx.scene.text.Text t = new javafx.scene.text.Text(s);
        t.setFont(f);
        return t.getLayoutBounds().getWidth();
    }

    private Calques() {}
}
