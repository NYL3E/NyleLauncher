package fr.nylerp.launcher.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.concurrent.CompletableFuture;

/**
 * Modal shown when the modpack sync fails during « Jouer » (no network, GitHub
 * unreachable, checksum mismatch…). Replaces the old SILENT fallback that
 * launched stale mods without telling the player — which left users stuck on an
 * outdated client indefinitely without any signal.
 *
 * <p>Three explicit choices:
 * <ul>
 *   <li><b>Réessayer</b> — retry the sync (loops in the caller).</li>
 *   <li><b>Lancer quand même</b> — proceed with the local mods. Kept on purpose
 *       for the genuine offline / GitHub-down case, but now a CONSCIOUS choice.</li>
 *   <li><b>Annuler</b> — abort the launch cleanly.</li>
 * </ul>
 *
 * <p>Called from the background Launch-Thread; {@link #ask(String)} blocks that
 * thread until the player picks, coordinating with the JavaFX thread through a
 * {@link CompletableFuture}. Visual style mirrors {@code BrokenBootstrapDialog}.
 */
public final class SyncFailedDialog {

    public enum Choice { RETRY, LAUNCH_ANYWAY, CANCEL }

    /** Show the dialog on the FX thread and block the caller until the player chooses. */
    public static Choice ask(String errorDetail) {
        CompletableFuture<Choice> result = new CompletableFuture<>();
        Platform.runLater(() -> show(errorDetail, result));
        try {
            return result.get();
        } catch (Exception e) {
            return Choice.CANCEL;   // thread interrupted / FX gone → safest is to abort the launch
        }
    }

    private static void show(String errorDetail, CompletableFuture<Choice> result) {
        Stage stage = new Stage(StageStyle.UNDECORATED);
        stage.initModality(Modality.APPLICATION_MODAL);

        Label title = new Label("Mise à jour échouée");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: 800;");

        String detail = (errorDetail == null || errorDetail.isBlank())
                ? "" : "\n\nDétail : " + errorDetail;
        Label body = new Label(
            "Impossible de contacter le serveur de mise à jour.\n" +
            "Lancer maintenant utilisera les mods déjà installés, qui peuvent être\n" +
            "PÉRIMÉS (versions différentes de celles du serveur)." + detail);
        body.setStyle("-fx-text-fill: #b6a8c6; -fx-font-size: 13px;");
        body.setWrapText(true);

        Button retry = new Button("Réessayer");
        retry.setStyle(
            "-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: 700; " +
            "-fx-padding: 12 28 12 28; -fx-background-radius: 6; -fx-font-size: 13px;");
        retry.setOnAction(e -> finish(stage, result, Choice.RETRY));

        Button anyway = new Button("Lancer quand même");
        anyway.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #b6a8c6; -fx-border-color: #3f2a55; " +
            "-fx-border-width: 1; -fx-padding: 12 18 12 18; -fx-background-radius: 6; -fx-border-radius: 6;");
        anyway.setOnAction(e -> finish(stage, result, Choice.LAUNCH_ANYWAY));

        Button cancel = new Button("Annuler");
        cancel.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #7a6f88; " +
            "-fx-padding: 12 18 12 18; -fx-background-radius: 6;");
        cancel.setOnAction(e -> finish(stage, result, Choice.CANCEL));

        HBox actions = new HBox(8, retry, anyway, cancel);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(16, title, body, actions);
        root.setPadding(new Insets(28));
        root.setStyle("-fx-background-color: #1a0e20; -fx-border-color: #3f2a55; -fx-border-width: 1;");
        root.setPrefWidth(460);

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle("NyleLauncher");
        // UNDECORATED has no close button, but guard any programmatic close → treat as CANCEL.
        stage.setOnCloseRequest(e -> finish(stage, result, Choice.CANCEL));
        stage.show();
    }

    private static void finish(Stage stage, CompletableFuture<Choice> result, Choice choice) {
        if (!result.isDone()) result.complete(choice);
        stage.close();
    }

    private SyncFailedDialog() {}
}
