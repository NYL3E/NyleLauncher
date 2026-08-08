package fr.nylerp.launcher.ui.terminal;

import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

/**
 * Habillage terminal de l'écran principal, en canal DEV uniquement.
 *
 * <p>Fournit trois briques posées par {@code MainView} sous garde {@code Constants.DEV} :
 * <ul>
 *   <li>{@link #fondTerminal()} — le fond, qui <b>remplace les deux vidéos H.264</b> ;</li>
 *   <li>{@link #marqueAscii(String, double)} — le logo, en ASCII art ;</li>
 *   <li>{@link #blocInfos(Label)} — l'état réel de la session, présenté comme une sortie de
 *       commande.</li>
 * </ul>
 *
 * <p><b>Le fond est le gain processeur du chantier.</b> En production l'arrière-plan est une
 * vidéo H.264 en boucle infinie (plus une seconde pré-décodée en pause) : c'est du décodage vidéo
 * permanent pendant tout le temps où le launcher est ouvert. Ici le fond est peint <b>une seule
 * fois</b> sur un {@code Canvas} et ne bouge plus jamais. Un écran d'attente qui fait tourner le
 * ventilateur avant même de lancer le jeu, c'est raté — le thème DEV consomme donc, au repos,
 * strictement moins que la production.
 */
public final class TerminalSkin {

    /** Densité du papier peint : proportion de cellules portant un caractère. */
    private static final double DENSITE = 0.16;

    /**
     * Fond fixe : dégradé sombre, papier peint de caractères à peine visible, lignes de balayage.
     * Repeint uniquement quand la taille change (donc une fois : la fenêtre n'est pas
     * redimensionnable).
     */
    public static Pane fondTerminal() {
        Canvas canvas = new Canvas();
        Pane pane = new Pane(canvas) {
            @Override protected void layoutChildren() {
                double w = getWidth(), h = getHeight();
                if (w <= 0 || h <= 0) return;
                if (canvas.getWidth() == w && canvas.getHeight() == h) return;
                canvas.setWidth(w);
                canvas.setHeight(h);
                peindreFond(canvas.getGraphicsContext2D(), w, h);
            }
        };
        pane.setMaxWidth(Double.MAX_VALUE);
        pane.setMaxHeight(Double.MAX_VALUE);
        return pane;
    }

    private static void peindreFond(GraphicsContext g, double w, double h) {
        g.setFill(TerminalTheme.BG);
        g.fillRect(0, 0, w, h);

        // Halo du tube en haut à gauche — donne du relief sans rien animer. En blanc : c'est le
        // filtre phosphore (cf. TerminalTheme) qui le teinte, comme tout le reste de l'écran.
        g.setFill(new RadialGradient(0, 0, 0.22, 0.10, 0.85, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(1, 1, 1, 0.075)),
                new Stop(1, Color.TRANSPARENT)));
        g.fillRect(0, 0, w, h);

        // Papier peint : caractères aléatoires figés (graine constante ⇒ même fond à chaque
        // ouverture, donc pas d'effet « ça bouge » involontaire entre deux navigations).
        javafx.scene.text.Font f = TerminalTheme.mono(12);
        double cw = CharGrid.advanceOf(f), ch = 16;
        g.setFont(f);
        g.setTextBaseline(javafx.geometry.VPos.TOP);
        g.setFill(Color.color(TerminalTheme.NOISE.getRed(), TerminalTheme.NOISE.getGreen(),
                              TerminalTheme.NOISE.getBlue(), 0.22));
        java.util.Random rnd = new java.util.Random(20260808L);
        final String alphabet = "01#$%&/\\|<>*+=_-[]{}abcdef";
        StringBuilder ligne = new StringBuilder();
        for (double y = 0; y < h; y += ch) {
            ligne.setLength(0);
            for (int c = 0; c * cw < w; c++) {
                ligne.append(rnd.nextDouble() < DENSITE ? alphabet.charAt(rnd.nextInt(alphabet.length())) : ' ');
            }
            g.fillText(ligne.toString(), 0, y);
        }

        // Vignette : les bords s'assombrissent comme sur un tube bombé. Posée APRÈS le papier
        // peint pour qu'elle l'atténue aussi — sinon les coins restent aussi bavards que le centre.
        g.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.80, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.TRANSPARENT),
                new Stop(0.60, Color.TRANSPARENT),
                new Stop(1, Color.color(0, 0, 0, 0.62))));
        g.fillRect(0, 0, w, h);

        // Lignes de balayage cathodiques.
        g.setFill(Color.color(0, 0, 0, 0.22));
        for (double y = 0; y < h; y += 3) g.fillRect(0, y, w, 1);
    }

    /**
     * Rend un mot en ASCII art dans un {@code Canvas} statique — le « logo » du canal DEV.
     * Dessiné une fois, jamais réanimé.
     *
     * @param texte        mot à composer (lettres présentes dans {@link AsciiFont})
     * @param largeurCible largeur visée en pixels ; la taille de police en découle
     */
    public static Canvas marqueAscii(String texte, double largeurCible) {
        AsciiReveal mot = new AsciiReveal(texte);
        double ratio = CharGrid.advanceOf(TerminalTheme.mono(100)) / 100.0;
        double taille = Math.max(6.0, (largeurCible / mot.width) / ratio);
        javafx.scene.text.Font f = TerminalTheme.mono(taille);
        CharGrid grille = new CharGrid(f, mot.width, mot.height, TerminalTheme.palette());
        mot.stampFinal(grille, 0, 0, TerminalTheme.C_ACCENT);
        Canvas canvas = new Canvas(grille.cols * grille.cellW, grille.rows * grille.cellH);
        grille.paint(canvas.getGraphicsContext2D(), 0, 0);
        return canvas;
    }

    /**
     * Bloc d'informations de session, présenté comme la sortie d'une commande.
     *
     * <p>Toutes les valeurs sont <b>réelles</b> (serveur visé, dépôt du pack, version du payload) :
     * un faux flux de journal aurait fait joli une fois puis menti tous les jours suivants. La
     * ligne « joueurs » est branchée sur le compteur en ligne existant via {@code compteur}.
     */
    public static Region blocInfos(Label compteur) {
        String packTag = fr.nylerp.launcher.config.Constants.MANIFEST_URL.contains("pack-dev")
                ? "pack-dev" : "pack-latest";
        VBox box = new VBox(2,
                ligne("canal",   "DEV", true),
                ligne("serveur", fr.nylerp.launcher.config.Constants.SERVER_HOST
                        + ":" + fr.nylerp.launcher.config.Constants.SERVER_PORT, false),
                ligne("pack",    packTag, false),
                ligne("payload", fr.nylerp.launcher.config.Constants.runningPayloadVersion(), false));
        compteur.getStyleClass().add("term-info");
        box.getChildren().add(compteur);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    /** {@code > canal ............ DEV} */
    private static Label ligne(String cle, String valeur, boolean accent) {
        StringBuilder sb = new StringBuilder("> ").append(cle).append(' ');
        while (sb.length() < 14) sb.append('.');
        sb.append(' ').append(valeur);
        Label l = new Label(sb.toString());
        l.getStyleClass().add(accent ? "term-info-em" : "term-info");
        return l;
    }

    private TerminalSkin() {}
}
