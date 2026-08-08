package fr.nylerp.launcher.ui.terminal;

import java.util.HashMap;
import java.util.Map;

/**
 * Fonte ASCII « bloc » maison, 7 colonnes × 7 lignes par lettre, dessinée uniquement avec
 * {@code '#'} et l'espace.
 *
 * <p><b>Pourquoi une fonte maison plutôt qu'un figlet copié</b> — le launcher n'embarque que des
 * Montserrat (aucune police à chasse fixe). L'ASCII art est donc rendu avec la famille
 * <i>logique</i> JavaFX {@code "Monospaced"}, qui existe sur les trois OS mais dont le dessin varie
 * (Menlo / Courier New / DejaVu Sans Mono). Deux conséquences ont dicté ce choix :
 * <ul>
 *   <li>on n'utilise que des caractères <b>ASCII 7 bits</b> ({@code '#'}) — pas de demi-blocs ni de
 *       box-drawing Unicode, qui manquent ou sont mal chassés dans certaines polices Windows ;</li>
 *   <li>chaque lettre fait <b>exactement</b> 7 colonnes, donc le rendu se fait sur une grille de
 *       cellules dont la largeur est mesurée à l'exécution ({@link CharGrid}) : même si la police
 *       résolue n'était pas parfaitement à chasse fixe, l'art resterait aligné.</li>
 * </ul>
 *
 * <p>Seules les lettres de « NYLELAUNCHER DEV » sont définies : c'est le seul texte que le canal DEV
 * affiche en grand. {@link #render(String)} lève une exception si on lui demande un caractère absent
 * — un banner tronqué en silence serait passé inaperçu jusqu'en production DEV.
 */
public final class AsciiFont {

    /** Largeur d'une lettre, en cellules. */
    public static final int GLYPH_W = 7;
    /** Hauteur d'une lettre, en cellules. */
    public static final int GLYPH_H = 7;
    /** Colonnes vides insérées entre deux lettres. */
    public static final int TRACKING = 1;

    private static final Map<Character, String[]> GLYPHS = new HashMap<>();

    static {
        put('N', "##   ##",
                 "###  ##",
                 "#### ##",
                 "## # ##",
                 "## ####",
                 "##  ###",
                 "##   ##");
        put('Y', "##   ##",
                 "##   ##",
                 " ## ## ",
                 "  ###  ",
                 "  ###  ",
                 "  ###  ",
                 "  ###  ");
        put('L', "##     ",
                 "##     ",
                 "##     ",
                 "##     ",
                 "##     ",
                 "##     ",
                 "#######");
        put('E', "#######",
                 "##     ",
                 "##     ",
                 "#####  ",
                 "##     ",
                 "##     ",
                 "#######");
        put('A', " ##### ",
                 "##   ##",
                 "##   ##",
                 "#######",
                 "##   ##",
                 "##   ##",
                 "##   ##");
        put('U', "##   ##",
                 "##   ##",
                 "##   ##",
                 "##   ##",
                 "##   ##",
                 "##   ##",
                 " ##### ");
        put('C', " ##### ",
                 "##   ##",
                 "##     ",
                 "##     ",
                 "##     ",
                 "##   ##",
                 " ##### ");
        put('H', "##   ##",
                 "##   ##",
                 "##   ##",
                 "#######",
                 "##   ##",
                 "##   ##",
                 "##   ##");
        put('R', "###### ",
                 "##   ##",
                 "##   ##",
                 "###### ",
                 "##  ## ",
                 "##   ##",
                 "##   ##");
        put('D', "###### ",
                 "##   ##",
                 "##   ##",
                 "##   ##",
                 "##   ##",
                 "##   ##",
                 "###### ");
        put('V', "##   ##",
                 "##   ##",
                 "##   ##",
                 "##   ##",
                 " ## ## ",
                 " ## ## ",
                 "  ###  ");
        put(' ', "       ",
                 "       ",
                 "       ",
                 "       ",
                 "       ",
                 "       ",
                 "       ");
    }

    private static void put(char c, String... rows) {
        if (rows.length != GLYPH_H) throw new IllegalStateException("glyphe " + c + " : hauteur ≠ " + GLYPH_H);
        for (String r : rows) {
            if (r.length() != GLYPH_W) throw new IllegalStateException("glyphe " + c + " : largeur ≠ " + GLYPH_W);
        }
        GLYPHS.put(c, rows);
    }

    /** Largeur en cellules du mot rendu (lettres + interlettrage, sans marge finale). */
    public static int width(String text) {
        if (text.isEmpty()) return 0;
        return text.length() * (GLYPH_W + TRACKING) - TRACKING;
    }

    /**
     * Compose le texte en {@value #GLYPH_H} lignes ASCII.
     *
     * @throws IllegalArgumentException si une lettre n'est pas dans la fonte — mieux vaut un échec
     *         bruyant au démarrage qu'un banner amputé que personne ne remarque.
     */
    public static String[] render(String text) {
        String up = text.toUpperCase(java.util.Locale.ROOT);
        StringBuilder[] out = new StringBuilder[GLYPH_H];
        for (int i = 0; i < GLYPH_H; i++) out[i] = new StringBuilder();
        for (int i = 0; i < up.length(); i++) {
            char c = up.charAt(i);
            String[] g = GLYPHS.get(c);
            if (g == null) throw new IllegalArgumentException("AsciiFont : lettre non définie « " + c + " »");
            for (int r = 0; r < GLYPH_H; r++) {
                if (i > 0) out[r].append(" ".repeat(TRACKING));
                out[r].append(g[r]);
            }
        }
        String[] rows = new String[GLYPH_H];
        for (int i = 0; i < GLYPH_H; i++) rows[i] = out[i].toString();
        return rows;
    }

    private AsciiFont() {}
}
