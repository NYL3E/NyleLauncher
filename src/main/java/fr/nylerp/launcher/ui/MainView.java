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
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.awt.Desktop;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

public class MainView extends BorderPane {

    private static final String DISCORD_URL = "https://discord.gg/nyle";
    private static final String WEBSITE_URL = "https://www.nylerp.fr";

    private final Label status = new Label("Prêt à jouer");
    private final ProgressBar progress = new ProgressBar(0);
    private HBox updateBanner;
    private Button playBtn;
    private Label playLabel;
    private SVGPath playIcon;
    // When true, clicking the main button runs the modpack sync instead of
    // launching. Set from background checks at startup.
    private volatile boolean modpackUpdatePending = false;
    private volatile String launcherUpdateUrl = null;
    private volatile String launcherUpdateTag = null;

    /** Background video player (looping, no audio — audio is on separate tracks). */
    private MediaPlayer videoPlayer;
    /** Ambient texture loop (3-min YouTube cut, low). */
    private MediaPlayer ambientPlayer;
    /** Foreground music loop ("Left to Bloom" full-length, max-quality). */
    private MediaPlayer musicPlayer;
    private boolean audioMuted = false;
    private SVGPath muteIcon;
    /** Volume for the layered ambient + music tracks. Both stay at the same level so
     *  the music sits naturally on top of the texture without needing a per-track mix. */
    private static final double AMBIENT_VOLUME = 0.08;
    private static final double MUSIC_VOLUME   = 0.08;

    public MainView(Account account, Runnable onLogout, Runnable onSettings) {
        getStyleClass().add("main-root");

        // No more setTop / setBottom — the body StackPane fills the entire window
        // and the header capsule, news panel, mute button and bottom bar all overlay
        // it. This lets the background video extend the full height (no break between
        // the body and the bottom-bar slot anymore) while the bar paints on top of
        // the lowest video region.
        StackPane body = (StackPane) buildContent(account, onLogout, onSettings);
        Region bottom = buildBottomBar();
        StackPane.setAlignment(bottom, Pos.BOTTOM_CENTER);
        body.getChildren().add(bottom);
        setCenter(body);

        SelfUpdater.check().thenAccept(info -> Platform.runLater(() -> {
            if (info.hasUpdate() && updateBanner != null) {
                updateBanner.setVisible(true);
                updateBanner.setManaged(true);
                ((Label) updateBanner.getChildren().get(1)).setText(
                        "Mise à jour " + info.latestTag() + " disponible");
                Button dl = (Button) updateBanner.getChildren().get(2);
                dl.setOnAction(e -> performSelfUpdate(info.latestTag(), info.releaseUrl(), dl));
                launcherUpdateUrl = info.releaseUrl();
                launcherUpdateTag = info.latestTag();
                refreshPlayButton();
            }
        }));

        // Check modpack in background — if the remote manifest is newer than
        // the cached one, flip the main button to "METTRE À JOUR" so the
        // player explicitly triggers the sync instead of it silently
        // happening on every launch.
        CompletableFuture.runAsync(() -> {
            boolean pending = ModpackUpdater.hasUpdate();
            Platform.runLater(() -> {
                modpackUpdatePending = pending;
                refreshPlayButton();
            });
        });
    }

    /**
     * Same as {@link #performSelfUpdate} but drives the giant Play button at the
     * bottom-right (which uses {@code playLabel} instead of its own text).
     */
    private void performLauncherUpdateOnPlay(Button play) {
        if (!SelfUpdater.canAutoUpdate()) {
            openBrowser(launcherUpdateUrl);
            return;
        }
        play.setDisable(true);
        playLabel.setText("0%");
        SelfUpdater.downloadUpdate(launcherUpdateTag, (done, total) -> {
            if (total > 0) {
                int pct = (int) Math.min(100L, done * 100L / total);
                Platform.runLater(() -> playLabel.setText(pct + "%"));
            }
        }).whenComplete((file, err) -> Platform.runLater(() -> {
            if (err != null) {
                playLabel.setText("RÉESSAYER");
                play.setDisable(false);
                return;
            }
            try {
                playLabel.setText("INSTALLATION");
                SelfUpdater.runInstaller(file);
                new Thread(() -> {
                    try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                    Platform.runLater(() -> { Platform.exit(); System.exit(0); });
                }).start();
            } catch (Exception ex) {
                playLabel.setText("RÉESSAYER");
                play.setDisable(false);
            }
        }));
    }

