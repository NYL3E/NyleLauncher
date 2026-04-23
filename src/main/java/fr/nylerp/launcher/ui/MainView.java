package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.config.Constants;
import fr.nylerp.launcher.launch.MinecraftLauncher;
import fr.nylerp.launcher.update.ModpackUpdater;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class MainView extends BorderPane {

    private final Label statusLabel = new Label("Prêt");
    private final ProgressBar progressBar = new ProgressBar(0);

    public MainView(Account account, Runnable onLogout) {
        getStyleClass().add("main-root");

        // ── Top bar ─────────────────────────────────────────────────────────
        HBox top = new HBox(12);
        top.getStyleClass().add("top-bar");
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(16, 22, 16, 22));

        ImageView logo = new ImageView();
        try {
            logo.setImage(new Image(getClass().getResourceAsStream("/images/logo.png")));
            logo.setFitHeight(32);
            logo.setPreserveRatio(true);
        } catch (Exception ignored) {}

        Label brand = new Label("NyleRP");
        brand.getStyleClass().add("brand");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label user = new Label(account.username() + "  ·  "
                + (account.isOffline() ? "offline" : "Microsoft"));
        user.getStyleClass().add("user-pill");

        Button logoutBtn = new Button("Déconnexion");
        logoutBtn.getStyleClass().addAll("btn", "btn-small");
        logoutBtn.setOnAction(e -> onLogout.run());

        top.getChildren().addAll(logo, brand, spacer, user, logoutBtn);
        setTop(top);

        // ── Center: news panel placeholder ──────────────────────────────────
        VBox center = new VBox(14);
        center.setPadding(new Insets(24, 22, 24, 22));

        Label newsTitle = new Label("Actualités");
        newsTitle.getStyleClass().add("h2");

        Label placeholder = new Label("""
                ● Lootbox disponibles sur le serveur !
                ● Rejoindre la communauté sur Discord pour les événements hebdos.
                ● Mises à jour du modpack automatiques à chaque démarrage.
                """);
        placeholder.getStyleClass().add("news-body");
        placeholder.setWrapText(true);

        Hyperlink discord = new Hyperlink("Rejoindre le Discord");
        discord.getStyleClass().add("link");
        discord.setOnAction(e -> getScene().getWindow().getClass()); // TODO: open browser

        center.getChildren().addAll(newsTitle, placeholder, discord);
        setCenter(center);

        // ── Bottom: progress + play button + RAM slider ─────────────────────
        VBox bottom = new VBox(10);
        bottom.setPadding(new Insets(16, 22, 22, 22));
        bottom.getStyleClass().add("bottom-bar");

        statusLabel.getStyleClass().add("status");
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.getStyleClass().add("progress");

        Label ramLabel = new Label("Mémoire allouée : 4 Go");
        ramLabel.getStyleClass().add("muted");
        Slider ram = new Slider(2, 12, 4);
        ram.setBlockIncrement(1);
        ram.setMajorTickUnit(2);
        ram.setMinorTickCount(1);
        ram.setSnapToTicks(true);
        ram.setShowTickMarks(true);
        ram.setShowTickLabels(true);
        ram.valueProperty().addListener((obs, a, b) ->
                ramLabel.setText("Mémoire allouée : " + b.intValue() + " Go"));

        Button play = new Button("JOUER  ►");
        play.getStyleClass().addAll("btn", "btn-play");
        play.setMaxWidth(Double.MAX_VALUE);
        play.setOnAction(e -> startPlay(account, (int)(ram.getValue() * 1024), play));

        bottom.getChildren().addAll(statusLabel, progressBar, ramLabel, ram, play);
        setBottom(bottom);
    }

    private void startPlay(Account account, int ramMb, Button play) {
        play.setDisable(true);
        progressBar.setProgress(0);
        statusLabel.setText("Synchronisation du modpack…");

        new Thread(() -> {
            try {
                ModpackUpdater updater = new ModpackUpdater(new ModpackUpdater.Listener() {
                    @Override public void onStatus(String line) {
                        Platform.runLater(() -> statusLabel.setText(line));
                    }
                    @Override public void onProgress(int d, int t, long bd, long bt) {
                        Platform.runLater(() -> progressBar.setProgress(t == 0 ? 0 : (double) d / t));
                    }
                });
                updater.sync();
                Platform.runLater(() -> {
                    statusLabel.setText("Lancement de Minecraft…");
                    MinecraftLauncher.launch(account, ramMb, true);
                    statusLabel.setText("Minecraft lancé — connexion à " + Constants.SERVER_HOST);
                    play.setDisable(false);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    statusLabel.setText("Erreur: " + ex.getMessage());
                    play.setDisable(false);
                });
            }
        }, "Launch-Thread").start();
    }
}
