package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.config.Constants;
import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.launch.MinecraftLauncher;
import fr.nylerp.launcher.update.ModpackUpdater;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

public class MainView extends BorderPane {

    private final Label status = new Label("Prêt à jouer");
    private final ProgressBar progress = new ProgressBar(0);

    public MainView(Account account, Runnable onLogout, Runnable onSettings) {
        getStyleClass().add("main-root");

        // Layered background with ambient orbs
        StackPane root = new StackPane();
        Circle orb = new Circle(210, Color.web("#FF6A1A", 0.14));
        orb.setTranslateX(340);
        orb.setTranslateY(-180);
        orb.setMouseTransparent(true);
        Circle orb2 = new Circle(160, Color.web("#4F46E5", 0.08));
        orb2.setTranslateX(-340);
        orb2.setTranslateY(160);
        orb2.setMouseTransparent(true);

        BorderPane layout = new BorderPane();
        layout.setTop(buildTopBar(account, onLogout, onSettings));
        layout.setCenter(buildHero(account));
        layout.setBottom(buildBottomBar());

        root.getChildren().addAll(orb, orb2, layout);
        setCenter(root);

        // Stagger in center kids
        if (layout.getCenter() instanceof HBox h) {
            int i = 0;
            for (Node n : h.getChildren()) Animations.enter(n, Duration.millis(120 + (i++) * 90));
        }
    }

    // ── Top bar ─────────────────────────────────────────────────────────────

    private Region buildTopBar(Account account, Runnable onLogout, Runnable onSettings) {
        HBox left = new HBox(12,
                new NyleLogo(20, Color.WHITE),
                labelOf("NYLERP", "brand"));
        left.setAlignment(Pos.CENTER_LEFT);

        HBox nav = new HBox(32,
                navLink("Accueil", true),
                navLink("Actualités", false),
                navLink("Discord", false));
        nav.setAlignment(Pos.CENTER);

        Button settingsBtn = new Button();
        settingsBtn.getStyleClass().add("icon-btn");
        settingsBtn.setGraphic(Icons.gear(17, Color.web("#9B9BA5")));
        settingsBtn.setOnAction(e -> { if (onSettings != null) onSettings.run(); });

        HBox userPill = new HBox(10,
                new SkinHead(account, 32),
                userCol(account),
                Icons.chevronDown(10, Color.web("#646470")));
        userPill.setAlignment(Pos.CENTER_LEFT);
        userPill.getStyleClass().add("user-pill");
        userPill.setPadding(new Insets(0, 12, 0, 4));
        userPill.setPrefHeight(40);

        Button logoutBtn = new Button();
        logoutBtn.getStyleClass().add("icon-btn");
        logoutBtn.setGraphic(Icons.arrowLeft(14, Color.web("#9B9BA5")));
        logoutBtn.setOnAction(e -> onLogout.run());

        HBox right = new HBox(10, settingsBtn, userPill, logoutBtn);
        right.setAlignment(Pos.CENTER_RIGHT);

        GridPane bar = new GridPane();
        bar.getStyleClass().add("top-bar");
        bar.add(left, 0, 0);
        bar.add(nav, 1, 0);
        bar.add(right, 2, 0);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        c1.setHalignment(HPos.LEFT);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHalignment(HPos.CENTER);
        ColumnConstraints c3 = new ColumnConstraints();
        c3.setHgrow(Priority.ALWAYS);
        c3.setHalignment(HPos.RIGHT);
        bar.getColumnConstraints().addAll(c1, c2, c3);
        bar.setPadding(new Insets(0, 24, 0, 24));
        bar.setPrefHeight(64);
        bar.setAlignment(Pos.CENTER);
        return bar;
    }

    private VBox userCol(Account account) {
        Label name = new Label(account.username());
        name.getStyleClass().add("user-name");
        Label type = new Label(account.isOffline() ? "OFFLINE" : "MICROSOFT");
        type.getStyleClass().add("user-type");
        VBox v = new VBox(2, name, type);
        v.setAlignment(Pos.CENTER_LEFT);
        return v;
    }

    private Label navLink(String text, boolean active) {
        Label l = new Label(text);
        l.getStyleClass().add(active ? "nav-link-active" : "nav-link");
        return l;
    }

    private Label labelOf(String text, String styleClass) {
        Label l = new Label(text);
        l.getStyleClass().add(styleClass);
        return l;
    }

    // ── Hero ────────────────────────────────────────────────────────────────

    private Region buildHero(Account account) {
        // Text block
        Label kicker = labelOf("SAISON 0  ·  FABRIC 1.21.1", "kicker");

        Text t1 = new Text("Le jeu\ntel que ");     t1.getStyleClass().add("hero-t");
        Text t2 = new Text("vous ");                t2.getStyleClass().add("hero-t-em");
        Text t3 = new Text("\nl'imaginez.");        t3.getStyleClass().add("hero-t");
        TextFlow title = new TextFlow(t1, t2, t3);
        title.setMaxWidth(500);
        title.setLineSpacing(-8);

        Label hsub = new Label(
                "Un serveur roleplay exigeant et artisanal. Le launcher synchronise " +
                "les mods automatiquement — il vous suffit de jouer.");
        hsub.setWrapText(true);
        hsub.setMaxWidth(500);
        hsub.getStyleClass().add("hero-sub");

        HBox stats = new HBox(40,
                stat("Mods", "78", false),
                stat("Version", "1.21.1", false),
                stat("Loader", "Fabric", false),
                stat("Serveur", "En ligne", true));
        stats.setAlignment(Pos.CENTER_LEFT);

        VBox textCol = new VBox(18, kicker, title, hsub,
                spacer(6), stats);
        textCol.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textCol, Priority.ALWAYS);

