package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.auth.MicrosoftAuth;
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
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.util.function.Consumer;

public class LoginView extends StackPane {

    public LoginView(Consumer<Account> onAuthenticated) {
        getStyleClass().add("login-root");

        // Background ambient orbs (no blur on JavaFX Circle with fill — use large radius + low opacity)
        Circle orb1 = new Circle(180, Color.web("#FF6A1A", 0.10));
        orb1.setTranslateX(-340); orb1.setTranslateY(-220);
        orb1.setMouseTransparent(true);

        Circle orb2 = new Circle(160, Color.web("#5B3CFF", 0.08));
        orb2.setTranslateX(320); orb2.setTranslateY(200);
        orb2.setMouseTransparent(true);

        // Center column
        VBox col = new VBox();
        col.setAlignment(Pos.CENTER);
        col.setMaxWidth(380);

        NyleLogo logo = new NyleLogo(62, Color.WHITE);

        // Title with orange emphasis
        Text t1 = new Text("Bienvenue sur\n"); t1.getStyleClass().add("hd-title");
        Text t2 = new Text("NyleRP.");         t2.getStyleClass().add("hd-title-em");
        TextFlow title = new TextFlow(t1, t2);
        title.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        title.setMaxWidth(380);

        Label sub = new Label("Serveur Minecraft roleplay · saison 0");
        sub.getStyleClass().add("hd-sub");

        // Microsoft CTA
        Button ms = new Button("Continuer avec Microsoft");
        ms.getStyleClass().add("btn-ms");
        ms.setPrefHeight(48);
        ms.setMaxWidth(Double.MAX_VALUE);
        ms.setGraphic(Icons.microsoftLogo(14));
        ms.setGraphicTextGap(10);
        ms.setOnAction(e -> doMicrosoftLogin(ms, onAuthenticated));

        // "ou" divider
        HBox divider = dividerWithLabel("OU");

        // Offline block
        Label pseudoLbl = new Label("PSEUDO");
        pseudoLbl.getStyleClass().add("field-label");

        TextField pseudo = new TextField();
        pseudo.getStyleClass().add("field-input");
        pseudo.setPromptText("Steve");
        pseudo.setPrefHeight(48);
        pseudo.setMaxWidth(Double.MAX_VALUE);
        pseudo.setOnAction(e -> tryOffline(pseudo, onAuthenticated));

        Button offline = new Button("Jouer en offline");
        offline.getStyleClass().add("btn-ghost");
        offline.setPrefHeight(48);
        offline.setMaxWidth(Double.MAX_VALUE);
        offline.setOnAction(e -> tryOffline(pseudo, onAuthenticated));

        VBox offlineBox = new VBox(6, pseudoLbl, pseudo);
        VBox authBox = new VBox(20, ms, divider, offlineBox, offline);
        authBox.setFillWidth(true);
        authBox.setMaxWidth(380);

        col.getChildren().addAll(
                logo,
                spacer(36),
                title,
                spacer(10),
                sub,
                spacer(40),
                authBox
        );

        // Footer
        Label footer = new Label("NyleLauncher v0.1.0  ·  play.nylerp.fr");
        footer.getStyleClass().add("foot-ver");
        StackPane.setAlignment(footer, Pos.BOTTOM_CENTER);
        StackPane.setMargin(footer, new Insets(0, 0, 22, 0));

        getChildren().addAll(orb1, orb2, col, footer);
        setAlignment(col, Pos.CENTER);

        // Stagger in
        int i = 0;
        for (Node n : col.getChildren()) {
            if (n instanceof Region r && r.getMaxHeight() == 0) continue;
            Animations.enter(n, Duration.millis(80 + (i++) * 60));
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
        btn.setText("Ouverture de Microsoft…");
        MicrosoftAuth.loginWithWebview()
                .whenComplete((acc, err) -> Platform.runLater(() -> {
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

    private void tryOffline(TextField pseudo, Consumer<Account> onAuthenticated) {
        try {
            onAuthenticated.accept(OfflineAuth.login(pseudo.getText()));
        } catch (IllegalArgumentException ex) {
            showError("Pseudo invalide", ex.getMessage());
        }
    }

    private static void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg);
        a.setTitle(title);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
