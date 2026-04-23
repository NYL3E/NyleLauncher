package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.config.Constants;
import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.launch.MinecraftLauncher;
import fr.nylerp.launcher.update.ModpackUpdater;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

public class MainView extends BorderPane {

    private final Label status = new Label("Prêt à jouer");
    private final ProgressBar progress = new ProgressBar(0);

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
        settingsBtn.getStyleClass().addAll("icon-btn", "icon-btn-emphasis");
        settingsBtn.setGraphic(Icons.gear(20, Color.web("#F4F4F7")));
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
        ColumnConstraints c1 = new ColumnConstraints(); c1.setHgrow(Priority.ALWAYS); c1.setHalignment(HPos.LEFT);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setHalignment(HPos.CENTER);
        ColumnConstraints c3 = new ColumnConstraints(); c3.setHgrow(Priority.ALWAYS); c3.setHalignment(HPos.RIGHT);
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

    // ── Content ─────────────────────────────────────────────────────────────

    private Region buildContent() {
        // Kicker + server status
        HBox meta = new HBox(14);
        meta.setAlignment(Pos.CENTER_LEFT);

        Label kicker = labelOf("ROLEPLAY · FRANCE · SAISON 0", "kicker");
        HBox dotBox = new HBox(6);
        dotBox.setAlignment(Pos.CENTER_LEFT);
        Circle dot = new Circle(4, Color.web("#22C55E"));
        Label online = labelOf("42 JOUEURS EN LIGNE", "online-label");
        dotBox.getChildren().addAll(dot, online);
        pulse(dot);

        Region sep = new Region();
        sep.setMinWidth(1); sep.setPrefWidth(1); sep.setMaxWidth(1);
        sep.setPrefHeight(12);
        sep.setStyle("-fx-background-color: #32323C;");

        meta.getChildren().addAll(kicker, sep, dotBox);

        // Title — apply font directly so weight truly pops
        Text t1 = new Text("Rejoignez\nl'"); t1.setFont(Fonts.bold(58));  t1.setFill(Color.web("#F4F4F7"));
        Text t2 = new Text("aventure.");      t2.setFont(Fonts.black(58)); t2.setFill(Color.web("#FF6A1A"));
        TextFlow title = new TextFlow(t1, t2);
        title.setMaxWidth(700);
        title.setLineSpacing(-6);

        Label sub = new Label("Un serveur roleplay exigeant et artisanal. Du vrai jeu, pas du grind.");
        sub.setFont(Fonts.medium(14));
        sub.setTextFill(Color.web("#A2A2AC"));
        sub.setWrapText(true);
        sub.setMaxWidth(560);

        // Feature grid — 4 glass tiles
        GridPane features = new GridPane();
        features.setHgap(16);
        features.setVgap(16);
        features.setMaxWidth(Double.MAX_VALUE);
        features.add(featureCard(voiceIcon(),  "Chat vocal",      "Voix proche entre joueurs."), 0, 0);
        features.add(featureCard(maskIcon(),   "Roleplay",        "Lore profond, events hebdo."), 1, 0);
        features.add(featureCard(coinIcon(),   "Économie joueur", "Commerces et gemmes premium."), 2, 0);
        features.add(featureCard(sparkIcon(),  "Cosmétiques",     "Pets, lootbox, skins exclus."), 3, 0);
        ColumnConstraints eq = new ColumnConstraints();
        eq.setPercentWidth(25);
        features.getColumnConstraints().addAll(eq, eq, eq, eq);

        VBox col = new VBox(20,
                meta,
                title,
                sub,
                spacer(8),
                features);
        col.setAlignment(Pos.CENTER_LEFT);
        col.setPadding(new Insets(36, 48, 28, 48));
        return col;
    }

    private VBox featureCard(SVGPath icon, String title, String desc) {
        icon.setFill(Color.web("#FF6A1A"));
        StackPane iconWrap = new StackPane(icon);
        iconWrap.setMinHeight(28);
        iconWrap.setAlignment(Pos.CENTER_LEFT);

        Label h = new Label(title);
        h.setFont(Fonts.bold(14));
        h.setTextFill(Color.web("#F4F4F7"));
        Label d = new Label(desc);
        d.setFont(Fonts.medium(11));
        d.setTextFill(Color.web("#A2A2AC"));
        d.setWrapText(true);
        d.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        VBox v = new VBox(6, iconWrap, spacer(4), h, d);
        v.getStyleClass().add("feature-card");
        v.setPadding(new Insets(16, 16, 16, 16));
        v.setPrefHeight(150);
        Animations.hoverLift(v, 3);
        return v;
    }

