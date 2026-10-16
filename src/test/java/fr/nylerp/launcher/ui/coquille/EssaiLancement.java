package fr.nylerp.launcher.ui.coquille;

import fr.nylerp.launcher.LauncherApp;
import fr.nylerp.launcher.fond.Fond;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.stage.Stage;

/**
 * Essai de bout en bout (lot L5) : la VRAIE application (LauncherApp, Fond, coquille), puis, une fois
 * la vérification du pack faite, le clic sur « Jouer » de l'univers affiché, exactement comme le bouton.
 * À lancer dans un dossier personnel isolé (-Duser.home).
 */
public final class EssaiLancement extends LauncherApp {

    public static void main(String[] args) {
        if (!Fond.prendreLaMain(args)) return;
        Application.launch(EssaiLancement.class, args);
    }

    @Override
    public void start(Stage stage) {
        super.start(stage);
        new Thread(() -> {
            try { Thread.sleep(9000); } catch (InterruptedException ignored) { }
            Platform.runLater(() -> {
                Coquille c = trouver(stage.getScene().getRoot());
                if (c == null) { System.out.println("ESSAI : coquille introuvable"); return; }
                System.out.println("ESSAI : clic sur « " + c.lanceur().libelle().getText() + " »");
                c.lanceur().jouer();
            });
        }).start();
    }

    static Coquille trouver(Node n) {
        if (n instanceof Coquille c) return c;
        if (n instanceof Parent p) for (Node e : p.getChildrenUnmodifiable()) { Coquille c = trouver(e); if (c != null) return c; }
        return null;
    }
}
