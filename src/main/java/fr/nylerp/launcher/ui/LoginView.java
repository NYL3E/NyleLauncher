package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.auth.MicrosoftSystemAuth;
import fr.nylerp.launcher.auth.OfflineAuth;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.util.function.Consumer;

public class LoginView extends StackPane {

    public LoginView(Consumer<Account> onAuthenticated) {
        getStyleClass().add("login-root");

        // Glass card centered — every dimension is the previous value × 0.85
        VBox card = new VBox();
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(357);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.getStyleClass().add("login-card");
        card.setPadding(new Insets(37, 37, 31, 37));

        NyleLogo logo = new NyleLogo(48, Color.WHITE);

        Text t1 = new Text("Bienvenue sur\n"); t1.getStyleClass().add("hd-title");
        Text t2 = new Text("NyleRP.");         t2.getStyleClass().add("hd-title-em");
        TextFlow title = new TextFlow(t1, t2);
        title.setTextAlignment(TextAlignment.CENTER);

        Label sub = new Label("Serveur Minecraft roleplay · saison 0");
        sub.getStyleClass().add("hd-sub");

        Button ms = new Button("Continuer avec Microsoft");
        ms.getStyleClass().add("btn-ms");
        ms.setPrefHeight(41);
        ms.setMaxWidth(Double.MAX_VALUE);
        ms.setGraphic(Icons.microsoftLogo(12));
        ms.setGraphicTextGap(9);
        ms.setOnAction(e -> doMicrosoftLogin(ms, onAuthenticated));

        HBox divider = dividerWithLabel("OU");

        Label pseudoLbl = new Label("PSEUDO");
        pseudoLbl.getStyleClass().add("field-label");

        TextField pseudo = new TextField();
        pseudo.getStyleClass().add("field-input");
        pseudo.setPromptText("Steve");
        pseudo.setPrefHeight(41);
        pseudo.setMaxWidth(Double.MAX_VALUE);
        pseudo.setOnAction(e -> tryOffline(pseudo, onAuthenticated));

        Button offline = new Button("Jouer en offline");
        offline.getStyleClass().add("btn-ghost");
        offline.setPrefHeight(41);
        offline.setMaxWidth(Double.MAX_VALUE);
        offline.setOnAction(e -> tryOffline(pseudo, onAuthenticated));

        VBox offlineBox = new VBox(5, pseudoLbl, pseudo);
        VBox authBox = new VBox(15, ms, divider, offlineBox, offline);
        authBox.setFillWidth(true);

        card.getChildren().addAll(
                logo,
                spacer(24),
                title,
                spacer(7),
                sub,
                spacer(31),
                authBox
        );

        Label footer = new Label("NyleLauncher v" + fr.nylerp.launcher.config.Constants.APP_VERSION + "  ·  play.nylerp.fr");
        footer.getStyleClass().add("foot-ver");
        StackPane.setAlignment(footer, Pos.BOTTOM_CENTER);
        StackPane.setMargin(footer, new Insets(0, 0, 22, 0));

        getChildren().addAll(card, footer);
        setAlignment(card, Pos.CENTER);

        int i = 0;
        for (Node n : card.getChildren()) {
            if (n instanceof Region r && r.getMaxHeight() == 0) continue;
            Animations.enter(n, Duration.millis(80 + (i++) * 50));
        }
    }

    private static Region spacer(double h) {
        Region r = new Region();
        r.setMinHeight(h); r.setPrefHeight(h); r.setMaxHeight(h);
        return r;
    }

    private HBox dividerWithLabel(String text) {
        Region l1 = new Region(); l1.getStyleClass().add("divider-line");
        l1.setMinHeight(1); l1.setPrefHeight(1); l1.setMaxHeight(1);
        HBox.setHgrow(l1, Priority.ALWAYS);
        Region l2 = new Region(); l2.getStyleClass().add("divider-line");
        l2.setMinHeight(1); l2.setPrefHeight(1); l2.setMaxHeight(1);
        HBox.setHgrow(l2, Priority.ALWAYS);
        Label lab = new Label(text); lab.getStyleClass().add("divider-label");
        HBox h = new HBox(12, l1, lab, l2);
        h.setAlignment(Pos.CENTER);
        return h;
    }

    private void doMicrosoftLogin(Button btn, Consumer<Account> onAuthenticated) {
        btn.setDisable(true);
        btn.setText("Demande du code…");

        // Dialog shown when Microsoft hands us the user_code. It copies the
        // code to clipboard, opens microsoft.com/link in the browser, and
        // waits for the background poll to finish.
        final javafx.stage.Stage[] dialog = { null };

        MicrosoftSystemAuth.login(dc -> Platform.runLater(() -> {
            btn.setText("En attente du navigateur…");
            javafx.scene.input.ClipboardContent cb = new javafx.scene.input.ClipboardContent();
            cb.putString(dc.userCode());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(cb);
            dialog[0] = showDeviceCodeDialog(dc.userCode(), dc.verificationUri());
        })).whenComplete((acc, err) -> Platform.runLater(() -> {
            if (dialog[0] != null) dialog[0].close();
            btn.setDisable(false);
            btn.setText("Continuer avec Microsoft");
            if (err != null) {
                showError("Microsoft",
                        err.getCause() != null ? err.getCause().getMessage() : err.getMessage());
            } else {
                onAuthenticated.accept(acc);
            }
        }));
    }

