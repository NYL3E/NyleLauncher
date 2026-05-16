package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.config.Constants;
import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.launch.MinecraftLauncher;
import fr.nylerp.launcher.update.ModpackUpdater;
import fr.nylerp.launcher.update.OptionalMods;
import fr.nylerp.launcher.update.SelfUpdater;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
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

    /** Background video players — two parallel loops with weighted random swap.
     *  98% of the time the next playthrough is {@link #player1}; 2% is
     *  {@link #player2}. {@link #forceVideo2Next} overrides the dice roll to
     *  force {@link #player2} once (the "agent" easter egg). Both players are
     *  pre-buffered and ALWAYS in the PLAYING state — we swap visibility on
     *  the {@link MediaView}s on end-of-media so the transition is instant
     *  (no demux/decode latency on the swap because the next clip is already
     *  decoded and rendering off-screen). */
    private MediaPlayer player1;
    private MediaPlayer player2;
    private MediaView   view1;
    private MediaView   view2;
    private MediaPlayer currentPlayer; // == player1 OR player2 at all times
    /** Set by the {@code "agent"} key-typed easter egg installed in
     *  {@link #installAgentEasterEgg}. Consumed on the NEXT end-of-media
     *  swap, forcing the upcoming clip to be {@link #player2}. */
    private static volatile boolean forceVideo2Next = false;
    private static final java.util.Random RNG = new java.util.Random();
    /** Ambient texture loop (3-min YouTube cut, low). Static so SettingsView can
     *  push a live volume update while it's playing. */
    private static MediaPlayer ambientPlayer;
    /** Foreground music loop. Static for the same reason — live volume updates. */
    private static MediaPlayer musicPlayer;

    /** Live volume setter for the ambient track — used by the Settings slider. */
    public static void setLiveAmbientVolume(double v) {
        if (ambientPlayer != null) {
            try { ambientPlayer.setVolume(Math.max(0, Math.min(1, v))); } catch (Throwable ignored) {}
        }
    }
    /** Live volume setter for the music track — used by the Settings slider. */
    public static void setLiveMusicVolume(double v) {
        if (musicPlayer != null) {
            try { musicPlayer.setVolume(Math.max(0, Math.min(1, v))); } catch (Throwable ignored) {}
        }
    }
    /** Hydrated from {@link Settings#launcherAudioMuted} so the mute choice
     *  persists across sessions — players who silenced the launcher don't
     *  need to do it again at every start. */
    private boolean audioMuted = Settings.get().launcherAudioMuted;
    private SVGPath muteIcon;

    public MainView(Account account, Runnable onLogout, Runnable onSettings) {
        getStyleClass().add("main-root");
        // BorderPane layout: bottom = 64 px play-bar (hard-clamped), center
        // = body StackPane. The Scene forces this BorderPane to the stage
        // content size; we don't touch min/pref/max (any value here can
        // either collapse the layout or push it past the visible scene
        // bounds on platforms whose title bar is bigger than expected).
        setCenter(buildContent(account, onLogout, onSettings));
        setBottom(buildBottomBar());

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

    /** Aspect ratio of the bundled launcher background videos
     *  (videolauncher1.mp4 and videolauncher2.mp4 are both 1942×1080 — see
     *  the ffmpeg recipe in the payload commit). The body StackPane height
     *  is pinned to {@code STAGE_WIDTH × BG_VIDEO_ASPECT} so the MediaView
     *  fills the body EXACTLY — no fallback region peeking out above the
     *  video, no overflow under the bar. Stage outer height
     *  ({@link LauncherApp}) is set to match interior + macOS chrome
     *  (28 px). If the source videos are re-encoded with a different
     *  aspect, bump both constants and the stage height together. */
    private static final double BG_VIDEO_ASPECT = 1080.0 / 1942.0;
    private static final double STAGE_INNER_WIDTH = 1000.0;
    /** Height the body StackPane MUST take so the bottom-anchored
     *  fitWidth-bound MediaView fills it edge-to-edge. */
    public static final double BODY_HEIGHT = STAGE_INNER_WIDTH * BG_VIDEO_ASPECT;

    private Region buildContent(Account account, Runnable onLogout, Runnable onSettings) {
        StackPane stack = new StackPane();
        stack.setStyle("-fx-background-color: #08080B;");
        // 2026-05-16 — pin the body height to exactly match the video's
        // displayed height at width 1000. Previously this was left to
        // BorderPane's auto-fill, so the body grew/shrank with the stage
        // and either (a) showed the legacy fallback image above the video
        // when there was vertical slack (user reported "média qui dépasse
        // au-dessus du fond du launcher") or (b) pushed the 64-px bottom
        // bar off-screen when the video's intrinsic pref height ate the
        // bar's slot. Pinning min=pref=max guarantees a fixed 556.13 px
        // body, so interior height = body + bar = exactly
        // BODY_HEIGHT + 64 px regardless of titlebar variance.
        stack.setMinHeight(BODY_HEIGHT);
        stack.setPrefHeight(BODY_HEIGHT);
        stack.setMaxHeight(BODY_HEIGHT);

        // Paint clip so children visually can't overflow into the bar slot
        // even if their intrinsic size temporarily exceeds the stack height
        // during layout.
        Rectangle bodyClip = new Rectangle();
        bodyClip.widthProperty().bind(stack.widthProperty());
        bodyClip.heightProperty().bind(stack.heightProperty());
        stack.setClip(bodyClip);

        // ── Background — ImageView fallback + MediaView for H.264 video.
        //    Both added directly as siblings of the overlay regions; the
        //    fallback is added FIRST (drawn behind), the video next (drawn
        //    in front when it plays). On codec failure we hide the video,
        //    leaving the static image visible.
        installBackground(stack);

        // ── Ambient audio — separate looping MediaPlayer at 30% by default; toggled
        //    via the mute button bottom-right of this panel. Created once for the
        //    lifetime of the launcher so the loop doesn't restart on view rebuild.
        startAmbientAudio();

        // ── Header capsule overlay (no more black strip above the picture) ──
        Region capsule = buildHeaderCapsule(account, onLogout, onSettings);
        StackPane.setAlignment(capsule, Pos.TOP_LEFT);
        StackPane.setMargin(capsule, new Insets(20, 0, 0, 20));

        // updateBanner will be stacked under the news panel — see VBox below.

        // ── Left overlay: brand mark (transparent PNG) + player count ─────
        // Uses the new logo-mark.png the user supplied — replaces the old
        // SVG-stroked NyleLogo "N" glyph. Smooth + cache so the upscale from
        // 875×875 doesn't shimmer on every layout pass.
        ImageView logo = new ImageView(new Image(
                getClass().getResourceAsStream("/images/logo-mark.png")));
        logo.setFitHeight(96);
        logo.setPreserveRatio(true);
        logo.setSmooth(true);
        logo.setCache(true);
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

        // ── Mute toggle, bottom-right of the body. The bottom bar lives in
        //    BorderPane.bottom (separate slot below this StackPane), so the
        //    body bottom edge already sits at the top of the bar — the mute
        //    only needs a small breathing gap from the body's own bottom.
        Region muteBtn = buildMuteButton();
        StackPane.setAlignment(muteBtn, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(muteBtn, new Insets(0, 22, 18, 0));

        // ── Payload version footer (bottom-left, low-contrast). Lets the
        //    user see at a glance which silent payload update they're on.
        Label versionLbl = new Label("v" + fr.nylerp.launcher.config.Constants.PAYLOAD_VERSION);
        versionLbl.setFont(Fonts.medium(10));
        versionLbl.setTextFill(Color.web("#6B6F7A"));
        versionLbl.setStyle("-fx-letter-spacing: 0.12em;");
        StackPane.setAlignment(versionLbl, Pos.BOTTOM_LEFT);
        StackPane.setMargin(versionLbl, new Insets(0, 0, 22, 28));

        stack.getChildren().addAll(leftBlock, rightColumn, capsule, muteBtn, versionLbl);
        return stack;
    }

    /** Install the static fallback image + the H.264 video MediaView directly
     *  as siblings in the body StackPane.
     *  <p>Critical detail: bind ONLY fitWidth, NOT fitHeight. With both
     *  bound to a property that starts at 0 (during initial layout, before
     *  the scene resizes the body), ImageView's docs spec that values ≤ 0
     *  trigger fallback to INTRINSIC size — and once the view latches onto
     *  the 4096×2081 intrinsic size on first render, the later binding
     *  update to 1000 doesn't recover correctly. Result was the
     *  "ultra-zoomed" crop the user reported. Pattern that has worked since
     *  1.0.18: only fitWidth bound, preserveRatio=true derives the height
     *  from aspect on every frame, body's paint clip handles any vertical
     *  overflow into the bar's slot.
     *
     *  <p>Since payload 1.0.49: two videos are stacked (videolauncher1.mp4
     *  98%, videolauncher2.mp4 2%). Both are pre-buffered MediaPlayers; on
     *  end-of-media we pick the next clip by weighted dice + the "agent"
     *  easter-egg override, seek the chosen player to ZERO and play(), and
     *  swap MediaView visibility. The off-screen player is paused, never
     *  disposed, so the next swap is instant. */
    private void installBackground(StackPane body) {
        // Static fallback as a Region with a BackgroundImage — canonical
        // JavaFX way to render a scaled image without ImageView's quirky
        // fitWidth=0 → intrinsic fallback that kept biting us. The Region
        // sizes itself to the parent (no prefSize inflation), and the
        // BackgroundSize is set to cover so the image fills the body while
        // preserving aspect (crops top/sides if needed).
        Region fallback = new Region();
        try {
            Image img = new Image(getClass().getResourceAsStream("/images/fond-launcher.png"));
            fallback.setBackground(new Background(new BackgroundImage(
                    img,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundRepeat.NO_REPEAT,
                    new BackgroundPosition(Side.LEFT, 0, true, Side.BOTTOM, 0, true),
                    // width=100%, height=100%, widthAsPercent=true, heightAsPercent=true,
                    // contain=false, cover=true → image fills the region preserving aspect
                    new BackgroundSize(1.0, 1.0, true, true, false, true)
            )));
        } catch (Throwable t) {
            System.err.println("[MainView] fallback image unavailable: " + t);
        }

        view1 = newBgMediaView(body);
        view2 = newBgMediaView(body);
        // view2 starts hidden — first clip is always videolauncher1.
        view2.setVisible(false);

        body.getChildren().add(fallback);
        body.getChildren().add(view1);
        body.getChildren().add(view2);

        try {
            player1 = newBgPlayer("/media/videolauncher1.mp4", view1);
            player2 = newBgPlayer("/media/videolauncher2.mp4", view2);
            if (player1 == null || player2 == null) {
                System.err.println("[MainView] one of the background videos failed to load");
                if (view1 != null) view1.setVisible(false);
                if (view2 != null) view2.setVisible(false);
                return;
            }
            // CRITICAL — both players run with cycleCount=INDEFINITE so they
            // NEVER enter the STOPPED state (which, in JavaFX, makes the
            // MediaView render NOTHING — that's the bug that exposed the
            // old fallback image when v1.0.49's cycleCount=1 + onEndOfMedia
            // swap fired). With INDEFINITE both videos keep producing
            // frames forever; we only toggle visibility on the two
            // MediaViews. The swap decision happens on `onRepeat`, which
            // JavaFX fires on the FX thread at every cycle boundary.
            player1.setOnRepeat(this::onPlayer1Repeat);
            player2.setOnRepeat(this::onPlayer2Repeat);
            currentPlayer = player1;
            // Both players auto-play; one is visible, the other off-screen
            // but already decoding so the swap is INSTANT (no demux/decode
            // latency at swap time).
            player1.play();
            player2.play();
        } catch (Throwable t) {
            System.err.println("[MainView] background videos unavailable: " + t);
            if (view1 != null) view1.setVisible(false);
            if (view2 != null) view2.setVisible(false);
        }

        installAgentEasterEgg(body);
    }

    /** Build a MediaView wired up exactly like the legacy launcher-bg view:
     *  fitWidth bound to body.widthProperty, preserveRatio + smooth ON,
     *  anchored at BOTTOM_CENTER. Returns the view, NOT yet attached to a
     *  player (caller adds the player). */
    private static MediaView newBgMediaView(StackPane body) {
        MediaView v = new MediaView();
        v.setPreserveRatio(true);
        v.setSmooth(true);
        v.fitWidthProperty().bind(body.widthProperty());
        StackPane.setAlignment(v, Pos.BOTTOM_CENTER);
        return v;
    }

    /** Construct a muted, INDEFINITELY-looping MediaPlayer for the given
     *  resource path, wire it to the supplied MediaView, and return it.
     *  cycleCount=INDEFINITE is the load-bearing detail: it keeps the
     *  player out of the STOPPED state at end-of-media, so the MediaView
     *  always has a decoded frame to display and never exposes the
     *  fallback image behind it. Swap logic is driven by `onRepeat` (fires
     *  at every cycle boundary, on the FX thread). */
    private MediaPlayer newBgPlayer(String resourcePath, MediaView attachedView) {
        try {
            String url = getClass().getResource(resourcePath).toExternalForm();
            Media media = new Media(url);
            media.setOnError(() -> {
                System.err.println("[MainView] media decode error (" + resourcePath + "): " + media.getError());
                Platform.runLater(() -> attachedView.setVisible(false));
            });
            MediaPlayer p = new MediaPlayer(media);
            p.setOnError(() -> {
                System.err.println("[MainView] mediaplayer error (" + resourcePath + "): " + p.getError());
                Platform.runLater(() -> attachedView.setVisible(false));
            });
            p.setMute(true);            // audio is on separate streams (ambient.mp3 + music.mp3)
            p.setCycleCount(MediaPlayer.INDEFINITE);
            attachedView.setMediaPlayer(p);
            return p;
        } catch (Throwable t) {
            System.err.println("[MainView] failed to build player for " + resourcePath + ": " + t);
            return null;
        }
    }

    /** Player1's cycle just ended and the next cycle is about to start.
     *  Roll the 98/2 dice — videolauncher1 stays on screen 98 % of the
     *  time. On the 2 % win (or when {@link #forceVideo2Next} was set by
     *  the "agent" easter egg) we swap visibility to view2 and let player2
     *  take the screen for ONE cycle. Both MediaPlayers keep playing in
     *  the background regardless, so the swap itself is just a visibility
     *  flip — no media reload, no decode warm-up, instant. */
    private void onPlayer1Repeat() {
        boolean force = forceVideo2Next;
        boolean pickVideo2 = force || RNG.nextDouble() < 0.02;
        if (!pickVideo2) return;       // 98 % — stay on view1, nothing to do
        forceVideo2Next = false;        // single-use override consumed
        view2.setVisible(true);
        view1.setVisible(false);
        currentPlayer = player2;
    }

    /** Player2's cycle just ended. By default we always swap back to
     *  videolauncher1 (the next dice roll happens on player1.onRepeat).
     *  Exception: if the user typed "agent" AGAIN while player2 was on
     *  screen, {@link #forceVideo2Next} is set and we keep videolauncher2
     *  for one more cycle. */
    private void onPlayer2Repeat() {
        if (forceVideo2Next) {
            forceVideo2Next = false;   // consume the re-trigger
            return;                     // stay on view2 for another cycle
        }
        view1.setVisible(true);
        view2.setVisible(false);
        currentPlayer = player1;
    }

    /** Install a scene-level KEY_TYPED filter that watches for the substring
     *  {@code "agent"} appearing in the rolling buffer of the last 8 typed
     *  characters. Match → set {@link #forceVideo2Next} so the NEXT clip
     *  rotation is forcibly videolauncher2. No UI, no sound, no log line —
     *  purely a side-channel. Existing focus targets (TextFields etc.)
     *  receive the events normally because we use an EventFilter that does
     *  NOT consume the event. */
    private void installAgentEasterEgg(StackPane body) {
        // The scene isn't attached at construction time; defer via sceneProperty().
        body.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene == null) return;
            final StringBuilder buf = new StringBuilder(8);
            scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_TYPED, e -> {
                String ch = e.getCharacter();
                if (ch == null || ch.isEmpty()) return;
                // Append every printable char (incl. casing) lower-cased for
                // a case-insensitive match — "Agent" still triggers.
                for (int i = 0; i < ch.length(); i++) {
                    char c = Character.toLowerCase(ch.charAt(i));
                    if (c < 0x20) continue; // skip control characters
                    buf.append(c);
                }
                if (buf.length() > 8) buf.delete(0, buf.length() - 8);
                if (buf.toString().endsWith("agent")) {
                    forceVideo2Next = true;
                    buf.setLength(0); // reset so a re-typing immediately re-arms
                }
                // Do NOT consume — TextFields and other focus targets must
                // still see the key event.
            });
        });
    }

    private void startAmbientAudio() {
        // Layered audio: ambient texture loop + foreground music loop. Both
        // ALWAYS auto-play and stay in the PLAYING state. Mute is controlled
        // exclusively via {@link MediaPlayer#setMute} — that flag is
        // state-independent, applied by the audio output stage every sample,
        // so toggling it never races with the player's state machine.
        //
        // The previous implementation used pause()/play() which only work
        // from a subset of states (READY/PAUSED/STOPPED/PLAYING/STALLED).
        // If the user clicked the mute button DURING a transitional state
        // (e.g. UNKNOWN before READY fired, or STALLED during a buffer
        // underrun) the call silently no-op'd and the audio drifted out of
        // sync with the icon — the "sometimes doesn't toggle" bug.
        if (ambientPlayer == null) {
            try {
                String url = getClass().getResource("/media/ambient.mp3").toExternalForm();
                Media media = new Media(url);
                media.setOnError(() -> System.err.println("[MainView] ambient media error: " + media.getError()));
                ambientPlayer = new MediaPlayer(media);
                ambientPlayer.setOnError(() -> System.err.println("[MainView] ambient player error: " + ambientPlayer.getError()));
                ambientPlayer.setVolume(Settings.get().ambientVolume);
                ambientPlayer.setCycleCount(MediaPlayer.INDEFINITE);
                ambientPlayer.setMute(audioMuted);
                ambientPlayer.setAutoPlay(true);
            } catch (Throwable t) {
                System.err.println("[MainView] ambient audio unavailable: " + t);
            }
        }
        if (musicPlayer == null) {
            try {
                String url = getClass().getResource("/media/music.mp3").toExternalForm();
                Media media = new Media(url);
                media.setOnError(() -> System.err.println("[MainView] music media error: " + media.getError()));
                musicPlayer = new MediaPlayer(media);
                musicPlayer.setOnError(() -> System.err.println("[MainView] music player error: " + musicPlayer.getError()));
                musicPlayer.setVolume(Settings.get().musicVolume);
                musicPlayer.setCycleCount(MediaPlayer.INDEFINITE);
                musicPlayer.setMute(audioMuted);
                musicPlayer.setAutoPlay(true);
            } catch (Throwable t) {
                System.err.println("[MainView] music unavailable: " + t);
            }
        }
    }

    /** Round 44×44 button with a speaker SVG glyph. Toggling calls
     *  {@link MediaPlayer#setMute(boolean)} on both audio layers and swaps
     *  the icon for an X-marked one. */
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
        // setMute is a per-sample audio-output flag, applied independently of
        // the player's state machine. Wrapped in try because on Windows N /
        // Education edition (no Media Feature Pack) the MediaPlayer can be
        // in HALTED state and setMute throws MediaException — the icon must
        // still flip so the user's choice is recorded and persisted for the
        // next start, where the audio may yet succeed.
        for (MediaPlayer p : new MediaPlayer[] { ambientPlayer, musicPlayer }) {
            if (p == null) continue;
            try { p.setMute(audioMuted); }
            catch (Throwable t) {
                System.err.println("[MainView] setMute failed (player likely halted): " + t);
            }
        }
        applyMuteIconShape();
        Settings.get().launcherAudioMuted = audioMuted;
        Settings.get().save();
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
        play.setPrefHeight(44);
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
        // Bar = play-button height (44 px) + 10 px breathing band above and
        // below = 64 px total. The vertical padding is what creates that
        // void on top and bottom of the button inside the dark strip.
        bar.setPadding(new Insets(10, 32, 10, 40));
        bar.setMinHeight(64);
        bar.setPrefHeight(64);
        bar.setMaxHeight(64);
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

                // Reconcile optional mods (Bobby, Litematica) AFTER the
                // manifest sync but BEFORE launching MC, so the mods/ folder
                // matches the user's settings before Fabric scans it.
                Platform.runLater(() -> status.setText("Mods optionnels…"));
                OptionalMods.applyAll();

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
