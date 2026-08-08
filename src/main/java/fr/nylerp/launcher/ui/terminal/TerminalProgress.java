package fr.nylerp.launcher.ui.terminal;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.value.ObservableDoubleValue;
import javafx.scene.control.ProgressBar;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

/**
 * Barre de chargement façon terminal : {@code [########............]  42%}.
 *
 * <p><b>Pourquoi elle ne remplace pas la {@link ProgressBar} mais la reflète</b> — toute la logique
 * de mise à jour du launcher (sync du modpack, téléchargement d'un installeur, lancement du jeu)
 * pousse son avancement dans une {@code ProgressBar} précise, depuis une demi-douzaine d'endroits.
 * Réécrire ces appels aurait mis du code de production (canal PROD compris) sur le chemin critique
 * pour un simple habillage. Ici la {@code ProgressBar} d'origine reste la source de vérité : elle
 * n'est simplement pas ajoutée à la scène en canal DEV, et cette vue s'abonne à sa propriété.
 * Aucun appelant ne change, donc la production ne peut pas régresser.
 *
 * <p><b>Coût</b> — un abonnement à une propriété : <b>au repos, rien ne tourne du tout</b>. Le
 * balayage d'attente (un curseur qui parcourt la barre à 8 images/s, pour les phases sans
 * progression mesurable comme le hachage local des fichiers) ne démarre que lorsque le launcher se
 * déclare occupé via {@link #setBusy(boolean)} — c'est-à-dire au même instant que le compteur du
 * bouton JOUER. Il s'arrête dès que l'avancement démarre, dès la fin du travail, et dès que le
 * nœud quitte la scène.
 */
public final class TerminalProgress extends TextFlow {

    /** Largeur de la barre, en caractères. */
    private static final int CELLS = 34;

    private final Text open  = seg("[",  "term-bar-frame");
    private final Text fill  = seg("",   "term-bar-fill");
    private final Text rest  = seg("",   "term-bar-rest");
    private final Text close = seg("]",  "term-bar-frame");
    private final Text pct   = seg("",   "term-bar-pct");

    private final Timeline sweep;
    private int sweepPos = 0;
    private boolean busy = false;
    private final ObservableDoubleValue progress;

    public TerminalProgress(ObservableDoubleValue progress) {
        this.progress = progress;
        getStyleClass().add("term-bar");
        getChildren().addAll(open, fill, rest, close, pct);

        sweep = new Timeline(new KeyFrame(Duration.millis(125), e -> {
            sweepPos = (sweepPos + 1) % (CELLS + 6);
            paintIdle();
        }));
        sweep.setCycleCount(Animation.INDEFINITE);

        progress.addListener((obs, o, n) -> apply(n.doubleValue()));
        // Le balayage d'attente ne doit jamais survivre à l'écran : sans ça, revenir aux
        // Paramètres laisserait une Timeline tourner sur un nœud détaché, pour rien.
        sceneProperty().addListener((obs, o, n) -> { if (n == null) sweep.stop(); else apply(this.progress.get()); });
        apply(progress.get());
    }

    /** Déclaré par la vue quand un travail long démarre / se termine (voir {@code setPlayBusy}). */
    public void setBusy(boolean busy) {
        this.busy = busy;
        apply(progress.get());
    }

    private void apply(double p) {
        if (p <= 0.0 || Double.isNaN(p)) {
            if (busy && getScene() != null) {
                if (sweep.getStatus() != Animation.Status.RUNNING) sweep.play();
            } else {
                sweep.stop();
                sweepPos = -1;              // barre vide, sans curseur : rien ne se passe
            }
            paintIdle();
            return;
        }
        sweep.stop();
        int filled = (int) Math.round(Math.min(1.0, p) * CELLS);
        fill.setText("#".repeat(filled));
        rest.setText(".".repeat(CELLS - filled));
        pct.setText(String.format(java.util.Locale.ROOT, "  %3d%%", (int) Math.round(Math.min(1.0, p) * 100)));
    }

    /** Attente : un curseur de 3 caractères parcourt la barre vide. */
    private void paintIdle() {
        StringBuilder sb = new StringBuilder(".".repeat(CELLS));
        for (int i = 0; i < 3; i++) {
            int x = sweepPos - i;
            if (x >= 0 && x < CELLS) sb.setCharAt(x, i == 0 ? '#' : '=');
        }
        fill.setText("");
        rest.setText(sb.toString());
        pct.setText("   --%");
    }

    private static Text seg(String s, String styleClass) {
        Text t = new Text(s);
        t.getStyleClass().add(styleClass);
        return t;
    }
}
