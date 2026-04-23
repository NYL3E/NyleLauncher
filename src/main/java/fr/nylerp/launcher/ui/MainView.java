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
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
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

        // Layered: header + gold divider + content + bottom
        VBox top = new VBox(buildTopBar(account, onLogout, onSettings), new GoldDivider());
        setTop(top);
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
        HBox left = new HBox(12,
                new NyleLogo(20, Color.WHITE),
                labelOf("NYLERP", "brand"));
        left.setAlignment(Pos.CENTER_LEFT);

        // Unified glass header capsule — enough padding for the square skin to sit inside the pill
        HBox capsule = new HBox(6);
        capsule.getStyleClass().add("header-capsule");
        capsule.setAlignment(Pos.CENTER_LEFT);
        capsule.setPadding(new Insets(5, 12, 5, 12));

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

        HBox right = new HBox(12, updateBanner, capsule);
        right.setAlignment(Pos.CENTER_RIGHT);

        // No central navigation — nav moved into the right capsule
        GridPane bar = new GridPane();
        bar.getStyleClass().add("top-bar-clean");
        bar.add(left, 0, 0);
        bar.add(right, 1, 0);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setHgrow(Priority.ALWAYS); c1.setHalignment(HPos.LEFT);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setHgrow(Priority.ALWAYS); c2.setHalignment(HPos.RIGHT);
        bar.getColumnConstraints().addAll(c1, c2);
        bar.setPadding(new Insets(0, 20, 0, 24));
        bar.setPrefHeight(60);
        bar.setAlignment(Pos.CENTER);
        return bar;
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
            t.setShowDelay(Duration.millis(500));
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

    // ── Content ─────────────────────────────────────────────────────────────

    private Region buildContent() {
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
        sep.setStyle("-fx-background-color: rgba(255,255,255,0.08);");
        HBox meta = new HBox(14, kicker, sep, dotBox);
        meta.setAlignment(Pos.CENTER_LEFT);

        Text t1 = new Text("Rejoignez\nl'"); t1.setFont(Fonts.semi(50));  t1.setFill(Color.web("#F4F4F7"));
        Text t2 = new Text("aventure.");     t2.setFont(Fonts.bold(50));  t2.setFill(Color.web("#FF6A1A"));
        TextFlow title = new TextFlow(t1, t2);
        title.setMaxWidth(520);
        title.setLineSpacing(-6);

        Label sub = new Label("Un serveur roleplay exigeant et artisanal. Du vrai jeu, pas du grind.");
        sub.setFont(Fonts.medium(13));
        sub.setTextFill(Color.web("#A2A2AC"));
        sub.setWrapText(true);
        sub.setMaxWidth(520);

        GridPane features = new GridPane();
        features.setHgap(10);
        features.setVgap(10);
        features.setMaxWidth(Double.MAX_VALUE);
        features.add(featureCard(voiceIcon(),  "Chat vocal",    "Voix proche entre joueurs."), 0, 0);
        features.add(featureCard(maskIcon(),   "Roleplay",      "Lore profond, events hebdo."), 1, 0);
        features.add(featureCard(coinIcon(),   "Économie",      "Commerces et gemmes."), 0, 1);
        features.add(featureCard(sparkIcon(),  "Cosmétiques",   "Pets, lootbox, skins."), 1, 1);
        ColumnConstraints eq = new ColumnConstraints();
        eq.setPercentWidth(50);
        features.getColumnConstraints().addAll(eq, eq);

        VBox left = new VBox(14, meta, title, sub, features);
        left.setAlignment(Pos.TOP_LEFT);

        VBox right = buildNewsColumn();

        HBox row = new HBox(24, left, right);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(18, 40, 10, 40));
        return new VBox(row);
    }

    private VBox buildNewsColumn() {
        Label title = new Label("Actualités");
        title.setFont(Fonts.bold(16));
        title.setTextFill(Color.web("#F4F4F7"));

        VBox col = new VBox(8);
        col.setPrefWidth(300);
        col.setMaxWidth(300);

        col.getChildren().addAll(
                title,
                newsItem("NOUVEAU", "Lootbox animées", "Trois nouvelles lootbox — classique, légendaire, ultime."),
                newsItem("MAJ", "Mods optionnels", "Litematica disponible dans les paramètres."),
                newsItem("ÉVÉNEMENT", "Weekend XP double", "Du vendredi soir au dimanche — profitez-en.")
        );
        return col;
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

        VBox v = new VBox(4, tagLbl, t, d);
        v.getStyleClass().add("news-item");
        v.setPadding(new Insets(10, 12, 10, 12));
        Animations.hoverLift(v, 2);
        return v;
    }

    private VBox featureCard(SVGPath icon, String title, String desc) {
        icon.setFill(Color.web("#FF6A1A"));
        StackPane iconWrap = new StackPane(icon);
        iconWrap.setMinHeight(22);
        iconWrap.setAlignment(Pos.CENTER_LEFT);

        Label h = new Label(title);
        h.setFont(Fonts.semi(13));
        h.setTextFill(Color.web("#F4F4F7"));
        Label d = new Label(desc);
        d.setFont(Fonts.medium(11));
        d.setTextFill(Color.web("#A2A2AC"));
        d.setWrapText(true);
        VBox v = new VBox(4, iconWrap, spacer(2), h, d);
        v.getStyleClass().add("feature-card");
        v.setPadding(new Insets(12, 12, 12, 12));
        v.setPrefHeight(82);
        Animations.hoverLift(v, 3);
        return v;
    }

    private SVGPath voiceIcon() {
        SVGPath p = new SVGPath();
        p.setContent("M8 3.75a4 4 0 0 1 8 0v6a4 4 0 0 1-8 0v-6Z M5 9.75a.75.75 0 0 1 1.5 0 5.5 5.5 0 0 0 11 0 .75.75 0 0 1 1.5 0 7 7 0 0 1-6.25 6.957V19a.75.75 0 0 1-1.5 0v-2.293A7 7 0 0 1 5 9.75Z");
        return p;
    }
    private SVGPath maskIcon() {
        SVGPath p = new SVGPath();
        p.setContent("M12 2C7 2 3 6 3 11c0 4 2.5 7 5 8 1 .4 2 .5 3 .2V19c-.5-.3-1-.8-1-1.5 0-1 1-1.5 2-1.5s2 .5 2 1.5c0 .7-.5 1.2-1 1.5v.2c1 .3 2 .2 3-.2 2.5-1 5-4 5-8 0-5-4-9-9-9Zm-3.5 8a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3Zm7 0a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3Z");
        return p;
    }
    private SVGPath coinIcon() {
        SVGPath p = new SVGPath();
        p.setContent("M12 2.25a9.75 9.75 0 1 0 0 19.5 9.75 9.75 0 0 0 0-19.5Zm.75 4.5v.5c1.1.2 2 1 2 2h-1.5c0-.3-.4-.75-1.25-.75s-1.25.45-1.25.75c0 .35.35.6 1.7.95 1.6.4 2.8 1.05 2.8 2.55 0 1-.9 1.8-2 2v.5a.75.75 0 1 1-1.5 0v-.5c-1.1-.2-2-1-2-2h1.5c0 .3.4.75 1.25.75s1.25-.45 1.25-.75c0-.35-.35-.6-1.7-.95-1.6-.4-2.8-1.05-2.8-2.55 0-1 .9-1.8 2-2v-.5a.75.75 0 1 1 1.5 0Z");
        return p;
    }
    private SVGPath sparkIcon() {
        SVGPath p = new SVGPath();
        p.setContent("M9 2l1.2 3.8L14 7l-3.8 1.2L9 12 7.8 8.2 4 7l3.8-1.2L9 2Zm8 6l.9 2.6L20.5 12l-2.6.9L17 15.5l-.9-2.6L13.5 12l2.6-.9L17 8Zm-4 7l.7 2.2L15.9 18l-2.2.7L13 21l-.7-2.3L10.1 18l2.2-.8L13 15Z");
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