    // Small icons — Heroicons solid, orange fill
    private SVGPath voiceIcon() {
        SVGPath p = new SVGPath();
        p.setContent("M8 3.75a4 4 0 0 1 8 0v6a4 4 0 0 1-8 0v-6Z M5 9.75a.75.75 0 0 1 1.5 0 5.5 5.5 0 0 0 11 0 .75.75 0 0 1 1.5 0 7 7 0 0 1-6.25 6.957V19a.75.75 0 0 1-1.5 0v-2.293A7 7 0 0 1 5 9.75Z");
        p.setScaleX(1.1); p.setScaleY(1.1);
        return p;
    }
    private SVGPath maskIcon() {
        SVGPath p = new SVGPath();
        p.setContent("M12 2C7 2 3 6 3 11c0 4 2.5 7 5 8 1 .4 2 .5 3 .2V19c-.5-.3-1-.8-1-1.5 0-1 1-1.5 2-1.5s2 .5 2 1.5c0 .7-.5 1.2-1 1.5v.2c1 .3 2 .2 3-.2 2.5-1 5-4 5-8 0-5-4-9-9-9Zm-3.5 8a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3Zm7 0a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3Z");
        p.setScaleX(1.1); p.setScaleY(1.1);
        return p;
    }
    private SVGPath coinIcon() {
        SVGPath p = new SVGPath();
        p.setContent("M12 2.25a9.75 9.75 0 1 0 0 19.5 9.75 9.75 0 0 0 0-19.5Zm.75 4.5v.5c1.1.2 2 1 2 2h-1.5c0-.3-.4-.75-1.25-.75s-1.25.45-1.25.75c0 .35.35.6 1.7.95 1.6.4 2.8 1.05 2.8 2.55 0 1-.9 1.8-2 2v.5a.75.75 0 1 1-1.5 0v-.5c-1.1-.2-2-1-2-2h1.5c0 .3.4.75 1.25.75s1.25-.45 1.25-.75c0-.35-.35-.6-1.7-.95-1.6-.4-2.8-1.05-2.8-2.55 0-1 .9-1.8 2-2v-.5a.75.75 0 1 1 1.5 0Z");
        p.setScaleX(1.1); p.setScaleY(1.1);
        return p;
    }
    private SVGPath sparkIcon() {
        SVGPath p = new SVGPath();
        p.setContent("M9 2l1.2 3.8L14 7l-3.8 1.2L9 12 7.8 8.2 4 7l3.8-1.2L9 2Zm8 6l.9 2.6L20.5 12l-2.6.9L17 15.5l-.9-2.6L13.5 12l2.6-.9L17 8Zm-4 7l.7 2.2L15.9 18l-2.2.7L13 21l-.7-2.3L10.1 18l2.2-.8L13 15Z");
        p.setScaleX(1.1); p.setScaleY(1.1);
        return p;
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

    // ── Bottom bar ──────────────────────────────────────────────────────────

    private Region buildBottomBar() {
        Label memKey = labelOf("MÉMOIRE", "micro");
        Label memVal = new Label((Settings.get().ramMb / 1024) + " Go");
        memVal.getStyleClass().add("ram-val");
        VBox mem = new VBox(4, memKey, memVal);

        status.getStyleClass().add("status");
        progress.getStyleClass().add("progress");
        progress.setPrefHeight(3);
        progress.setMaxWidth(Double.MAX_VALUE);
        VBox mid = new VBox(8, status, progress);
        mid.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(mid, Priority.ALWAYS);

        Button play = new Button("JOUER");
        play.getStyleClass().add("btn-play");
        play.setFont(Fonts.black(30));
        play.setTextFill(Color.WHITE);
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
                    Platform.runLater(() -> status.setText(
                            "Sync KO, lancement local…"));
                }

                Account account = fr.nylerp.launcher.auth.AuthManager.loadSaved();
                int ramMb = Settings.get().ramMb;
                MinecraftLauncher.launch(account, ramMb, true, new MinecraftLauncher.Listener() {
                    @Override public void onStatus(String s) {
                        Platform.runLater(() -> status.setText(s));
                    }
                    @Override public void onProgress(long d, long t) {
                        Platform.runLater(() -> progress.setProgress(t <= 0 ? -1 : (double) d / t));
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