    /**
     * Downloads the new launcher installer for the current OS and runs it.
     * Falls back to opening the GitHub release page only if the OS isn't
     * supported by the auto-installer (currently only macOS).
     */
    private void performSelfUpdate(String tag, String fallbackUrl, Button dl) {
        if (!SelfUpdater.canAutoUpdate()) {
            // No installer for this OS yet — open the release page so the user sees the assets
            openBrowser(fallbackUrl);
            return;
        }
        dl.setDisable(true);
        dl.setText("0%");
        SelfUpdater.downloadUpdate(tag, (done, total) -> {
            if (total > 0) {
                int pct = (int) Math.min(100L, done * 100L / total);
                Platform.runLater(() -> dl.setText(pct + "%"));
            }
        }).whenComplete((file, err) -> Platform.runLater(() -> {
            if (err != null) {
                dl.setText("Réessayer");
                dl.setDisable(false);
                return;
            }
            try {
                dl.setText("Installation...");
                SelfUpdater.runInstaller(file);
                // Give the installer a moment to start before we release file locks
                new Thread(() -> {
                    try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                    Platform.runLater(() -> { Platform.exit(); System.exit(0); });
                }).start();
            } catch (Exception ex) {
                dl.setText("Réessayer");
                dl.setDisable(false);
            }
        }));
    }

    private void refreshPlayButton() {
        if (playLabel == null || playIcon == null) return;
        if (modpackUpdatePending || launcherUpdateUrl != null) {
            playLabel.setText("METTRE À JOUR");
            playLabel.setFont(Fonts.bold(15));
            playIcon.setVisible(false);
            playIcon.setManaged(false);
        } else {
            playLabel.setText("JOUER");
            playLabel.setFont(Fonts.bold(22));
            playIcon.setVisible(true);
            playIcon.setManaged(true);
        }
    }

    // ── Header capsule (overlay, not a dedicated top region) ────────────────

