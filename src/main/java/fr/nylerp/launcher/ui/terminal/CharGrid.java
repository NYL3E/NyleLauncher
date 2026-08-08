package fr.nylerp.launcher.ui.terminal;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * Grille de caractères — le cœur du thème DEV : tout l'écran d'accueil est une matrice
 * {@code cols × rows} de cellules (caractère + index de couleur), redessinée sur un {@code Canvas}.
 *
 * <p><b>Pourquoi une grille et pas des Label/Text JavaFX</b> :
 * <ul>
 *   <li><b>Alignement garanti</b> — chaque cellule est peinte à son abscisse propre
 *       ({@code col × cellW}). Même si la police résolue par la famille logique
 *       {@code "Monospaced"} n'était pas parfaitement à chasse fixe sur un poste donné, l'ASCII art
 *       resterait droit. Aucune dépendance à une police embarquée (le launcher n'embarque que des
 *       Montserrat, cf. {@link AsciiFont}).</li>
 *   <li><b>Coût maîtrisé</b> — un seul nœud dans le graphe de scène, donc pas de layout JavaFX à
 *       chaque image. Le dessin regroupe les cellules contiguës de même couleur en un seul
 *       {@code fillText} (cf. {@link #paint}), ce qui ramène un écran typique de ~5 000 cellules à
 *       quelques centaines d'appels par image.</li>
 * </ul>
 *
 * <p>La grille est un pur tampon : elle ne connaît ni le temps ni l'animation. Les scènes la
 * remplissent puis appellent {@link #paint}, ce qui rend le rendu <b>déterministe</b> et donc
 * capturable image par image par l'outil de prévisualisation.
 */
public final class CharGrid {

    /** Couleurs adressées par index depuis les cellules (0 = vide/transparent). */
    private final Color[] palette;

    public final int cols;
    public final int rows;
    public final double cellW;
    public final double cellH;
    private final Font font;

    private final char[] chars;
    private final byte[] colors;

    /**
     * @param font    police à chasse fixe déjà résolue (voir {@link TerminalTheme#mono(double)})
     * @param cols    largeur en cellules
     * @param rows    hauteur en cellules
     * @param palette palette indexée ; l'index 0 doit rester « rien à dessiner »
     */
    public CharGrid(Font font, int cols, int rows, Color[] palette) {
        this.font = font;
        this.cols = Math.max(1, cols);
        this.rows = Math.max(1, rows);
        this.palette = palette;
        this.chars = new char[this.cols * this.rows];
        this.colors = new byte[this.cols * this.rows];
        this.cellW = advanceOf(font);
        this.cellH = Math.ceil(font.getSize() * 1.30);
        clear();
    }

    /** Avance horizontale d'un caractère, mesurée sur la police réellement résolue. */
    public static double advanceOf(Font font) {
        Text probe = new Text("MMMMMMMMMM");
        probe.setFont(font);
        return probe.getLayoutBounds().getWidth() / 10.0;
    }

    public void clear() {
        java.util.Arrays.fill(chars, ' ');
        java.util.Arrays.fill(colors, (byte) 0);
    }

    public void put(int col, int row, char c, int colorIdx) {
        if (col < 0 || col >= cols || row < 0 || row >= rows) return;
        int i = row * cols + col;
        chars[i] = c;
        colors[i] = (byte) colorIdx;
    }

    /** Écrit une chaîne à partir de (col,row). Les caractères hors grille sont ignorés. */
    public void write(int col, int row, String s, int colorIdx) {
        for (int i = 0; i < s.length(); i++) put(col + i, row, s.charAt(i), colorIdx);
    }

    /** Écrit les {@code n} premiers caractères — utilisé par l'effet « frappe au clavier ». */
    public void writeTyped(int col, int row, String s, int n, int colorIdx) {
        int k = Math.max(0, Math.min(n, s.length()));
        write(col, row, s.substring(0, k), colorIdx);
    }

    /**
     * Peint la grille sur le contexte, à l'origine (x0, y0).
     *
     * <p>Optimisation clef : on regroupe les suites horizontales de cellules non vides et de même
     * couleur en un seul {@code fillText}. Sans ça, un écran plein tournerait à ~5 000 appels de
     * dessin par image, ce qui se voit au ventilateur sur un portable — exactement ce qu'un écran
     * d'attente ne doit pas faire.
     */
    public void paint(GraphicsContext g, double x0, double y0) {
        g.setFont(font);
        g.setTextBaseline(VPos.TOP);
        StringBuilder run = new StringBuilder();
        for (int r = 0; r < rows; r++) {
            int runStart = -1;
            byte runColor = 0;
            for (int c = 0; c <= cols; c++) {
                int i = r * cols + c;
                boolean drawable = c < cols && colors[i] != 0 && chars[i] != ' ';
                byte col = drawable ? colors[i] : 0;
                if (runStart >= 0 && (!drawable || col != runColor)) {
                    g.setFill(palette[runColor]);
                    g.fillText(run.toString(), x0 + runStart * cellW, y0 + r * cellH);
                    run.setLength(0);
                    runStart = -1;
                }
                if (drawable) {
                    if (runStart < 0) { runStart = c; runColor = col; }
                    run.append(chars[i]);
                }
            }
        }
    }
}
