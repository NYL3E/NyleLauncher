package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.config.Constants;
import fr.nylerp.launcher.launch.MinecraftLauncher;
import fr.nylerp.launcher.update.ModpackUpdater;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class MainView extends BorderPane {

    private final Label statusLabel = new Label("Prêt à jouer");
    private final ProgressBar progressBar = new ProgressBar(0);
    private Timeline playBreathe;

    public MainView(Account account, Runnable onLogout) {
        getStyleClass().add("main-root");
        this.accountRef = account;

        setTop(buildTopBar(account, onLogout));
        setCenter(buildHero());
        setBottom(buildBottomBar(account));

        // stagger center cards
        if (getCenter() instanceof VBox v) {
            int i = 0;
            for (Node n : v.getChildren()) {
                Animations.enter(n, Duration.millis(120 + i++ * 80));
            }
        }
    }

    // ── Top bar ─────────────────────────────────────────────────────────────

    private Region buildTopBar(Account account, Runnable onLogout) {
        NyleLogo logo = new NyleLogo(28, Color.web("#FF7A1A"), false);
        Label brand = new Label("NYLERP");
        brand.getStyleClass().add("brand-caps");

        HBox left = new HBox(14, logo, brand);
        left.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox nav = new HBox(32,
                navLink("Actualités"),
                navLink("Statistiques"),
                navLink("Discord")
        );
        nav.setAlignment(Pos.CENTER);

        // user pill
        Circle avatar = new Circle(14, Color.web("#FF7A1A"));
        Label userName = new Label(account.username());
        userName.getStyleClass().add("user-name");
        Label userType = new Label(account.isOffline() ? "Offline" : "Microsoft");
        userType.getStyleClass().add("user-type");
        VBox userInfo = new VBox(userName, userType);
        userInfo.setAlignment(Pos.CENTER_LEFT);

        Button logoutBtn = new Button("Déconnexion");
        logoutBtn.getStyleClass().add("icon-btn");
        logoutBtn.setOnAction(e -> onLogout.run());

        HBox userBox = new HBox(10, avatar, userInfo, logoutBtn);
        userBox.setAlignment(Pos.CENTER_RIGHT);
        userBox.getStyleClass().add("user-pill");
        userBox.setPadding(new Insets(6, 10, 6, 8));

        HBox bar = new HBox(28, left, spacer, nav, spacer(), userBox);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(18, 30, 18, 30));
        bar.getStyleClass().add("top-bar");

        return bar;
    }

    private Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private Label navLink(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("nav-link");
        return l;
    }

    // ── Hero / news ─────────────────────────────────────────────────────────

    private Region buildHero() {
        Label kicker = new Label("SAISON 0  •  FABRIC 1.21.1");
        kicker.getStyleClass().add("kicker");

        Label heroTitle = new Label("Rejoignez l'aventure.");
        heroTitle.getStyleClass().add("hero-title");
        heroTitle.setWrapText(true);

        Label heroSub = new Label(
                "Un serveur Minecraft roleplay exigeant et artisanal. "
              + "Tous les mods sont synchronisés automatiquement — cliquez sur JOUER.");
        heroSub.getStyleClass().add("hero-sub");
        heroSub.setWrapText(true);
        heroSub.setMaxWidth(560);

        // Feature cards row
        HBox cards = new HBox(18,
                card("Modpack", "78 mods", "Tous les mods Nyle + curation communautaire."),
                card("Mise à jour", "Auto", "Chaque lancement synchronise les nouveautés."),
                card("Connexion", account().isOffline() ? "Offline" : "Microsoft",
                     "Jouer sans compte premium est possible.")
        );
        cards.setAlignment(Pos.CENTER_LEFT);

        VBox v = new VBox(24, kicker, heroTitle, heroSub, cards);
        v.setAlignment(Pos.CENTER_LEFT);
        v.setPadding(new Insets(56, 56, 40, 56));
        return v;
    }

    private Region card(String kicker, String big, String sub) {
        Label k = new Label(kicker);
        k.getStyleClass().add("card-kicker");
        Label b = new Label(big);
        b.getStyleClass().add("card-big");
        Label s = new Label(sub);
        s.getStyleClass().add("card-sub");
        s.setWrapText(true);
        VBox box = new VBox(8, k, b, s);
        box.getStyleClass().add("feature-card");
        box.setPrefWidth(240);
        box.setPrefHeight(150);
        box.setPadding(new Insets(18, 20, 18, 20));
        Animations.hoverLift(box, 4);
        return box;
    }

    private Account account() {
        return accountRef; // set from constructor via field binding
    }

    private Account accountRef;

    // ── Bottom bar ──────────────────────────────────────────────────────────

    private Region buildBottomBar(Account account) {
        this.accountRef = account;

        statusLabel.getStyleClass().add("status-label");

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(progressBar.widthProperty());
        clip.heightProperty().bind(progressBar.heightProperty());
        clip.setArcWidth(6); clip.setArcHeight(6);
        progressBar.setClip(clip);
        progressBar.getStyleClass().add("progress-premium");
        progressBar.setPrefHeight(4);

        VBox statusCol = new VBox(8, statusLabel, progressBar);
        statusCol.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(statusCol, Priority.ALWAYS);

        // RAM slider
        Label ramLbl = new Label("4 Go");
        ramLbl.getStyleClass().add("ram-label");
        Slider ram = new Slider(2, 12, 4);
        ram.setBlockIncrement(1);
        ram.setMajorTickUnit(2);
        ram.setSnapToTicks(true);
        ram.getStyleClass().add("ram-slider");
        ram.setPrefWidth(160);
        ram.valueProperty().addListener((o, a, b) -> ramLbl.setText(b.intValue() + " Go"));
        VBox ramCol = new VBox(4,
                labelMuted("MÉMOIRE"),
                new HBox(10, ram, ramLbl) {{ setAlignment(Pos.CENTER_LEFT); }});
        ramCol.setAlignment(Pos.CENTER_LEFT);

        // Play button
        Button play = new Button("JOUER");
        play.getStyleClass().addAll("cta", "cta-play");
        play.setPrefWidth(220);
        play.setPrefHeight(64);
        play.setOnAction(e -> startPlay(account, (int)(ram.getValue() * 1024), play));
        playBreathe = Animations.breathe(play, 1.0, 1.02, Duration.seconds(2.8));

        HBox bar = new HBox(30, ramCol, statusCol, play);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(22, 56, 28, 56));
        bar.getStyleClass().add("bottom-bar");
        return bar;
    }

    private Label labelMuted(String s) {
        Label l = new Label(s);
        l.getStyleClass().add("muted-micro");
        return l;
    }

    // ── Launch pipeline ─────────────────────────────────────────────────────

    private void startPlay(Account account, int ramMb, Button play) {
        play.setDisable(true);
        if (playBreathe != null) playBreathe.stop();
        progressBar.setProgress(0);
        statusLabel.setText("Synchronisation du modpack…");

        new Thread(() -> {
            try {
                try {
                    ModpackUpdater updater = new ModpackUpdater(new ModpackUpdater.Listener() {
                        @Override public void onStatus(String line) {
                            Platform.runLater(() -> statusLabel.setText(line));
                        }
                        @Override public void onProgress(int d, int t, long bd, long bt) {
                            Platform.runLater(() ->
                                    progressBar.setProgress(t == 0 ? 0 : (double) d / t));
                        }
                    });
                    updater.sync();
                } catch (Exception syncErr) {
                    org.slf4j.LoggerFactory.getLogger("ModpackSync")
                            .warn("Modpack sync failed, continuing local: {}", syncErr.toString(), syncErr);
                    Platform.runLater(() -> statusLabel.setText(
                            "Sync KO (" + syncErr.getClass().getSimpleName() + "), lancement local…"));
                }

                MinecraftLauncher.launch(account, ramMb, true, new MinecraftLauncher.Listener() {
                    @Override public void onStatus(String s) {
                        Platform.runLater(() -> statusLabel.setText(s));
                    }
                    @Override public void onProgress(long d, long t) {
                        Platform.runLater(() ->
                                progressBar.setProgress(t <= 0 ? -1 : (double) d / t));
                    }
                });

                Platform.runLater(() -> {
                    statusLabel.setText("Minecraft lancé · " + Constants.SERVER_HOST);
                    progressBar.setProgress(1);
                    play.setDisable(false);
                    if (playBreathe != null) playBreathe.play();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    statusLabel.setText("Erreur: " + ex.getMessage());
                    play.setDisable(false);
                    if (playBreathe != null) playBreathe.play();
                });
            }
        }, "Launch-Thread").start();
    }
}
