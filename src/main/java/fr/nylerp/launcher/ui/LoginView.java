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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.util.function.Consumer;

public class LoginView extends StackPane {

    public LoginView(Consumer<Account> onAuthenticated) {
        getStyleClass().add("login-root");

        VBox card = new VBox(20);
        card.getStyleClass().add("login-card");
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(40, 50, 40, 50));
        card.setMaxWidth(420);
        card.setMaxHeight(500);

        ImageView logo = new ImageView();
        try {
            logo.setImage(new Image(getClass().getResourceAsStream("/images/logo.png")));
            logo.setFitWidth(130);
            logo.setPreserveRatio(true);
        } catch (Exception ignored) {}

        Label title = new Label("NyleRP");
        title.getStyleClass().add("h1");
        Label subtitle = new Label("Connexion au launcher");
        subtitle.getStyleClass().add("subtitle");

        Button msBtn = new Button("Se connecter avec Microsoft");
        msBtn.getStyleClass().addAll("btn", "btn-primary");
        msBtn.setMaxWidth(Double.MAX_VALUE);
        msBtn.setOnAction(e -> {
            msBtn.setDisable(true);
            msBtn.setText("Ouverture du navigateur…");
            MicrosoftAuth.loginWithWebview()
                    .whenComplete((acc, err) -> Platform.runLater(() -> {
                        msBtn.setDisable(false);
                        msBtn.setText("Se connecter avec Microsoft");
                        if (err != null) {
                            showError("Microsoft", err.getMessage());
                        } else {
                            onAuthenticated.accept(acc);
                        }
                    }));
        });

        Label or = new Label("ou");
        or.getStyleClass().add("muted");

        Label crackedTitle = new Label("Mode offline (crack)");
        crackedTitle.getStyleClass().add("section");

        TextField pseudoField = new TextField();
        pseudoField.setPromptText("Pseudo (3-16 caractères)");
        pseudoField.getStyleClass().add("input");

        Button offlineBtn = new Button("Jouer en offline");
        offlineBtn.getStyleClass().addAll("btn", "btn-ghost");
        offlineBtn.setMaxWidth(Double.MAX_VALUE);
        offlineBtn.setOnAction(e -> {
            try {
                Account acc = OfflineAuth.login(pseudoField.getText());
                onAuthenticated.accept(acc);
            } catch (IllegalArgumentException ex) {
                showError("Pseudo invalide", ex.getMessage());
            }
        });

        card.getChildren().addAll(logo, title, subtitle, msBtn, or, crackedTitle, pseudoField, offlineBtn);
        getChildren().add(card);
        setAlignment(Pos.CENTER);
    }

    private static void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg);
        a.setTitle(title);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
