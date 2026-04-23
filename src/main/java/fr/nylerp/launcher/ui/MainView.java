package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.config.Constants;
import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.launch.MinecraftLauncher;
import fr.nylerp.launcher.update.ModpackUpdater;
import fr.nylerp.launcher.update.SelfUpdater;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.awt.Desktop;
import java.net.URI;

public class MainView extends BorderPane {

    private static final String DISCORD_URL = "https://discord.gg/nyle";
    private static final String WEBSITE_URL = "https://www.nylerp.fr";

    private final Label status = new Label("Prêt à jouer");
    private final ProgressBar progress = new ProgressBar(0);
    private HBox updateBanner;

    public MainView(Account account, Runnable onLogout, Runnable onSettings) {
        getStyleClass().add("main-root");

        setTop(buildTopBar(account, onLogout, onSettings));
        setCenter(buildContent());
        setBottom(buildBottomBar());

        if (getCenter() instanceof VBox v) {
            int i = 0;
            for (Node n : v.getChildren()) {
                Animations.enter(n, Duration.millis(100 + (i++) * 80));
            }
        }

        SelfUpdater.check().thenAccept(info -> Platform.runLater(() -> {
            if (info.hasUpdate() && updateBanner != null) {
                updateBanner.setVisible(true);
                updateBanner.setManaged(true);
                ((Label) updateBanner.getChildren().get(1)).setText(
                        "Mise à jour " + info.latestTag() + " disponible");
                Button dl = (Button) updateBanner.getChildren().get(2);
                dl.setOnAction(e -> openBrowser(info.releaseUrl()));
            }
        }));
    }

    // ── Top bar ─────────────────────────────────────────────────────────────