    private Region buildHeaderCapsule(Account account, Runnable onLogout, Runnable onSettings) {
        // Unified glass header capsule — fits its content, never stretches
        HBox capsule = new HBox(8);
        capsule.getStyleClass().add("header-capsule");
        capsule.setAlignment(Pos.CENTER_LEFT);
        capsule.setPadding(new Insets(3, 18, 3, 18));
        capsule.setMaxWidth(Region.USE_PREF_SIZE);
        capsule.setMaxHeight(Region.USE_PREF_SIZE);

        SkinHead skin = new SkinHead(account, 26);

        Label name = new Label(account.username());
        name.setFont(Fonts.semi(12));
        name.setTextFill(Color.WHITE);
        Label type = new Label(account.isOffline() ? "OFFLINE" : "MICROSOFT");
        type.setFont(Fonts.black(9));
        type.setTextFill(Color.WHITE);
        type.setStyle("-fx-letter-spacing: 0.14em; -fx-opacity: 0.75;");
        VBox userCol = new VBox(1, name, type);
        userCol.setAlignment(Pos.CENTER_LEFT);
        userCol.setPadding(new Insets(0, 10, 0, 8));

        Color iconColor = Color.WHITE;

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
        updateBanner.setMaxWidth(Region.USE_PREF_SIZE);
        updateBanner.setMaxHeight(Region.USE_PREF_SIZE);
        return capsule;
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

    // ── Body (fond image + header overlay + logo + glass news) ────────────

    private Region buildContent(Account account, Runnable onLogout, Runnable onSettings) {
        StackPane stack = new StackPane();
        stack.setStyle("-fx-background-color: #08080B;");

        // ── Video background — replaces the static fond-launcher.png. Looping silent
        //    H.264 .mp4 covers the body area (CSS object-fit:cover style — overflow
        //    clipped, no letterbox). Anchored BOTTOM_CENTER so when the source is taller
        //    than the body, the top of the video gets cropped instead of the bottom.
        MediaView videoView = buildBackgroundVideo(stack);
        StackPane.setAlignment(videoView, Pos.BOTTOM_CENTER);
        stack.getChildren().add(videoView);

        // ── Ambient audio — separate looping MediaPlayer at 30% by default; toggled
        //    via the mute button bottom-right of this panel. Created once for the
        //    lifetime of the launcher so the loop doesn't restart on view rebuild.
        startAmbientAudio();

        // ── Header capsule overlay (no more black strip above the picture) ──
        Region capsule = buildHeaderCapsule(account, onLogout, onSettings);
        StackPane.setAlignment(capsule, Pos.TOP_LEFT);
        StackPane.setMargin(capsule, new Insets(20, 0, 0, 20));

        // updateBanner will be stacked under the news panel — see VBox below.

        // ── Left overlay: big white logo + player count ───────────────────
        NyleLogo logo = new NyleLogo(96, Color.WHITE);
        Circle dot = new Circle(6, Color.web("#22C55E"));
        pulse(dot);
        Label online = new Label("42 JOUEURS EN LIGNE");
        online.setFont(Fonts.medium(14));
        online.setTextFill(Color.web("#F4F4F7"));
        online.setStyle("-fx-letter-spacing: 0.16em;");
        HBox onlineRow = new HBox(10, dot, online);
        onlineRow.setAlignment(Pos.CENTER_LEFT);

        logo.setTranslateY(4);
        onlineRow.setTranslateY(-5);
        HBox leftBlock = new HBox(20, logo, onlineRow);
        leftBlock.setAlignment(Pos.CENTER_LEFT);
        leftBlock.setMaxWidth(Region.USE_PREF_SIZE);
        leftBlock.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(leftBlock, Pos.TOP_LEFT);
        StackPane.setMargin(leftBlock, new Insets(78, 0, 0, 30));

        // ── Right overlay: Glass Actualité panel + update banner attached below ───
        Region newsPanel = buildGlassNewsPanel();
        VBox rightColumn = new VBox(10, newsPanel, updateBanner);
        rightColumn.setAlignment(Pos.TOP_RIGHT);
        rightColumn.setMaxWidth(Region.USE_PREF_SIZE);
        rightColumn.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(rightColumn, Pos.TOP_RIGHT);
        StackPane.setMargin(rightColumn, new Insets(20, 22, 20, 0));

        // ── Mute toggle, bottom-right of the body, sitting JUST above the bottom bar
        //    (which is 72 px tall + a small breathing gap).
        Region muteBtn = buildMuteButton();
        StackPane.setAlignment(muteBtn, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(muteBtn, new Insets(0, 22, 86, 0));

        stack.getChildren().addAll(leftBlock, rightColumn, capsule, muteBtn);
        return stack;
    }

    /** Build a {@link MediaView} that plays the launcher background video on loop and
     *  covers the StackPane CSS-{@code object-fit: cover}-style: scaled so the smaller
     *  dimension fills the viewport, the other axis overflows and is clipped. The
     *  view is anchored to {@link Pos#BOTTOM_CENTER} so when the video is taller than
     *  the body, it's the TOP that gets cropped (per user request). The parent StackPane
     *  receives a Rectangle clip bound to its size so overflow never paints over the
     *  window chrome. */
    private MediaView buildBackgroundVideo(StackPane parent) {
        MediaView view = new MediaView();
        view.setPreserveRatio(true);
        view.setSmooth(true);
        try {
            String url = getClass().getResource("/media/launcher-bg.mp4").toExternalForm();
            Media media = new Media(url);
            videoPlayer = new MediaPlayer(media);
            videoPlayer.setMute(true);                   // audio is on separate streams
            videoPlayer.setCycleCount(MediaPlayer.INDEFINITE);
            videoPlayer.setAutoPlay(true);
            view.setMediaPlayer(videoPlayer);

            // Once the media reports its native dimensions, install listeners that
            // recompute the cover-scale on every viewport size change.
            videoPlayer.setOnReady(() -> {
                int mw = media.getWidth();
                int mh = media.getHeight();
                if (mw <= 0 || mh <= 0) {
                    // fallback if metadata wasn't decoded — fill anyway
                    view.fitWidthProperty().bind(parent.widthProperty());
                    view.fitHeightProperty().bind(parent.heightProperty());
                    return;
                }
                Runnable rescale = () -> {
                    double pw = parent.getWidth();
                    double ph = parent.getHeight();
                    if (pw <= 0 || ph <= 0) return;
                    double s = Math.max(pw / mw, ph / mh);   // cover, never letterbox
                    view.setFitWidth(mw * s);
                    view.setFitHeight(mh * s);
                };
                parent.widthProperty().addListener((o, a, b) -> rescale.run());
                parent.heightProperty().addListener((o, a, b) -> rescale.run());
                rescale.run();
            });

            // Clip the parent so the overflowing video doesn't paint outside body bounds.
            Rectangle clip = new Rectangle();
            clip.widthProperty().bind(parent.widthProperty());
            clip.heightProperty().bind(parent.heightProperty());
            parent.setClip(clip);
        } catch (Throwable t) {
            // If the codec is missing or the file is corrupt we'd rather show the empty
            // dark background than crash on startup. Log + carry on.
            System.err.println("[MainView] background video unavailable: " + t);
        }
        return view;
    }

    private void startAmbientAudio() {
        // Layered audio: ambient texture loop on track 1 + foreground music loop on
        // track 2. Both at the same low volume so they sit naturally together — no
        // per-track ducking needed. Singletons across view rebuilds to keep the loops
        // continuous instead of restarting from t=0 every time the screen swaps.
        if (ambientPlayer == null) {
            try {
                String url = getClass().getResource("/media/ambient.mp3").toExternalForm();
                Media media = new Media(url);
                ambientPlayer = new MediaPlayer(media);
                ambientPlayer.setVolume(AMBIENT_VOLUME);
                ambientPlayer.setCycleCount(MediaPlayer.INDEFINITE);
                ambientPlayer.setAutoPlay(true);
            } catch (Throwable t) {
                System.err.println("[MainView] ambient audio unavailable: " + t);
            }
        }
        if (musicPlayer == null) {
            try {
                String url = getClass().getResource("/media/music.mp3").toExternalForm();
                Media media = new Media(url);
                musicPlayer = new MediaPlayer(media);
                musicPlayer.setVolume(MUSIC_VOLUME);
                musicPlayer.setCycleCount(MediaPlayer.INDEFINITE);
                musicPlayer.setAutoPlay(true);
            } catch (Throwable t) {
                System.err.println("[MainView] music unavailable: " + t);
            }
        }
    }

    /** Round 44×44 button with a speaker SVG glyph. Toggling sets the audio player's
     *  volume to 0 / {@link #AMBIENT_VOLUME} and swaps the icon for an X-marked one. */
    private Region buildMuteButton() {
        Button btn = new Button();
        btn.setMinSize(44, 44);
        btn.setPrefSize(44, 44);
        btn.setMaxSize(44, 44);
        btn.setStyle(
            "-fx-background-color: rgba(8,8,11,0.62);" +
            "-fx-background-radius: 22;" +
            "-fx-border-color: rgba(255,255,255,0.12);" +
            "-fx-border-radius: 22;" +
            "-fx-border-width: 1;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 0;"
        );
        btn.setOnMouseEntered(e -> btn.setStyle(btn.getStyle().replace(
            "rgba(8,8,11,0.62)", "rgba(20,20,28,0.78)")));
        btn.setOnMouseExited(e -> btn.setStyle(btn.getStyle().replace(
            "rgba(20,20,28,0.78)", "rgba(8,8,11,0.62)")));
        btn.setTooltip(new Tooltip("Couper / réactiver le son d'ambiance"));

        muteIcon = new SVGPath();
        muteIcon.setFill(Color.web("#F4F4F7"));
        applyMuteIconShape();
        btn.setGraphic(muteIcon);

        btn.setOnAction(e -> toggleMute());
        return btn;
    }

    private void toggleMute() {
        audioMuted = !audioMuted;
        // Pause rather than mute volume — saves CPU on the decoder threads when the
        // user silences the launcher. Both layers (texture + music) toggle together
        // so the mute state always matches what the user hears.
        for (MediaPlayer p : new MediaPlayer[] { ambientPlayer, musicPlayer }) {
            if (p == null) continue;
            if (audioMuted) p.pause();
            else            p.play();
        }
        applyMuteIconShape();
    }

    private void applyMuteIconShape() {
        if (muteIcon == null) return;
        // Material Icons "volume_up" / "volume_off" path data, rendered at 18px.
        if (audioMuted) {
            muteIcon.setContent(
                "M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63z" +
                "M19 12c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28" +
                "-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71z" +
                "M4.27 3 3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18" +
                "v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3z" +
                "M12 4 9.91 6.09 12 8.18V4z");
            muteIcon.setScaleX(0.85);
            muteIcon.setScaleY(0.85);
        } else {
            muteIcon.setContent(
                "M3 9v6h4l5 5V4L7 9H3z" +
                "M16.5 12c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z" +
                "M14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 " +
                "7-8.77s-2.99-7.86-7-8.77z");
            muteIcon.setScaleX(0.85);
            muteIcon.setScaleY(0.85);
        }
    }

    private Region buildGlassNewsPanel() {
        Label title = new Label("ACTUALITÉ");
        title.setFont(Fonts.black(13));
        title.setTextFill(Color.web("#F4F4F7"));
        title.setStyle("-fx-letter-spacing: 0.22em;");

        // Chevron-shaped toggle — points down when expanded ("fold me up"),
        // rotates 180° to point up when collapsed ("unfold me").
        SVGPath arrow = new SVGPath();
        arrow.setContent("M 0 4 L 6 -2 L 12 4");
        arrow.setStroke(Color.web("#F4F4F7"));
        arrow.setStrokeWidth(2.0);
        arrow.setFill(Color.TRANSPARENT);
        arrow.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        arrow.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(12, title, spacer, arrow);
        header.setPadding(new Insets(18, 22, 14, 22));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-cursor: hand;");
        header.setPrefHeight(50);
        header.setMinHeight(50);
        header.setMaxHeight(50);

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

        // Scrollable news list so more items can be added later without resizing the panel
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPannable(true);
        scroll.getStyleClass().add("news-scroll");
        // Trackpad / mouse wheel — convert scroll delta directly to Vvalue change
        // so the panel responds even when the cursor is over the content (items
        // can otherwise swallow the event during their hover animation).
        scroll.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, e -> {
            double range = Math.max(1, body.getBoundsInLocal().getHeight()
                                     - scroll.getViewportBounds().getHeight());
            scroll.setVvalue(Math.max(0, Math.min(1,
                    scroll.getVvalue() - e.getDeltaY() / range)));
            e.consume();
        });

        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Force top position + collapse the default increment/decrement buttons
        // so the pill track shows with no chevrons top/bottom.
        Platform.runLater(() -> Platform.runLater(() -> {
            scroll.setVvalue(0);
            for (String sel : new String[]{".increment-button", ".decrement-button",
                                           ".increment-arrow", ".decrement-arrow"}) {
                for (Node n : scroll.lookupAll(sel)) {
                    n.setVisible(false);
                    if (n instanceof Region r) {
                        r.setPrefSize(0, 0);
                        r.setMinSize(0, 0);
                        r.setMaxSize(0, 0);
                    }
                }
            }
        }));

        VBox glassPanel = new VBox(header, divider, scroll);
        glassPanel.getStyleClass().add("glass-news-panel");
        glassPanel.setPrefWidth(320);
        glassPanel.setMaxWidth(320);
        glassPanel.setPrefHeight(260);
        glassPanel.setMaxHeight(260);
        // Allow the VBox to shrink below its content's minHeight during the
        // collapse animation. Without this, the scroll's minHeight would
        // floor the panel and the chevron toggle would do nothing visible.
        glassPanel.setMinHeight(0);

        // Clip so the scroll content disappears cleanly inside the rounded
        // corners as the panel animates from full height down to just-header.
        Rectangle panelClip = new Rectangle(320, 260);
        panelClip.setArcWidth(44);
        panelClip.setArcHeight(44);
        glassPanel.setClip(panelClip);
        glassPanel.heightProperty().addListener((o, a, b) -> panelClip.setHeight(b.doubleValue()));

        // Collapse / expand — clicking anywhere on the header animates the
        // panel between full (260) and just-the-header (~54). Arrow flips
        // 180° so the chevron always points in the direction content will go.
        final double EXPANDED_H = 260;
        final double COLLAPSED_H = 50;
        boolean[] expanded = { true };
        header.setOnMouseClicked(e -> {
            expanded[0] = !expanded[0];
            double targetH = expanded[0] ? EXPANDED_H : COLLAPSED_H;
            double targetRot = expanded[0] ? 0 : 180;
            Timeline tl = new Timeline(new KeyFrame(Duration.millis(260),
                    new KeyValue(glassPanel.prefHeightProperty(), targetH, Interpolator.EASE_BOTH),
                    new KeyValue(glassPanel.maxHeightProperty(), targetH, Interpolator.EASE_BOTH),
                    new KeyValue(arrow.rotateProperty(), targetRot, Interpolator.EASE_BOTH)));
            tl.play();
        });

        return glassPanel;
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

        // Triangle play icon — a tiny SVG so the button reads clearly as
        // "launch game" even before the text is processed by the reader.
        playIcon = new SVGPath();
        playIcon.setContent("M 3 1 L 3 15 L 15 8 Z");
        playIcon.setFill(Color.WHITE);
        playLabel = new Label("JOUER");
        playLabel.setFont(Fonts.bold(22));
        playLabel.setTextFill(Color.WHITE);
        HBox playContent = new HBox(10, playIcon, playLabel);
        playContent.setAlignment(Pos.CENTER);

        Button play = new Button();
        play.setGraphic(playContent);
        play.getStyleClass().add("btn-play");
        play.setPrefWidth(240);
        play.setPrefHeight(56);
        play.setOnAction(e -> {
            if (launcherUpdateUrl != null) {
                // Same auto-update flow as the small banner button — never open the browser
                performLauncherUpdateOnPlay(play);
            } else if (modpackUpdatePending) {
                runModpackUpdate(play);
            } else {
                startPlay(play);
            }
        });
        playBtn = play;

        HBox bar = new HBox(24, mid, play);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(14, 32, 16, 40));
        // Clamp height: in BorderPane.bottom this didn't matter, but inside a StackPane
        // with Pos.BOTTOM_CENTER alignment a HBox without a max-height grows vertically
        // until it fills the parent, and since .bottom-bar's CSS sets a solid #08080B
        // background it would paint a black slab over the entire body. Pinning all three
        // size dimensions to 72 keeps the bar a strip at the bottom.
        bar.setPrefHeight(72);
        bar.setMinHeight(72);
        bar.setMaxHeight(72);
        bar.setMaxWidth(Double.MAX_VALUE);   // still fills horizontally
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

    private void runModpackUpdate(Button play) {
        play.setDisable(true);
        playLabel.setText("MISE À JOUR…");
        progress.setProgress(0);
        status.setText("Téléchargement des mises à jour…");

        new Thread(() -> {
            try {
                new ModpackUpdater(new ModpackUpdater.Listener() {
                    @Override public void onStatus(String line) {
                        Platform.runLater(() -> status.setText(line));
                    }
                    @Override public void onProgress(int d, int t, long bd, long bt) {
                        Platform.runLater(() -> progress.setProgress(t == 0 ? 0 : (double) d / t));
                    }
                }).sync();
                Platform.runLater(() -> {
                    modpackUpdatePending = false;
                    status.setText("Prêt à jouer");
                    progress.setProgress(0);
                    play.setDisable(false);
                    refreshPlayButton();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    status.setText("Erreur: " + ex.getMessage());
                    play.setDisable(false);
                    refreshPlayButton();
                });
            }
        }, "Modpack-Update").start();
    }

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
                // Read Fabric version from the cached manifest so the launcher
                // always installs the loader specified by the modpack publisher,
                // not a hardcoded one that drifts out of sync.
                String fabricVer = readFabricVersionFromManifest();
                Process proc = MinecraftLauncher.launch(account, ramMb, true, fabricVer, new MinecraftLauncher.Listener() {
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
                    playLabel.setText("EN COURS");
                    playLabel.setFont(Fonts.bold(18));
                    playIcon.setVisible(false);
                    playIcon.setManaged(false);
                    play.setDisable(true);
                });

                // Watch the MC process and reset UI when it exits
                if (proc != null) {
                    new Thread(() -> {
                        try { proc.waitFor(); } catch (InterruptedException ignored) {}
                        Platform.runLater(() -> {
                            status.setText("Prêt à jouer");
                            progress.setProgress(0);
                            play.setDisable(false);
                            playIcon.setVisible(true);
                            playIcon.setManaged(true);
                            refreshPlayButton();
                        });
                    }, "MC-Watch").start();
                }
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    status.setText("Erreur: " + ex.getMessage());
                    play.setDisable(false);
                    playIcon.setVisible(true);
                    playIcon.setManaged(true);
                    refreshPlayButton();
                });
            }
        }, "Launch-Thread").start();
    }

    /**
     * Reads the cached manifest.json and returns loader.version (Fabric version),
     * or null if the file doesn't exist or the field is missing. The launcher
     * falls back to a hardcoded default in that case.
     */
    private static String readFabricVersionFromManifest() {
        try {
            java.nio.file.Path f = fr.nylerp.launcher.config.AppPaths.manifestCache();
            if (!java.nio.file.Files.exists(f)) return null;
            String json = java.nio.file.Files.readString(f);
            com.google.gson.JsonObject obj = com.google.gson.JsonParser
                    .parseString(json).getAsJsonObject();
            if (!obj.has("loader")) return null;
            com.google.gson.JsonObject loader = obj.getAsJsonObject("loader");
            return loader.has("version") ? loader.get("version").getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