    /**
     * Pops a soft, screen-centered dialog with the Microsoft device code.
     * Auto-closed when the background poller receives the token.
     */
    private javafx.stage.Stage showDeviceCodeDialog(String code, String uri) {
        Label title = new Label("Connexion Microsoft");
        title.setFont(Fonts.semi(16));
        title.setTextFill(Color.web("#F4F4F7"));

        Label body = new Label("Colle ce code dans la page Microsoft qui vient de s'ouvrir (déjà copié).");
        body.setFont(Fonts.medium(12));
        body.setTextFill(Color.web("#A2A2AC"));
        body.setWrapText(true);
        body.setMaxWidth(320);
        body.setStyle("-fx-text-alignment: center;");

        Label codeLbl = new Label(code);
        codeLbl.setFont(Fonts.medium(24));
        codeLbl.setTextFill(Color.web("#F4F4F7"));
        codeLbl.setStyle("-fx-letter-spacing: 0.22em;"
                + "-fx-background-color: rgba(255,255,255,0.05);"
                + "-fx-background-radius: 14;"
                + "-fx-padding: 14 26 14 26;"
                + "-fx-border-color: rgba(255,255,255,0.10);"
                + "-fx-border-radius: 14; -fx-border-width: 1;");

        Button copyBtn = new Button("Copier");
        copyBtn.setFont(Fonts.medium(11));
        copyBtn.setStyle("-fx-background-color: rgba(255,255,255,0.06); -fx-text-fill: #A2A2AC;"
                + "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 6 16 6 16;");
        copyBtn.setOnAction(e -> {
            javafx.scene.input.ClipboardContent cb = new javafx.scene.input.ClipboardContent();
            cb.putString(code);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(cb);
        });

        Button openBtn = new Button("Rouvrir la page");
        openBtn.setFont(Fonts.medium(11));
        openBtn.setStyle("-fx-background-color: rgba(255,255,255,0.06); -fx-text-fill: #A2A2AC;"
                + "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 6 16 6 16;");
        openBtn.setOnAction(e -> openBrowserUri(uri));

        HBox actions = new HBox(8, copyBtn, openBtn);
        actions.setAlignment(Pos.CENTER);

        javafx.scene.control.ProgressIndicator spinner = new javafx.scene.control.ProgressIndicator();
        spinner.setMinSize(14, 14);
        spinner.setMaxSize(14, 14);
        spinner.setPrefSize(14, 14);
        spinner.setStyle("-fx-progress-color: #FF8128;");

        Label hint = new Label("En attente de validation…");
        hint.setFont(Fonts.medium(10));
        hint.setTextFill(Color.web("#8A8A94"));
        HBox hintRow = new HBox(8, spinner, hint);
        hintRow.setAlignment(Pos.CENTER);

        VBox content = new VBox(16, title, body, codeLbl, actions, hintRow);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(34, 40, 28, 40));
        content.setStyle(
                "-fx-background-color: linear-gradient(to bottom, rgba(32,26,52,0.96), rgba(20,16,36,0.96));"
                        + "-fx-background-radius: 20;"
                        + "-fx-border-color: rgba(255,255,255,0.12);"
                        + "-fx-border-radius: 20; -fx-border-width: 1;"
                        + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.55), 32, 0.25, 0, 8);");

        // Outer transparent pane so the drop-shadow has room to spread
        StackPane wrapper = new StackPane(content);
        wrapper.setPadding(new Insets(40));
        wrapper.setStyle("-fx-background-color: transparent;");

        javafx.scene.Scene scene = new javafx.scene.Scene(wrapper);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().addAll(getScene().getStylesheets());

        javafx.stage.Stage stg = new javafx.stage.Stage();
        stg.initStyle(javafx.stage.StageStyle.TRANSPARENT);
        stg.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stg.initOwner(getScene().getWindow());
        stg.setScene(scene);
        stg.setResizable(false);

        // Center on the primary screen (works even when the launcher window
        // is off-center), after layout so the stage's own size is known.
        stg.setOnShown(e -> {
            javafx.geometry.Rectangle2D screen = javafx.stage.Screen.getPrimary().getVisualBounds();
            stg.setX(screen.getMinX() + (screen.getWidth()  - stg.getWidth())  / 2);
            stg.setY(screen.getMinY() + (screen.getHeight() - stg.getHeight()) / 2);
        });
        stg.show();
        return stg;
    }

    private void openBrowserUri(String uri) {
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(uri));
            }
        } catch (Exception ignored) {}
    }

    private void tryOffline(TextField pseudo, Consumer<Account> onAuthenticated) {
        try { onAuthenticated.accept(OfflineAuth.login(pseudo.getText())); }
        catch (IllegalArgumentException ex) { showError("Pseudo invalide", ex.getMessage()); }
    }

    private static void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg);
        a.setTitle(title);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