    private Region buildTopBar(Account account, Runnable onLogout, Runnable onSettings) {
        // Unified glass header capsule — generous padding, fully rounded
        HBox capsule = new HBox(8);
        capsule.getStyleClass().add("header-capsule");
        capsule.setAlignment(Pos.CENTER_LEFT);
        capsule.setPadding(new Insets(3, 18, 3, 18));

        SkinHead skin = new SkinHead(account, 30);

        Label name = new Label(account.username());
        name.setFont(Fonts.semi(13));
        name.setTextFill(Color.web("#F4F4F7"));
        Label type = new Label(account.isOffline() ? "OFFLINE" : "MICROSOFT");
        type.setFont(Fonts.black(9));
        type.setTextFill(Color.web("#6A6A74"));
        type.setStyle("-fx-letter-spacing: 0.14em;");
        VBox userCol = new VBox(1, name, type);
        userCol.setAlignment(Pos.CENTER_LEFT);
        userCol.setPadding(new Insets(0, 10, 0, 8));

        Color iconColor = Color.web("#A2A2AC");

        Button discordBtn = capsuleIcon(Icons.discord(15, iconColor), "Discord");
        discordBtn.setOnAction(e -> openBrowser(DISCORD_URL));

        Button webBtn = capsuleIcon(Icons.cart(15, iconColor), "Boutique nylerp.fr");
        webBtn.setOnAction(e -> openBrowser(WEBSITE_URL));

        Button settingsBtn = capsuleIcon(Icons.gear(15, iconColor), "Paramètres");
        settingsBtn.setOnAction(e -> { if (onSettings != null) onSettings.run(); });

        Button logoutBtn = capsuleIcon(Icons.arrowLeft(14, iconColor), "Déconnexion");
        logoutBtn.setOnAction(e -> onLogout.run());

        capsule.getChildren().addAll(
                skin, userCol,
                capsuleSep(),
                discordBtn, webBtn,
                capsuleSep(),
                settingsBtn, logoutBtn
        );

        updateBanner = buildUpdateBanner();
        updateBanner.setVisible(false);
        updateBanner.setManaged(false);

        // Capsule on the LEFT, update banner (if any) on the RIGHT
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, capsule, spacer, updateBanner);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 20, 0, 20));
        row.setPrefHeight(44);
        row.getStyleClass().add("top-bar-clean");
        return row;
    }

    private HBox buildUpdateBanner() {
        Circle dot = new Circle(3, Color.web("#FF6A1A"));
        Label msg = new Label("Mise à jour disponible");
        msg.setFont(Fonts.semi(11));
        msg.setTextFill(Color.web("#FF6A1A"));
        Button dl = new Button("Télécharger");
        dl.setFont(Fonts.bold(11));
        dl.getStyleClass().add("update-download-btn");
        HBox b = new HBox(8, dot, msg, dl);
        b.setAlignment(Pos.CENTER_LEFT);
        b.getStyleClass().add("update-banner");
        b.setPadding(new Insets(6, 10, 6, 12));
        return b;
    }

    private Button capsuleIcon(javafx.scene.Node icon, String tooltip) {
        Button b = new Button();
        b.getStyleClass().add("capsule-icon");
        b.setGraphic(icon);
        b.setPrefSize(32, 32);
        b.setMinSize(32, 32);
        b.setMaxSize(32, 32);
        if (tooltip != null) {
            Tooltip t = new Tooltip(tooltip);
            t.setShowDelay(Duration.millis(300));
            t.setShowDuration(Duration.seconds(8));
            t.getStyleClass().add("nyle-tooltip");
            b.setTooltip(t);
        }
        return b;
    }

    private Region capsuleSep() {
        Region r = new Region();
        r.setMinWidth(1); r.setPrefWidth(1); r.setMaxWidth(1);
        r.setMinHeight(20); r.setPrefHeight(20); r.setMaxHeight(20);
        r.setStyle("-fx-background-color: rgba(255,255,255,0.08);");
        return r;
    }

    private Label labelOf(String text, String styleClass) {
        Label l = new Label(text);
        l.getStyleClass().add(styleClass);
        return l;
    }

    // ── Content (maquette: fond image + logo left + glass news right) ──────

    private Region buildContent() {
        StackPane stack = new StackPane();
        // Use CSS background so the image auto-fits via -fx-background-size: cover.
        String imgUrl = getClass().getResource("/images/fond-launcher.png").toExternalForm();
        // Anchor the image so its BOTTOM edge lines up with the top of the play bar.
        // We pin the image to the bottom of the body region and size it by width
        // so we never crop the bottom (what the user sees is the full bottom strip
        // of the picture, landing right above the JOUER container).
        stack.setStyle(
                "-fx-background-image: url('" + imgUrl + "');" +
                "-fx-background-size: 100% auto;" +
                "-fx-background-position: center bottom;" +
                "-fx-background-repeat: no-repeat;"
        );

        // ── Left overlay: big white logo + player count ───────────────────
        NyleLogo logo = new NyleLogo(96, Color.WHITE);
        Circle dot = new Circle(4, Color.web("#22C55E"));
        pulse(dot);
        Label online = new Label("42 JOUEURS EN LIGNE");
        online.setFont(Fonts.black(11));
        online.setTextFill(Color.web("#F4F4F7"));
        online.setStyle("-fx-letter-spacing: 0.18em;");
        HBox onlineRow = new HBox(8, dot, online);
        onlineRow.setAlignment(Pos.CENTER_LEFT);

        HBox leftBlock = new HBox(18, logo, onlineRow);
        leftBlock.setAlignment(Pos.CENTER_LEFT);
        StackPane.setAlignment(leftBlock, Pos.TOP_LEFT);
        StackPane.setMargin(leftBlock, new Insets(0, 0, 0, 36));

        // ── Right overlay: Glass Actualité panel ───────────────────────────
        Region newsPanel = buildGlassNewsPanel();
        StackPane.setAlignment(newsPanel, Pos.TOP_RIGHT);
        StackPane.setMargin(newsPanel, new Insets(20, 22, 20, 0));

        stack.getChildren().addAll(leftBlock, newsPanel);
        return stack;
    }

    private Region buildGlassNewsPanel() {
        Label title = new Label("ACTUALITÉ");
        title.setFont(Fonts.black(13));
        title.setTextFill(Color.web("#F4F4F7"));
        title.setStyle("-fx-letter-spacing: 0.22em;");
        HBox header = new HBox(title);
        header.setPadding(new Insets(18, 22, 14, 22));
        header.setAlignment(Pos.CENTER_LEFT);

        Region divider = new Region();
        divider.setPrefHeight(1);
        divider.setMaxHeight(1);
        divider.setStyle("-fx-background-color: rgba(255,255,255,0.10);");

        VBox body = new VBox(12);
        body.setPadding(new Insets(16, 20, 20, 20));
        body.getChildren().addAll(
                newsItem("NOUVEAU", "Lootbox animées",
                        "Trois nouvelles lootbox — classique, légendaire, ultime."),
                newsItem("MAJ", "Mods optionnels",
                        "Litematica désormais disponible dans les paramètres."),
                newsItem("ÉVÉNEMENT", "Weekend XP double",
                        "Vendredi soir au dimanche — profitez-en pour monter.")
        );

        // 3 items fit — no scrollbar/chevrons needed
        VBox panel = new VBox(header, divider, body);
        panel.getStyleClass().add("glass-news-panel");
        panel.setPrefWidth(320);
        panel.setMaxWidth(320);
        panel.setPrefHeight(340);
        panel.setMaxHeight(340);
        return panel;
    }

    private Region newsItem(String tag, String title, String desc) {
        Label tagLbl = new Label(tag);
        tagLbl.setFont(Fonts.black(9));
        tagLbl.getStyleClass().add("news-tag");

        Label t = new Label(title);
        t.setFont(Fonts.bold(13));
        t.setTextFill(Color.web("#F4F4F7"));

        Label d = new Label(desc);
        d.setFont(Fonts.medium(11));
        d.setTextFill(Color.web("#A2A2AC"));
        d.setWrapText(true);

        VBox v = new VBox(6, tagLbl, t, d);
        v.getStyleClass().add("news-item-glass");
        v.setPadding(new Insets(12, 14, 12, 14));
        Animations.hoverLift(v, 2);
        return v;
    }

    private void pulse(Circle dot) {
        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(dot.scaleXProperty(), 1.0),
                        new KeyValue(dot.scaleYProperty(), 1.0),
                        new KeyValue(dot.opacityProperty(), 1.0)),
                new KeyFrame(Duration.seconds(0.9),
                        new KeyValue(dot.scaleXProperty(), 1.35),
                        new KeyValue(dot.scaleYProperty(), 1.35),
                        new KeyValue(dot.opacityProperty(), 0.55)),
                new KeyFrame(Duration.seconds(1.8),
                        new KeyValue(dot.scaleXProperty(), 1.0),
                        new KeyValue(dot.scaleYProperty(), 1.0),
                        new KeyValue(dot.opacityProperty(), 1.0))
        );
        tl.setCycleCount(Timeline.INDEFINITE); tl.play();
    }

    // ── Bottom bar (no more memory readout) ─────────────────────────────────

    private Region buildBottomBar() {
        status.getStyleClass().add("status");
        progress.getStyleClass().add("progress");
        progress.setPrefHeight(3);
        progress.setMaxWidth(Double.MAX_VALUE);
        VBox mid = new VBox(6, status, progress);
        mid.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(mid, Priority.ALWAYS);

        Button play = new Button("JOUER");
        play.getStyleClass().add("btn-play");
        play.setFont(Fonts.bold(22));
        play.setTextFill(Color.WHITE);
        play.setPrefWidth(200);
        play.setPrefHeight(56);
        play.setOnAction(e -> startPlay(play));

        HBox bar = new HBox(24, mid, play);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(14, 32, 16, 40));
        bar.setPrefHeight(72);
        bar.getStyleClass().add("bottom-bar");
        return bar;
    }

    private static Region spacer(double h) {
        Region r = new Region();
        r.setMinHeight(h); r.setPrefHeight(h); r.setMaxHeight(h);
        return r;
    }

    private void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ignored) {}
    }

    // ── Launch ──────────────────────────────────────────────────────────────

    private void startPlay(Button play) {
        play.setDisable(true);
        progress.setProgress(0);
        status.setText("Synchronisation du modpack…");

        new Thread(() -> {
            try {
                try {
                    ModpackUpdater updater = new ModpackUpdater(new ModpackUpdater.Listener() {
                        @Override public void onStatus(String line) {
                            Platform.runLater(() -> status.setText(line));
                        }
                        @Override public void onProgress(int d, int t, long bd, long bt) {
                            Platform.runLater(() -> progress.setProgress(t == 0 ? 0 : (double) d / t));
                        }
                    });
                    updater.sync();
                } catch (Exception syncErr) {
                    org.slf4j.LoggerFactory.getLogger("ModpackSync")
                            .warn("Modpack sync failed, continuing local: {}", syncErr.toString(), syncErr);
                    Platform.runLater(() -> status.setText("Sync KO, lancement local…"));
                }

                Account account = fr.nylerp.launcher.auth.AuthManager.loadSaved();
                int ramMb = Settings.get().ramMb;
                Process proc = MinecraftLauncher.launch(account, ramMb, true, new MinecraftLauncher.Listener() {
                    @Override public void onStatus(String s) {
                        Platform.runLater(() -> status.setText(s));
                    }
                    @Override public void onProgress(long d, long t) {
                        Platform.runLater(() -> progress.setProgress(t <= 0 ? -1 : (double) d / t));
                    }
                });

                Platform.runLater(() -> {
                    status.setText("Jeu en cours de lancement…");
                    progress.setProgress(1);
                    play.setText("EN COURS");
                    play.setDisable(true);
                });

                // Watch the MC process and reset UI when it exits
                if (proc != null) {
                    new Thread(() -> {
                        try { proc.waitFor(); } catch (InterruptedException ignored) {}
                        Platform.runLater(() -> {
                            status.setText("Prêt à jouer");
                            progress.setProgress(0);
                            play.setText("JOUER");
                            play.setDisable(false);
                        });
                    }, "MC-Watch").start();
                }
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    status.setText("Erreur: " + ex.getMessage());
                    play.setText("JOUER");
                    play.setDisable(false);
                });
            }
        }, "Launch-Thread").start();
    }
}