        // News card
        VBox newsCard = new VBox(12);
        newsCard.getStyleClass().add("news-card");
        newsCard.setPadding(new Insets(22, 22, 22, 22));
        newsCard.setMaxWidth(280);
        newsCard.setPrefWidth(280);

        Label tag = new Label("NOUVEAU");
        tag.getStyleClass().add("news-tag");

        Label ttl = new Label("Lootbox animées");
        ttl.getStyleClass().add("news-title");
        ttl.setWrapText(true);

        Label body = new Label(
                "Trois nouvelles lootbox GeckoLib rejoignent le serveur : classique, " +
                "légendaire et ultime. Cours le découvrir.");
        body.getStyleClass().add("news-body");
        body.setWrapText(true);

        Label meta = new Label("v1.3.3  ·  Il y a 2 jours");
        meta.getStyleClass().add("news-meta");

        newsCard.getChildren().addAll(tag, ttl, body, meta);
        Animations.hoverLift(newsCard, 3);

        HBox row = new HBox(40, textCol, newsCard);
        row.setAlignment(Pos.CENTER);
        row.setPadding(new Insets(48, 48, 24, 48));
        return row;
    }

    private VBox stat(String k, String v, boolean online) {
        Label key = labelOf(k.toUpperCase(), "stat-k");
        Label val = new Label(v);
        val.getStyleClass().add(online ? "stat-v-ok" : "stat-v");
        if (online) {
            Circle dot = new Circle(4, Color.web("#22C55E"));
            HBox inline = new HBox(8, dot, val);
            inline.setAlignment(Pos.CENTER_LEFT);
            VBox box = new VBox(4, key, inline);
            Timeline pulse = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(dot.scaleXProperty(), 1.0),
                            new KeyValue(dot.scaleYProperty(), 1.0), new KeyValue(dot.opacityProperty(), 1.0)),
                    new KeyFrame(Duration.seconds(0.9), new KeyValue(dot.scaleXProperty(), 1.35),
                            new KeyValue(dot.scaleYProperty(), 1.35), new KeyValue(dot.opacityProperty(), 0.55)),
                    new KeyFrame(Duration.seconds(1.8), new KeyValue(dot.scaleXProperty(), 1.0),
                            new KeyValue(dot.scaleYProperty(), 1.0), new KeyValue(dot.opacityProperty(), 1.0))
            );
            pulse.setCycleCount(Timeline.INDEFINITE); pulse.play();
            return box;
        }
        VBox box = new VBox(4, key, val);
        return box;
    }

    // ── Bottom bar ──────────────────────────────────────────────────────────

    private Region buildBottomBar() {
        // RAM display
        Label memKey = labelOf("MÉMOIRE", "micro");
        Label memVal = new Label((Settings.get().ramMb / 1024) + " Go");
        memVal.getStyleClass().add("ram-val");
        VBox mem = new VBox(4, memKey, memVal);

        // Status + progress
        status.getStyleClass().add("status");
        progress.getStyleClass().add("progress");
        progress.setPrefHeight(3);
        progress.setMaxWidth(Double.MAX_VALUE);
        VBox mid = new VBox(8, status, progress);
        mid.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(mid, Priority.ALWAYS);

        // Play button
        Button play = new Button("JOUER");
        play.getStyleClass().add("btn-play");
        play.setPrefWidth(260);
        play.setPrefHeight(72);
        play.setOnAction(e -> startPlay(play));

        HBox bar = new HBox(30, mem, mid, play);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(0, 32, 0, 32));
        bar.setPrefHeight(96);
        bar.getStyleClass().add("bottom-bar");
        return bar;
    }

    private static Region spacer(double h) {
        Region r = new Region();
        r.setMinHeight(h); r.setPrefHeight(h); r.setMaxHeight(h);
        return r;
    }

    private Account currentAccount() {
        // Walked up when needed
        return currentAcc;
    }

    private Account currentAcc;

    // ── Launch pipeline ─────────────────────────────────────────────────────

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
                            Platform.runLater(() ->
                                    progress.setProgress(t == 0 ? 0 : (double) d / t));
                        }
                    });
                    updater.sync();
                } catch (Exception syncErr) {
                    org.slf4j.LoggerFactory.getLogger("ModpackSync")
                            .warn("Modpack sync failed, continuing local: {}", syncErr.toString(), syncErr);
                    Platform.runLater(() -> status.setText(
                            "Sync KO (" + syncErr.getClass().getSimpleName() + "), lancement local…"));
                }

                Account account = fr.nylerp.launcher.auth.AuthManager.loadSaved();
                int ramMb = Settings.get().ramMb;
                MinecraftLauncher.launch(account, ramMb, true, new MinecraftLauncher.Listener() {
                    @Override public void onStatus(String s) {
                        Platform.runLater(() -> status.setText(s));
                    }
                    @Override public void onProgress(long d, long t) {
                        Platform.runLater(() ->
                                progress.setProgress(t <= 0 ? -1 : (double) d / t));
                    }
                });

                Platform.runLater(() -> {
                    status.setText("Minecraft lancé · " + Constants.SERVER_HOST);
                    progress.setProgress(1);
                    play.setDisable(false);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    status.setText("Erreur: " + ex.getMessage());
                    play.setDisable(false);
                });
            }
        }, "Launch-Thread").start();
    }
}
