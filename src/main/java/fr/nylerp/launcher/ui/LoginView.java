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
