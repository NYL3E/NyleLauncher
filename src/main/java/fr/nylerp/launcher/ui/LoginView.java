package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.auth.MicrosoftAuth;
import fr.nylerp.launcher.auth.OfflineAuth;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.function.Consumer;

public class LoginView extends StackPane {

    public LoginView(Consumer<Account> onAuthenticated) {
        getStyleClass().add("login-root");

        // ── Background ambient glow ─────────────────────────────────────────
        Circle glowTopLeft = new Circle(380, Color.web("#FF7A1A", 0.12));
        glowTopLeft.setTranslateX(-520);
        glowTopLeft.setTranslateY(-380);
        glowTopLeft.setMouseTransparent(true);
        Circle glowBottomRight = new Circle(280, Color.web("#5B3CFF", 0.10));
        glowBottomRight.setTranslateX(520);
        glowBottomRight.setTranslateY(340);
        glowBottomRight.setMouseTransparent(true);

        // ── Centre stack ────────────────────────────────────────────────────
        NyleLogo logo = new NyleLogo(92);

        Label title = new Label("NyleRP");
        title.getStyleClass().add("title-xl");

        Label tag = new Label("L'expérience Roleplay Minecraft");
        tag.getStyleClass().add("tagline");

        // Microsoft CTA
        Button msBtn = buildMicrosoftButton(onAuthenticated);

        // Divider
        HBox divider = buildDivider();

        // Offline block — minimal floating input
        TextField pseudo = new TextField();
        pseudo.setPromptText("Pseudo");
        pseudo.getStyleClass().add("input-ghost");
        pseudo.setPrefWidth(320);
        pseudo.setMaxWidth(320);
        pseudo.setOnAction(e -> tryOffline(pseudo, onAuthenticated));

        Button offlineBtn = new Button("Jouer en offline");
        offlineBtn.getStyleClass().add("link-btn");
        offlineBtn.setOnAction(e -> tryOffline(pseudo, onAuthenticated));

        VBox card = new VBox();
        card.setAlignment(Pos.CENTER);
        card.setSpacing(0);
        card.setMaxWidth(360);
        card.setPadding(new Insets(20));
        card.getChildren().addAll(
                spaced(logo, 0, 20),
                spaced(title, 0, 10),
                spaced(tag, 0, 44),
                spaced(msBtn, 0, 28),
                spaced(divider, 0, 28),
                spaced(pseudo, 0, 14),
                offlineBtn
        );

        Label version = new Label("v0.1.0");
        version.getStyleClass().add("version-badge");
        StackPane.setAlignment(version, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(version, new Insets(0, 28, 22, 0));

        getChildren().addAll(glowTopLeft, glowBottomRight, card, version);
        setAlignment(card, Pos.CENTER);

        // ── Stagger in ─────────────────────────────────────────────────────
        int idx = 0;
        for (javafx.scene.Node child : card.getChildren()) {
            if (idx == 0) {
                Animations.pop(child, Duration.millis(80));
            } else {
                Animations.enter(child, Duration.millis(80 + idx * 70));
            }
            idx++;
        }
        Animations.breathe(logo, 1.0, 1.035, Duration.seconds(4.5));
    }

    private static Region spaced(javafx.scene.Node n, double above, double below) {
        VBox box = new VBox(n);
        box.setAlignment(Pos.CENTER);
        VBox.setMargin(n, new Insets(above, 0, below, 0));
        return box;
    }

    private Button buildMicrosoftButton(Consumer<Account> onAuthenticated) {
        Button b = new Button("Continuer avec Microsoft");
        b.getStyleClass().addAll("cta", "cta-primary");
        b.setPrefHeight(52);
        b.setPrefWidth(320);
        b.setMaxWidth(320);
        b.setOnAction(e -> {
            b.setDisable(true);
            b.setText("Ouverture de la connexion Microsoft…");
            MicrosoftAuth.loginWithWebview()
                    .whenComplete((acc, err) -> Platform.runLater(() -> {
                        b.setDisable(false);
                        b.setText("Continuer avec Microsoft");
                        if (err != null) {
                            showError("Microsoft", err.getCause() != null
                                    ? err.getCause().getMessage()
                                    : err.getMessage());
                        } else {
                            onAuthenticated.accept(acc);
                        }
                    }));
        });
        return b;
    }

    private HBox buildDivider() {
        Region line1 = new Region();
        line1.getStyleClass().add("divider-line");
        HBox.setHgrow(line1, Priority.ALWAYS);
        Region line2 = new Region();
        line2.getStyleClass().add("divider-line");
        HBox.setHgrow(line2, Priority.ALWAYS);
        Label or = new Label("OU");
        or.getStyleClass().add("divider-label");
        HBox h = new HBox(12, line1, or, line2);
        h.setAlignment(Pos.CENTER);
        h.setMaxWidth(320);
        return h;
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
