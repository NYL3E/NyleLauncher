package fr.nylerp.launcher.fond;

import fr.nylerp.launcher.LauncherApp;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Montre une carte d'annonce de test, puis quitte : {@code NyleLauncher --notif-test}. Aucune fenêtre, aucun verrou
 * d'instance, aucun réglage lu (la carte s'affiche même si les notifications sont coupées : c'est un contrôle visuel).
 * Sert à vérifier en une seconde le rendu, la croix et le clic sur un système donné, sans publier d'annonce.
 */
public final class NotifTest extends Application {

    public static final String ARG = "--notif-test";

    @Override
    public void start(Stage stage) {
        LauncherApp.loadFonts();
        Toast.afficher("Notification de test",
                "Clique sur la croix en haut à droite pour la fermer, ou n'importe où ailleurs sur la carte.",
                () -> { });
        // 8 s d'affichage + marge, puis on quitte (le survol suspend le délai : on laisse 40 s au plus).
        PauseTransition fin = new PauseTransition(Duration.seconds(40));
        fin.setOnFinished(e -> Platform.exit());
        fin.play();
        Toast.surToutFerme(() -> {
            PauseTransition court = new PauseTransition(Duration.millis(400));
            court.setOnFinished(e -> Platform.exit());
            court.play();
        });
    }
}
