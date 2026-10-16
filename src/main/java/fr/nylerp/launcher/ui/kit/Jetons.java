package fr.nylerp.launcher.ui.kit;

import javafx.animation.Interpolator;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

/**
 * Jetons du design du site Pokényle (src/css/base.css de ~/sites/pokenyle-v2), repris tels quels pour le launcher.
 *
 * <p>Échelle : le site mesure tout en rem, 1 rem = 19,2 px à 1440 px de large. Ici {@link #rem(double)} rend la même
 * mesure en pixels JavaFX ; {@link #setEchelle(double)} réduit ou agrandit tout le kit d'un coup (1 = taille du site à
 * 1440, la référence des captures ; la coquille du launcher choisit la sienne AVANT de construire ses écrans).
 */
public final class Jetons {

    /** 1 rem du site à 1440 px de large. */
    public static final double REM_SITE = 19.2;
    private static double echelle = 1.0;

    /** Fixe l'échelle de tout le kit (à appeler avant de construire les composants). */
    public static void setEchelle(double e) { echelle = e > 0 ? e : 1.0; }
    public static double getEchelle() { return echelle; }

    /** Une mesure du site en rem → pixels JavaFX à l'échelle courante. */
    public static double rem(double r) { return r * REM_SITE * echelle; }

    // ── couleurs (:root du site) ────────────────────────────────────────────────────────────────────
    public static final Color ENCRE        = Color.web("#30384a");
    public static final Color ENCRE_2      = Color.web("#4d6b84");
    public static final Color ENCRE_3      = Color.web("#587a9a");
    public static final Color ACTIF        = Color.web("#10a7e6");
    public static final Color ACTIF_SOMBRE = Color.web("#73dbff");
    public static final Color CIEL         = Color.web("#eaf7ff");
    public static final Color CIEL_2       = Color.web("#dff4ff");
    public static final Color ARDOISE      = Color.web("#3d3d50");
    /** Texte des onglets choisis (ardoise + bleu ciel). */
    public static final Color ONGLET_CHOISI = Color.web("#a5e1ff");
    /** Icônes de la barre du site (#5879b3 → actif au survol). */
    public static final Color ICONE        = Color.web("#5879b3");
    /** Anneau de focus du site. */
    public static final Color FOCUS        = Color.web("#4c8ff0");

    /** --nacre : linear-gradient(100deg, …) — 100° CSS ≈ de gauche à droite, légèrement descendant. */
    public static LinearGradient nacre() {
        return degradeCss(100, "#c1f1f0", "#dbddf1", "#a6dff8", "#c9e3fc", "#dfe2ff", "#e2f8e3", "#f3d4f3");
    }

    /** Le liseré irisé des pilules : linear-gradient(100deg, #f7c7ec, #c9e3fc 28%, #a6dff8 50%, #dfe2ff 70%, #f3d4f3). */
    public static LinearGradient lisereIrise() {
        return degradeCss(100, new Stop(0, Color.web("#f7c7ec")), new Stop(0.28, Color.web("#c9e3fc")),
                new Stop(0.50, Color.web("#a6dff8")), new Stop(0.70, Color.web("#dfe2ff")), new Stop(1, Color.web("#f3d4f3")));
    }

    /** Texte des pilules « launcher » : linear-gradient(180deg, #5980c3, #79b2da). */
    public static LinearGradient texteLauncher() { return vertical("#5980c3", "#79b2da"); }
    /** Texte des pilules « Discord » : linear-gradient(180deg, #6577e8, #8aa6ec). */
    public static LinearGradient texteDiscord() { return vertical("#6577e8", "#8aa6ec"); }

    public static LinearGradient vertical(String haut, String bas) {
        return new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE, new Stop(0, Color.web(haut)), new Stop(1, Color.web(bas)));
    }

    /** Dégradé CSS « linear-gradient(Ndeg, couleurs régulièrement espacées) » en coordonnées proportionnelles. */
    public static LinearGradient degradeCss(double angleDeg, String... couleurs) {
        Stop[] s = new Stop[couleurs.length];
        for (int i = 0; i < couleurs.length; i++) s[i] = new Stop(couleurs.length == 1 ? 0 : (double) i / (couleurs.length - 1), Color.web(couleurs[i]));
        return degradeCss(angleDeg, s);
    }

    /** Dégradé CSS orienté : 0° = vers le haut, 90° = vers la droite, 180° = vers le bas (boîte carrée normalisée). */
    public static LinearGradient degradeCss(double angleDeg, Stop... stops) {
        double a = Math.toRadians(angleDeg);
        double dx = Math.sin(a), dy = -Math.cos(a);
        // segment centré sur (0,5 ; 0,5), demi-longueur projetée pour couvrir les coins (comme CSS)
        double demi = (Math.abs(dx) + Math.abs(dy)) / 2;
        return new LinearGradient(0.5 - dx * demi, 0.5 - dy * demi, 0.5 + dx * demi, 0.5 + dy * demi, true, CycleMethod.NO_CYCLE, stops);
    }

    // ── courbes du site ─────────────────────────────────────────────────────────────────────────────
    /** --sortie : cubic-bezier(0.22, 1, 0.36, 1) — entrées, glissements. */
    public static final Interpolator SORTIE = new CourbeBezier(0.22, 1, 0.36, 1);
    /** cubic-bezier(.3, 1.6, .5, 1) — le petit rebond des survols (soulèvement des boutons). */
    public static final Interpolator REBOND = new CourbeBezier(0.3, 1.6, 0.5, 1);
    /** cubic-bezier(.3, .6, .3, 1) — le reflet qui balaie une pilule. */
    public static final Interpolator BALAYE = new CourbeBezier(0.3, 0.6, 0.3, 1);
    /** cubic-bezier(.5, 0, .3, 1) — la flèche qui tombe dans le bac (icône launcher). */
    public static final Interpolator TOMBE  = new CourbeBezier(0.5, 0, 0.3, 1);
    /** cubic-bezier(.3, .7, .3, 1) — l'anneau qui se remplit, la bascule du logo. */
    public static final Interpolator DOUX   = new CourbeBezier(0.3, 0.7, 0.3, 1);

    private Jetons() {}
}
