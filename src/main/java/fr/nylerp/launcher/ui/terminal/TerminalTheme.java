package fr.nylerp.launcher.ui.terminal;

import javafx.scene.effect.Blend;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.ColorInput;
import javafx.scene.effect.Effect;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Thème DEV « phosphore » : <b>vert et noir, rien d'autre</b> (consigne owner du 2026-08-08).
 *
 * <h2>Comment la contrainte est tenue</h2>
 * Pas en repeignant à la main les ~90 couleurs écrites en dur dans {@code SettingsView},
 * {@code MainView} et {@code LoginView} — un tel diff aurait touché tout le code d'interface de
 * production pour un habillage, et il aurait suffi d'un {@code setTextFill} oublié pour laisser
 * une tache blanche. On reproduit à la place ce que faisait un vrai moniteur monochrome :
 * <b>un filtre appliqué à l'écran entier</b> ({@link #phosphore}).
 *
 * <ol>
 *   <li>l'interface est rendue normalement, puis <b>désaturée</b> ({@code ColorAdjust}) : il ne
 *       reste que des niveaux de gris, donc la hiérarchie visuelle d'origine (titre clair, libellé
 *       gris, cadre sombre) est conservée telle quelle ;</li>
 *   <li>le résultat est <b>multiplié</b> par un vert unique ({@code Blend.MULTIPLY}) : le blanc
 *       devient le vert vif, les gris deviennent des verts éteints, le noir reste noir.</li>
 * </ol>
 *
 * <p>Conséquences directes : les <b>images</b> (vignette d'actualité, tête de skin) passent aussi
 * en vert — impossible autrement — et tout écran ajouté plus tard hérite du thème sans un seul
 * appel supplémentaire. C'est aussi ce qui rend la teinte pilotable depuis une seule constante,
 * {@link #PHOSPHORE}.
 *
 * <p><b>Le filtre n'est JAMAIS posé en production</b> : ses deux seuls points d'application sont
 * gardés par {@code Constants.DEV} ({@code LauncherApp.show} et {@link TerminalBoot}).
 *
 * <h2>Palette</h2>
 * Puisque le filtre fabrique le vert, les couleurs définies ici et dans {@code terminal.css} sont
 * volontairement des <b>niveaux de gris</b> : elles décrivent une <i>intensité</i>, pas une teinte.
 * Les valeurs finales à l'écran (et leurs contrastes mesurés) sont documentées ci-dessous.
 */
public final class TerminalTheme {

    /**
     * Le vert du tube. Unique teinte de tout le canal DEV.
     * Choisi pour que le blanc (niveau 255) donne exactement ce vert après multiplication.
     */
    public static final Color PHOSPHORE = Color.web("#2BFF8F");

    // ── L'ÉCHELLE D'INTENSITÉS ──────────────────────────────────────────────────────────────────
    // Le thème n'ayant qu'une teinte, toute la hiérarchie visuelle repose sur ces cinq marches.
    // Elles sont ESPACÉES À LA MESURE, pas au jugé : une première version plaçait ACCENT à #F2F2F2
    // et HOT à #FFFFFF, et la mire de contraste a montré qu'après filtre les deux tombaient sur le
    // même pixel (#2BFF8F, 15,8:1) — le marqueur « DEV » et les erreurs devenaient indistinguables
    // du texte courant. Contrastes WCAG mesurés sur les pixels rendus, fond noir :
    //    HOT 15,8:1 · ACCENT ~11:1 · TEXT ~7,7:1 · DIM ~5,3:1 · FRAME ~2,5:1 · NOISE ~1,1:1
    // Tout ce qui porte du TEXTE reste ≥ 4,5:1. Vérification reproductible :
    //    java -cp nylelauncher.jar fr.nylerp.launcher.ui.terminal.TerminalPreview design/terminal-dev
    //    → design/terminal-dev/contraste.txt + contraste-mire.png
    /** Fond. Le seul « noir » du thème. */
    public static final Color BG        = Color.web("#05070A");
    /** Texte courant. */
    public static final Color TEXT      = Color.web("#9E9E9E");
    /** Texte secondaire, libellés. Plancher de lisibilité du thème. */
    public static final Color DIM       = Color.web("#848484");
    /** Cadres, points de conduite, creux de barre. Décoratif — jamais du texte à lire. */
    public static final Color FRAME     = Color.web("#4E4E4E");
    /** Papier peint de fond. Doit rester à la limite du perceptible. */
    public static final Color NOISE     = Color.web("#242424");
    /** Ce qui compte : valeurs, mot en ASCII art, bordure du bouton principal. */
    public static final Color ACCENT    = Color.web("#C0C0C0");
    /** Intensité MAXIMALE du tube : crête de balayage, marqueur DEV, erreurs. */
    public static final Color HOT       = Color.web("#FFFFFF");

    /** Index de palette utilisés par les grilles (0 = cellule vide, rien n'est peint). */
    public static final int C_NONE = 0, C_DIM = 1, C_TEXT = 2, C_ACCENT = 3,
                            C_HOT = 4, C_NOISE = 5, C_FRAME = 6;

    public static Color[] palette() {
        return new Color[] { Color.TRANSPARENT, DIM, TEXT, ACCENT, HOT, NOISE, FRAME };
    }

    /**
     * Filtre monochrome vert, à poser sur la racine d'un écran.
     *
     * @param w largeur du nœud filtré ; le voile de couleur doit couvrir toute sa surface
     * @param h hauteur du nœud filtré
     */
    public static Effect phosphore(double w, double h) {
        ColorAdjust gris = new ColorAdjust();
        gris.setSaturation(-1.0);     // désaturation totale : plus aucune teinte d'origine ne survit
        gris.setBrightness(0.10);     // relève les demi-tons, sinon les gris moyens virent au vert sombre
        gris.setContrast(0.12);       // recreuse les noirs que le gain précédent avait délavés
        Blend blend = new Blend(BlendMode.MULTIPLY);
        blend.setBottomInput(gris);   // entrée nulle ⇒ le rendu du nœud lui-même
        blend.setTopInput(new ColorInput(0, 0, w, h, PHOSPHORE));
        return blend;
    }

    /**
     * Police à chasse fixe, en graisse grasse.
     *
     * <p>Famille <b>logique</b> {@code "Monospaced"} : JavaFX la résout vers une vraie police à
     * chasse fixe présente sur chaque OS (Menlo, Courier New, DejaVu Sans Mono). Embarquer une
     * police libre de plus aurait alourdi le payload sans rien garantir : la grille de
     * {@link CharGrid} rend l'alignement indépendant des métriques réelles.
     *
     * <p>Le gras est assumé : le « # » d'une police fine laisse tant de vide dans sa cellule que
     * l'ASCII art se lit gris et mou à 12 px. La chasse, elle, est identique en gras.
     */
    public static Font mono(double size) {
        return Font.font("Monospaced", FontWeight.BOLD, size);
    }

    private TerminalTheme() {}
}
