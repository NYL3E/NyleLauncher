package fr.nylerp.launcher.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.List;

/**
 * Boîte d'erreur/info NyleRP — remplace les {@code Alert} JavaFX bruts (le popup système gris
 * « Microsoft ✕ » que voyaient les joueurs). Style aligné sur {@link SyncFailedDialog} /
 * {@code BrokenBootstrapDialog} : fenêtre sombre sans chrome, titre net, texte lisible,
 * étapes concrètes en puces, détail technique discret en bas, bouton unique.
 *
 * <p>Toujours appelée sur le thread JavaFX. Bloquante ({@code showAndWait}) comme l'ancien Alert,
 * pour ne changer aucun flux appelant.</p>
 */
public final class NyleErrorDialog {

    private NyleErrorDialog() {}

    /** Erreur simple (titre + message). */
    public static void show(String title, String message) {
        show(title, message, null, null);
    }

    /**
     * @param title     titre court (« Connexion Microsoft refusée »)
     * @param message   explication en français simple — la CAUSE probable, pas le jargon
     * @param steps     étapes concrètes pour s'en sortir (puces), ou {@code null}
     * @param technical détail technique brut (message d'origine), affiché petit et discret, ou {@code null}
     */
    public static void show(String title, String message, List<String> steps, String technical) {
        Stage stage = new Stage(StageStyle.TRANSPARENT);   // coins arrondis réels (fond de scène transparent)
        stage.initModality(Modality.APPLICATION_MODAL);

        // Pastille d'icône : rond rouge doux avec une croix dessinée (pas d'emoji, pas d'icône système).
        Label cross = new Label("✕");
        cross.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: 900;");
        StackPane icon = new StackPane(cross);
        icon.setMinSize(38, 38); icon.setMaxSize(38, 38);
        icon.setStyle("-fx-background-color: #ef4444; -fx-background-radius: 19;");

        Label titleL = new Label(title);
        titleL.setStyle("-fx-text-fill: white; -fx-font-size: 19px; -fx-font-weight: 800;");
        titleL.setWrapText(true);

        HBox header = new HBox(14, icon, titleL);
        header.setAlignment(Pos.CENTER_LEFT);

        Label body = new Label(message);
        body.setStyle("-fx-text-fill: #d6cde2; -fx-font-size: 13.5px;");
        body.setWrapText(true);
        body.setMaxWidth(440);

        VBox root = new VBox(14, header, body);

        if (steps != null && !steps.isEmpty()) {
            VBox stepBox = new VBox(7);
            for (String s : steps) {
                Label dot = new Label("•");
                dot.setStyle("-fx-text-fill: #f0b429; -fx-font-size: 14px; -fx-font-weight: 900;");
                Label txt = new Label(s);
                txt.setStyle("-fx-text-fill: #b6a8c6; -fx-font-size: 12.5px;");
                txt.setWrapText(true);
                txt.setMaxWidth(410);
                HBox line = new HBox(9, dot, txt);
                line.setAlignment(Pos.TOP_LEFT);
                stepBox.getChildren().add(line);
            }
            VBox card = new VBox(stepBox);
            card.setPadding(new Insets(12, 14, 12, 14));
            card.setStyle("-fx-background-color: #241430; -fx-background-radius: 10; "
                    + "-fx-border-color: #3f2a55; -fx-border-width: 1; -fx-border-radius: 10;");
            root.getChildren().add(card);
        }

        if (technical != null && !technical.isBlank()) {
            Label tech = new Label("Détail technique : " + technical);
            tech.setStyle("-fx-text-fill: #7a6f88; -fx-font-size: 10.5px;");
            tech.setWrapText(true);
            tech.setMaxWidth(440);
            root.getChildren().add(tech);
        }

        Button ok = new Button("Compris");
        ok.setStyle("-fx-background-color: #f0b429; -fx-text-fill: #241430; -fx-font-weight: 800; "
                + "-fx-font-size: 13px; -fx-background-radius: 8; -fx-padding: 8 26 8 26; -fx-cursor: hand;");
        ok.setOnAction(e -> stage.close());
        HBox actions = new HBox(ok);
        actions.setAlignment(Pos.CENTER_RIGHT);
        root.getChildren().add(actions);

        root.setPadding(new Insets(22, 24, 18, 24));
        root.setStyle("-fx-background-color: #1a0e20; -fx-border-color: #3f2a55; -fx-border-width: 1; "
                + "-fx-background-radius: 14; -fx-border-radius: 14;");
        root.setMaxWidth(500);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setScene(scene);
        stage.setAlwaysOnTop(true);
        stage.showAndWait();
    }
}
