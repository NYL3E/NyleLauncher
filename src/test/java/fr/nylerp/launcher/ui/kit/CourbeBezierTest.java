package fr.nylerp.launcher.ui.kit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Les courbes CSS du site doivent donner les mêmes valeurs que les navigateurs. */
class CourbeBezierTest {

    private static double v(CourbeBezier c, double t) { return c.interpolate(0.0, 1.0, t); }

    @Test
    void ease_du_navigateur() {
        // cubic-bezier(0.25, 0.1, 0.25, 1) = « ease » ; valeur de référence à mi-parcours : 0,8024
        CourbeBezier ease = new CourbeBezier(0.25, 0.1, 0.25, 1);
        assertEquals(0.8024, v(ease, 0.5), 1e-3);
        assertEquals(0, v(ease, 0), 1e-9);
        assertEquals(1, v(ease, 1), 1e-9);
    }

    @Test
    void lineaire() {
        CourbeBezier lin = new CourbeBezier(0, 0, 1, 1);
        for (double t = 0; t <= 1; t += 0.1) assertEquals(t, v(lin, t), 1e-4);
    }

    @Test
    void le_rebond_des_survols_depasse_puis_revient() {
        // cubic-bezier(.3, 1.6, .5, 1) : dépasse 1 en cours de route (ce que SPLINE de JavaFX refuse)
        double max = 0;
        for (double t = 0; t <= 1; t += 0.01) max = Math.max(max, v((CourbeBezier) Jetons.REBOND, t));
        assertTrue(max > 1.05, "le rebond doit dépasser l'arrivée, max = " + max);
        assertEquals(1, v((CourbeBezier) Jetons.REBOND, 1), 1e-9);
    }

    @Test
    void x_hors_bornes_refuse() {
        assertThrows(IllegalArgumentException.class, () -> new CourbeBezier(-0.1, 0, 0.5, 1));
    }
}
