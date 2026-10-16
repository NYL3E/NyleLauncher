package fr.nylerp.launcher.ui.kit;

import javafx.animation.Interpolator;

/**
 * Une courbe CSS {@code cubic-bezier(x1, y1, x2, y2)} quelconque. {@link Interpolator#SPLINE} refuse les points de
 * contrôle hors de [0, 1] ; or les survols du site rebondissent ({@code cubic-bezier(.3, 1.6, .5, 1)} dépasse 1).
 * Même calcul que les navigateurs : on retrouve t tel que x(t) = progression (Newton, repli par dichotomie), puis y(t).
 */
public final class CourbeBezier extends Interpolator {

    private final double x1, y1, x2, y2;

    public CourbeBezier(double x1, double y1, double x2, double y2) {
        if (x1 < 0 || x1 > 1 || x2 < 0 || x2 > 1) throw new IllegalArgumentException("x hors de [0, 1]");
        this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
    }

    private static double bezier(double t, double p1, double p2) {
        double u = 1 - t;
        return 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t;
    }

    private static double derivee(double t, double p1, double p2) {
        double u = 1 - t;
        return 3 * u * u * p1 + 6 * u * t * (p2 - p1) + 3 * t * t * (1 - p2);
    }

    private double tPourX(double x) {
        double t = x;
        for (int i = 0; i < 8; i++) {
            double ecart = bezier(t, x1, x2) - x;
            if (Math.abs(ecart) < 1e-6) return t;
            double d = derivee(t, x1, x2);
            if (Math.abs(d) < 1e-6) break;
            t -= ecart / d;
        }
        double bas = 0, haut = 1;
        t = x;
        for (int i = 0; i < 40; i++) {
            double v = bezier(t, x1, x2);
            if (Math.abs(v - x) < 1e-6) break;
            if (v < x) bas = t; else haut = t;
            t = (bas + haut) / 2;
        }
        return t;
    }

    @Override
    protected double curve(double progression) {
        if (progression <= 0) return 0;
        if (progression >= 1) return 1;
        return bezier(tPourX(progression), y1, y2);
    }

    @Override
    public String toString() { return "cubic-bezier(" + x1 + ", " + y1 + ", " + x2 + ", " + y2 + ")"; }
}
