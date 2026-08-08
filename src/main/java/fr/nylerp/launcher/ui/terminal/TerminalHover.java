package fr.nylerp.launcher.ui.terminal;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.util.Duration;

/**
 * Réactions au survol dans le langage du terminal.
 *
 * <p>Deux effets seulement, volontairement :
 * <ul>
 *   <li>{@link #recompose} — le texte du bouton se recompose caractère par caractère, comme un
 *       décodage ;</li>
 *   <li>{@link #curseur} — un chevron d'invite apparaît devant l'élément survolé, façon ligne
 *       sélectionnée dans un menu texte.</li>
 * </ul>
 * Le reste du survol (fond, bordure, couleur) est fait en CSS dans {@code terminal.css} : c'est là
 * que ça coûte le moins cher et que ça se retouche sans recompiler.
 *
 * <p><b>Coût</b> — aucune animation ne tourne au repos. Une recomposition dure
 * {@value #DUREE_MS} ms, en {@value #ETAPES} images ; sortir du bouton l'arrête immédiatement.
 *
 * <p><b>Garde-fou</b> — si le texte du libellé change pendant l'effet (le bouton passe de
 * « JOUER » à « METTRE À JOUR », ou affiche un pourcentage de téléchargement), l'animation
 * s'interrompt sans rien réécrire : un effet décoratif ne doit jamais masquer un état réel.
 */
public final class TerminalHover {

    private static final int ETAPES = 8;
    private static final int DUREE_MS = 260;
    private static final char[] BROUILLAGE = "#$%&/\\|<>*+=_-01!?".toCharArray();
    private static final java.util.Random RNG = new java.util.Random();

    /**
     * État du texte à l'étape {@code etape} de la recomposition : le début est déjà figé, la fin
     * est encore brouillée. Isolée en fonction pure pour être <b>rendue en capture</b> par
     * {@code TerminalPreview} — un effet de survol ne se prouve pas autrement qu'en le montrant,
     * et une souris ne se pilote pas depuis une capture d'écran.
     */
    public static String recomposition(String finale, int etape, int etapes) {
        int fige = (int) Math.ceil(finale.length() * (etape / (double) etapes));
        StringBuilder sb = new StringBuilder(finale.substring(0, Math.min(fige, finale.length())));
        for (int i = sb.length(); i < finale.length(); i++) {
            char c = finale.charAt(i);
            sb.append(c == ' ' ? ' ' : BROUILLAGE[RNG.nextInt(BROUILLAGE.length)]);
        }
        return sb.toString();
    }

    /**
     * Recompose le texte du libellé quand la souris entre sur {@code source}.
     *
     * @param source nœud qui reçoit le survol (souvent le bouton)
     * @param cible  libellé dont le texte est recomposé
     */
    public static void recompose(Node source, Labeled cible) {
        final Timeline[] courant = new Timeline[1];
        final String[] dernierEcrit = new String[1];

        source.setOnMouseEntered(e -> {
            if (courant[0] != null) courant[0].stop();
            final String finale = cible.getText();
            if (finale == null || finale.isEmpty()) return;
            Timeline tl = new Timeline();
            for (int k = 1; k <= ETAPES; k++) {
                final int etape = k;
                tl.getKeyFrames().add(new KeyFrame(
                        Duration.millis((double) DUREE_MS * k / ETAPES), ev -> {
                    // Le libellé a changé sous nos pieds (fin de téléchargement, changement
                    // d'état du bouton) : on lâche l'affaire plutôt que d'écraser l'info.
                    if (dernierEcrit[0] != null && !dernierEcrit[0].equals(cible.getText())) {
                        if (courant[0] != null) courant[0].stop();
                        return;
                    }
                    dernierEcrit[0] = recomposition(finale, etape, ETAPES);
                    cible.setText(dernierEcrit[0]);
                }));
            }
            tl.setOnFinished(ev -> {
                if (dernierEcrit[0] == null || dernierEcrit[0].equals(cible.getText())) {
                    cible.setText(finale);
                    dernierEcrit[0] = finale;
                }
            });
            courant[0] = tl;
            dernierEcrit[0] = null;
            tl.play();
        });

        source.setOnMouseExited(e -> {
            if (courant[0] != null) courant[0].stop();
            // On ne restaure QUE si la dernière écriture vient de nous.
            if (dernierEcrit[0] != null && dernierEcrit[0].equals(cible.getText())) {
                cible.setText(dernierEcrit[0]);
            }
        });
    }

    /**
     * Fait apparaître un chevron d'invite devant le libellé au survol — la ligne « prend le
     * curseur », comme dans un menu texte.
     */
    public static void curseur(Node source, Labeled cible) {
        final String[] base = { cible.getText() };
        cible.textProperty().addListener((o, ov, nv) -> {
            if (nv != null && !nv.startsWith("> ")) base[0] = nv;
        });
        source.setOnMouseEntered(e -> cible.setText("> " + base[0]));
        source.setOnMouseExited(e -> cible.setText(base[0]));
    }

    private TerminalHover() {}
}
