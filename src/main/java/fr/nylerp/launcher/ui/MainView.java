package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.config.Constants;
import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.launch.MinecraftLauncher;
import fr.nylerp.launcher.update.ModpackUpdater;
import fr.nylerp.launcher.update.OptionalMods;
import fr.nylerp.launcher.update.SelfUpdater;
import fr.nylerp.launcher.update.ServerListSanitizer;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
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
    /** Vue « terminal » de {@link #progress} — non nulle sur un build DEV uniquement. */
    private fr.nylerp.launcher.ui.terminal.TerminalProgress termProgress;
    private HBox updateBanner;
    private Button playBtn;
    private Label playLabel;
    private SVGPath playIcon;
    /** 8-cube ring spinner shown next to the play label whenever a launch
     *  is in flight (modpack sync, Fabric install, MC bootstrap) and while
     *  the MC process is alive. Replaces the static play arrow so the
     *  player has an unambiguous "something is happening" affordance —
     *  previously the button just went disabled with no motion, which
     *  read as "frozen" when the launch took more than a few seconds. */
    private CubeSpinner playSpinner;
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
    private static final java.util.Random RNG = new java.util.Random();
    /** Set by the "agent" key-typed easter egg. Stays true until the next
     *  {@link #onPlayer1Repeat} fires AT video1's natural cycle boundary —
     *  i.e. video1 finishes its current loop UNINTERRUPTED, and only THEN
     *  do we substitute video2 for what would have been the next video1
     *  iteration. This is the user-explicit behaviour: do not cut video1,
     *  do not cut video2 before its end either; the easter egg simply
     *  swaps the next "natural" loop iteration. */
    private static volatile boolean queueVideo2NextLoop = false;
    /** Ambient texture loop (3-min YouTube cut, low). Static so SettingsView can
     *  push a live volume update while it's playing. */
    private static MediaPlayer ambientPlayer;
    /** Foreground music loop. Static for the same reason — live volume updates. */
    private static MediaPlayer musicPlayer;

    /** Live volume setter for the ambient track — used by the Settings slider.
     *  Also propagates the same volume to the easter-egg video2 audio so the
     *  player setting "Crépitement de feu" controls both layers (matches the
     *  user spec: videolauncher2 audio plays at the same percentage as the
     *  fire/ambient slider). videolauncher2 stays MUTED when off-screen and
     *  is un-muted only while view2 is visible — see {@link #setView2Audio}. */
    public static void setLiveAmbientVolume(double v) {
        double clamped = Math.max(0, Math.min(1, v));
        if (ambientPlayer != null) {
            try { ambientPlayer.setVolume(clamped); } catch (Throwable ignored) {}
        }
        if (instanceForLiveUpdate != null) {
            try { instanceForLiveUpdate.applyAmbientVolumeToVideo2(clamped); } catch (Throwable ignored) {}
        }
    }
    /** Tracking pointer to the most-recently-built MainView so the static
     *  {@link #setLiveAmbientVolume} hook (called from SettingsView via
     *  reflection-style static call) can reach the instance-bound player2.
     *  Stays valid for the lifetime of the current MainView; replaced when
     *  the user re-enters MainView (e.g. after going through SettingsView). */
    private static volatile MainView instanceForLiveUpdate;
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

    /** Hydraté depuis {@link Settings#launcherVideoDisabled} : le fond vidéo reste
     *  coupé d'une session à l'autre, sans quoi le joueur au petit PC devrait le
     *  recouper à chaque démarrage — ce qui est précisément la demande. */
    private boolean videoCoupee = Settings.get().launcherVideoDisabled;
    private SVGPath videoIcon;
    /** Le corps où vivent les MediaView. Mémorisé pour pouvoir RALLUMER la vidéo
     *  après coup : au démarrage coupé, les lecteurs n'existent pas encore et il
     *  faut donc pouvoir les construire au moment du clic, pas seulement les
     *  reprendre. */
    private StackPane corpsFond;

    private final java.util.function.Consumer<Account> onSwitchAccount;
    private final Runnable onAddAccount;
    private final Runnable onLogout;

    /** Legacy 3-arg constructor kept for any external caller — no switcher. */
    public MainView(Account account, Runnable onLogout, Runnable onSettings) {
        this(account, onLogout, onSettings, null, null);
    }

    public MainView(Account account, Runnable onLogout, Runnable onSettings,
                    java.util.function.Consumer<Account> onSwitchAccount,
                    Runnable onAddAccount) {
        this.onSwitchAccount = onSwitchAccount;
        this.onAddAccount = onAddAccount;
        this.onLogout = onLogout;
        getStyleClass().add("main-root");
        instanceForLiveUpdate = this;       // expose to static volume hooks
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
        setPlayBusy(true);          // 8-cube ring while we DL the installer
        SelfUpdater.downloadUpdate(launcherUpdateTag, (done, total) -> {
            if (total > 0) {
                int pct = (int) Math.min(100L, done * 100L / total);
                Platform.runLater(() -> playLabel.setText(pct + "%"));
            }
        }).whenComplete((file, err) -> Platform.runLater(() -> {
            if (err != null) {
                playLabel.setText("RÉESSAYER");
                play.setDisable(false);
                setPlayBusy(false);
                return;
            }
            try {
                playLabel.setText("INSTALLATION");
                // Keep the spinner running — the installer is about to run
                // and the process will exit on its own (System.exit below).
                SelfUpdater.runInstaller(file);
                new Thread(() -> {
                    try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                    Platform.runLater(() -> { Platform.exit(); System.exit(0); });
                }).start();
            } catch (Exception ex) {
                playLabel.setText("RÉESSAYER");
                play.setDisable(false);
                setPlayBusy(false);
            }
        }));
    }

    /** Toggle the Play button between "idle arrow" and "8-cube spinner".
     *  Centralised so all three call sites (launcher update, modpack
     *  update, game launch) flip both bits the same way — previously
     *  only {@link #startPlay} wired the spinner, so clicking
     *  {@code METTRE À JOUR} just disabled the button with no motion. */
    private void setPlayBusy(boolean busy) {
        // Canal DEV : la barre terminal n'anime son curseur d'attente QUE pendant un travail réel.
        // Au repos elle reste strictement figée — pas d'animation permanente sur l'écran d'accueil.
        if (termProgress != null) termProgress.setBusy(busy);
        if (playSpinner == null || playIcon == null) return;
        if (busy) {
            playSpinner.start();
            playIcon.setVisible(false);
            playIcon.setManaged(false);
        } else {
            playSpinner.stop();
            playIcon.setVisible(true);
            playIcon.setManaged(true);
        }
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
        boolean dev = fr.nylerp.launcher.config.Constants.DEV;
        if (modpackUpdatePending || launcherUpdateUrl != null) {
            playLabel.setText("METTRE À JOUR");
            playLabel.setFont(dev ? fr.nylerp.launcher.ui.terminal.TerminalTheme.mono(15) : Fonts.bold(15));
            playIcon.setVisible(false);
            playIcon.setManaged(false);
        } else {
            playLabel.setText("JOUER");
            playLabel.setFont(dev ? fr.nylerp.launcher.ui.terminal.TerminalTheme.mono(20) : Fonts.bold(22));
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
        userCol.setPadding(new Insets(0, 6, 0, 8));

        // ── Account chip : skin + name + chevron, opens the roster menu ──
        SVGPath chevron = new SVGPath();
        chevron.setContent("M 0 0 L 4 4 L 8 0");
        chevron.setStroke(Color.web("#FFFFFF", 0.75));
        chevron.setStrokeWidth(1.6);
        chevron.setFill(Color.TRANSPARENT);
        HBox accountChip = new HBox(2, skin, userCol, chevron);
        accountChip.setAlignment(Pos.CENTER_LEFT);
        accountChip.setPadding(new Insets(2, 8, 2, 2));
        accountChip.setStyle("-fx-background-radius: 10; -fx-cursor: hand;");
        accountChip.setOnMouseEntered(e ->
                accountChip.setStyle("-fx-background-radius: 10; -fx-cursor: hand;"
                        + "-fx-background-color: rgba(255,255,255,0.07);"));
        accountChip.setOnMouseExited(e ->
                accountChip.setStyle("-fx-background-radius: 10; -fx-cursor: hand;"));
        accountChip.setOnMouseClicked(e -> showAccountMenu(accountChip, account));

        Color iconColor = Color.WHITE;

        Button discordBtn = capsuleIcon(Icons.discord(15, iconColor), "Discord");
        discordBtn.setOnAction(e -> openBrowser(DISCORD_URL));

        Button webBtn = capsuleIcon(Icons.cart(15, iconColor), "Boutique nylerp.fr");
        webBtn.setOnAction(e -> openBrowser(WEBSITE_URL));

        Button settingsBtn = capsuleIcon(Icons.gear(15, iconColor), "Paramètres");
        settingsBtn.setOnAction(e -> { if (onSettings != null) onSettings.run(); });

        Button logoutBtn = capsuleIcon(Icons.arrowLeft(14, iconColor), "Se déconnecter (retire ce compte)");
        logoutBtn.setOnAction(e -> onLogout.run());

        capsule.getChildren().addAll(
                accountChip,
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

    /** Glass popup listing the saved accounts (max 3) : click to switch,
     *  plus an "Ajouter un compte" row (disabled at 3/3). */
    private void showAccountMenu(javafx.scene.Node anchor, Account current) {
        java.util.List<Account> roster = fr.nylerp.launcher.auth.AccountStore.list();

        VBox box = new VBox(2);
        box.setPadding(new Insets(8));
        box.setStyle(
                "-fx-background-color: linear-gradient(to bottom, rgba(32,26,52,0.97), rgba(20,16,36,0.97));"
                + "-fx-background-radius: 14;"
                + "-fx-border-color: rgba(255,255,255,0.12);"
                + "-fx-border-radius: 14; -fx-border-width: 1;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.55), 24, 0.2, 0, 6);");

        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.setAutoHide(true);

        for (Account acc : roster) {
            boolean isCurrent = acc.type() == current.type()
                    && (acc.isOffline()
                        ? acc.username().equalsIgnoreCase(current.username())
                        : acc.uuid() != null && acc.uuid().equalsIgnoreCase(current.uuid()));

            Label n = new Label(acc.username());
            n.setFont(Fonts.semi(12));
            n.setTextFill(Color.WHITE);
            Label t = new Label(acc.isOffline() ? "OFFLINE" : "MICROSOFT");
            t.setFont(Fonts.black(8));
            t.setTextFill(Color.web("#A2A2AC"));
            t.setStyle("-fx-letter-spacing: 0.14em;");
            VBox col = new VBox(0, n, t);
            col.setAlignment(Pos.CENTER_LEFT);

            javafx.scene.shape.Circle activeDot = new javafx.scene.shape.Circle(3,
                    isCurrent ? Color.web("#FF8128") : Color.TRANSPARENT);
            Region grow = new Region();
            HBox.setHgrow(grow, Priority.ALWAYS);

            // ── Bouton « Retirer » → « Confirmer » (2 étapes) ─────────────────────
            Label removeBtn = new Label("Retirer");
            removeBtn.setFont(Fonts.semi(10));
            removeBtn.setTextFill(Color.web("#8A8A93"));
            removeBtn.setPadding(new Insets(3, 8, 3, 8));
            String removeBase = "-fx-background-radius: 8; -fx-cursor: hand;"
                    + "-fx-border-color: rgba(255,255,255,0.14); -fx-border-radius: 8; -fx-border-width: 1;";
            removeBtn.setStyle(removeBase);
            final boolean[] confirming = { false };
            removeBtn.setOnMouseEntered(e -> { if (!confirming[0]) removeBtn.setStyle(removeBase
                    + "-fx-background-color: rgba(255,255,255,0.06);"); });
            removeBtn.setOnMouseExited(e -> { if (!confirming[0]) removeBtn.setStyle(removeBase); });
            removeBtn.setOnMouseClicked(e -> {
                e.consume();   // ne PAS déclencher le switch de compte de la ligne
                if (!confirming[0]) {
                    // 1er clic → bascule en confirmation (rouge)
                    confirming[0] = true;
                    removeBtn.setText("Confirmer");
                    removeBtn.setTextFill(Color.web("#FF5C5C"));
                    removeBtn.setStyle("-fx-background-radius: 8; -fx-cursor: hand;"
                            + "-fx-background-color: rgba(255,60,60,0.16);"
                            + "-fx-border-color: rgba(255,92,92,0.55); -fx-border-radius: 8; -fx-border-width: 1;");
                } else {
                    // 2e clic → retrait effectif du compte
                    boolean wasActive = fr.nylerp.launcher.auth.AccountStore.remove(acc);
                    popup.hide();
                    if (wasActive) {
                        // le compte actif a été retiré → bascule vers le suivant, ou logout si plus aucun
                        Account na = fr.nylerp.launcher.auth.AccountStore.active();
                        if (na != null && onSwitchAccount != null) onSwitchAccount.accept(na);
                        else if (onLogout != null) onLogout.run();
                    } else {
                        showAccountMenu(anchor, current);   // rouvre le menu avec la liste à jour
                    }
                }
            });

            HBox row = new HBox(10, new SkinHead(acc, 22), col, grow, activeDot, removeBtn);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(7, 12, 7, 8));
            row.setMinWidth(210);
            String base = "-fx-background-radius: 10;" + (isCurrent ? "" : "-fx-cursor: hand;");
            row.setStyle(base);
            if (!isCurrent && onSwitchAccount != null) {
                row.setOnMouseEntered(e -> row.setStyle(base
                        + "-fx-background-color: rgba(255,255,255,0.07);"));
                row.setOnMouseExited(e -> row.setStyle(base));
                row.setOnMouseClicked(e -> { popup.hide(); onSwitchAccount.accept(acc); });
            }
            box.getChildren().add(row);
        }

        Region sep = new Region();
        sep.setMinHeight(1); sep.setPrefHeight(1); sep.setMaxHeight(1);
        sep.setStyle("-fx-background-color: rgba(255,255,255,0.10);");
        VBox.setMargin(sep, new Insets(4, 4, 4, 4));
        box.getChildren().add(sep);

        boolean full = roster.size() >= fr.nylerp.launcher.auth.AccountStore.MAX_ACCOUNTS;
        Label plus = new Label(full
                ? "Comptes au maximum (3/3)"
                : "+  Ajouter un compte (" + roster.size() + "/3)");
        plus.setFont(Fonts.semi(11));
        plus.setTextFill(full ? Color.web("#6F6F78") : Color.web("#FF8128"));
        HBox addRow = new HBox(plus);
        addRow.setAlignment(Pos.CENTER_LEFT);
        addRow.setPadding(new Insets(7, 12, 7, 10));
        if (!full && onAddAccount != null) {
            addRow.setStyle("-fx-background-radius: 10; -fx-cursor: hand;");
            addRow.setOnMouseEntered(e -> addRow.setStyle(
                    "-fx-background-radius: 10; -fx-cursor: hand;"
                    + "-fx-background-color: rgba(255,129,40,0.12);"));
            addRow.setOnMouseExited(e -> addRow.setStyle(
                    "-fx-background-radius: 10; -fx-cursor: hand;"));
            addRow.setOnMouseClicked(e -> { popup.hide(); onAddAccount.run(); });
        }
        box.getChildren().add(addRow);

        popup.getContent().add(box);
        javafx.geometry.Bounds b = anchor.localToScreen(anchor.getBoundsInLocal());
        popup.show(anchor.getScene().getWindow(), b.getMinX() - 6, b.getMaxY() + 8);
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
        // La pastille qui respire n'existe que dans la disposition de production : en DEV elle
        // n'est jamais ajoutée à la scène. Sans cette garde, sa Timeline tournait quand même,
        // indéfiniment, sur un nœud détaché — une animation permanente que personne ne voit.
        if (!fr.nylerp.launcher.config.Constants.DEV) pulse(dot);
        Label online = new Label("— JOUEURS EN LIGNE");
        online.setFont(Fonts.medium(14));
        online.setTextFill(Color.web("#F4F4F7"));
        online.setStyle("-fx-letter-spacing: 0.16em;");
        // Live player count fetched from the Pterodactyl-backed status
        // endpoint (cf. site-mc /api/server). 30s polling keeps the UI
        // honest without hammering the server. Replaces the hard-coded
        // 42 placeholder.
        // Un SEUL abonnement : en canal DEV seul le formatage change (ligne de terminal au lieu
        // de la capitale espacée). Démarrer le sondage deux fois doublerait les requêtes.
        final boolean devFmt = fr.nylerp.launcher.config.Constants.DEV;
        fr.nylerp.launcher.net.ServerStatus.start(count -> javafx.application.Platform.runLater(() -> {
            if (devFmt) {
                online.setText("> joueurs ...... " + (count >= 0 ? String.valueOf(count) : "hors ligne"));
            } else if (count >= 0) {
                online.setText(count + " JOUEURS EN LIGNE");
            } else {
                online.setText("HORS LIGNE");
            }
        }));
        HBox onlineRow = new HBox(10, dot, online);
        onlineRow.setAlignment(Pos.CENTER_LEFT);

        logo.setTranslateY(4);
        onlineRow.setTranslateY(-5);
        Region leftBlock;
        if (fr.nylerp.launcher.config.Constants.DEV) {
            // Canal DEV : le logo devient un mot en ASCII art (dessiné une fois) et le compteur
            // rejoint un bloc d'état de session façon sortie de commande. Le compteur en ligne
            // reste le MÊME Label, donc le sondage ServerStatus branché plus haut continue de
            // l'alimenter — on ne duplique pas la source de vérité.
            online.setText("> joueurs ...... --");
            online.setStyle("");
            VBox devBlock = new VBox(10,
                    fr.nylerp.launcher.ui.terminal.TerminalSkin.marqueAscii("NYLE", 190),
                    fr.nylerp.launcher.ui.terminal.TerminalSkin.blocInfos(online));
            devBlock.setAlignment(Pos.TOP_LEFT);
            leftBlock = devBlock;
        } else {
            HBox row = new HBox(20, logo, onlineRow);
            row.setAlignment(Pos.CENTER_LEFT);
            leftBlock = row;
        }
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

        // ── Lecture / pause du fond vidéo, juste à GAUCHE du bouton de son.
        //    Marge droite = 22 (marge du son) + 44 (sa largeur) + 10 (écart) = 76, pour que les
        //    deux pastilles forment une paire régulière plutôt que deux boutons posés côte à côte.
        //    Absent du canal DEV : son fond est un Canvas, il n'y a aucune vidéo à couper.
        Region videoBtn = fr.nylerp.launcher.config.Constants.DEV ? null : buildVideoButton();
        if (videoBtn != null) {
            StackPane.setAlignment(videoBtn, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(videoBtn, new Insets(0, 76, 18, 0));
        }

        // ── Payload version footer (bottom-left, low-contrast). Lets the
        //    user see at a glance which silent payload update they're on.
        Label versionLbl = new Label("v" + fr.nylerp.launcher.config.Constants.runningPayloadVersion());
        versionLbl.setFont(Fonts.medium(10));
        versionLbl.setTextFill(Color.web("#6B6F7A"));
        versionLbl.setStyle("-fx-letter-spacing: 0.12em;");
        StackPane.setAlignment(versionLbl, Pos.BOTTOM_LEFT);
        StackPane.setMargin(versionLbl, new Insets(0, 0, 22, 28));

        stack.getChildren().addAll(leftBlock, rightColumn, capsule, muteBtn, versionLbl);
        if (videoBtn != null) stack.getChildren().add(videoBtn);
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
        // ── Canal DEV : fond terminal au lieu des deux vidéos ────────────────────────────────
        // Le thème DEV n'a pas d'arrière-plan vidéo : le fond est peint une fois sur un Canvas
        // (cf. TerminalSkin.fondTerminal). On sort AVANT de construire les MediaPlayer, donc
        // aucun décodage H.264 ne tourne — c'est aussi ce qui rend l'écran DEV moins gourmand
        // que la prod au repos. view1/view2/player1/player2 restent nuls ; tous leurs autres
        // points d'usage (mute, easter egg, volume) sont déjà gardés contre null.
        if (fr.nylerp.launcher.config.Constants.DEV) {
            body.getChildren().add(fr.nylerp.launcher.ui.terminal.TerminalSkin.fondTerminal());
            installAgentEasterEgg(body);
            return;
        }

        corpsFond = body;

        // ── Fond vidéo coupé par le joueur : on s'arrête AVANT toute construction ────────────
        // Même sortie anticipée que le canal DEV ci-dessus, et pour la même raison : ce qui coûte
        // cher n'est pas d'AFFICHER la vidéo, c'est de la DÉCODER. Ne pas construire les
        // MediaPlayer est donc le seul moyen de tenir la promesse faite au joueur — masquer la
        // MediaView aurait laissé deux flux H.264 tourner pour personne. Le fond uni du corps
        // (#08080B) prend le relais, exactement comme pendant la fenêtre de décodage au démarrage.
        if (videoCoupee) {
            installAgentEasterEgg(body);
            return;
        }

        // 2026-05-16 — fallback Region with fond-launcher.png REMOVED. The
        // image was the cause of the "vieille photo qui flash" the player
        // saw when navigating Settings → Home: a freshly-built MainView
        // would render the fallback for the ~50–200 ms between when the
        // MediaPlayer was constructed and when its first decoded frame
        // arrived on the MediaView, exposing the legacy png briefly. The
        // body's own background-color (#08080B) covers that decode window
        // now, and LauncherApp caches the MainView instance so subsequent
        // navigations don't tear down + re-instantiate the players at all.

        construireVideos(body);
        installAgentEasterEgg(body);
    }

    /**
     * Construit et lance les deux lecteurs de fond. Extrait de {@link #installBackground} pour
     * être REJOUABLE : quand le joueur rallume la vidéo, les lecteurs n'existent pas encore (le
     * démarrage coupé ne les crée pas), il ne suffit donc pas de reprendre la lecture.
     *
     * <p>Les MediaView sont insérées en TÊTE de la pile ({@code add(0, …)}) et non ajoutées à la
     * fin. À la première construction le corps est vide et cela revient au même ; au rallumage, en
     * revanche, la pile contient déjà les panneaux, les boutons et le pied de page — une simple
     * addition aurait posé la vidéo PAR-DESSUS toute l'interface.
     */
    private void construireVideos(StackPane body) {
        if (view1 != null || player1 != null) return;      // déjà en place

        view1 = newBgMediaView(body);
        view2 = newBgMediaView(body);
        // view2 starts hidden — first clip is always videolauncher1.
        view2.setVisible(false);

        body.getChildren().add(0, view1);
        body.getChildren().add(1, view2);

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
            // player1 has no audio track; player2 ships with AAC audio so
            // when the secret video plays the player hears the campfire
            // crackle synced to it.
            player1.setMute(true);
            player2.setMute(true);
            applyAmbientVolumeToVideo2(Settings.get().ambientVolume);
            // player1: INDEFINITE looping, always playing, default view.
            player1.play();
            // player2: pre-pause AT FRAME 0 so a future easter-egg trigger
            // can call play() without paying the demux + seek-to-zero
            // latency the user described as a "mini délai" — switching
            // from view1 to view2 is now a state transition (PAUSED →
            // PLAYING) which JavaFX completes within one render pulse.
            // The setOnReady callback fires once the MediaPlayer enters
            // READY (after media parsing); we use it as a safe point to
            // seek + pause without racing the auto-play. The player
            // remains in PAUSED at offset 0 until {@link #onPlayer1Repeat}
            // resumes it on a cycle boundary or the "agent" easter egg
            // queues a swap.
            player2.setOnReady(() -> {
                try {
                    player2.seek(javafx.util.Duration.ZERO);
                    player2.pause();
                } catch (Throwable ignored) {}
            });
            player2.play();
        } catch (Throwable t) {
            System.err.println("[MainView] background videos unavailable: " + t);
            if (view1 != null) view1.setVisible(false);
            if (view2 != null) view2.setVisible(false);
        }
    }

    /**
     * Coupe le fond vidéo et REND les ressources : les lecteurs sont libérés, pas mis en pause.
     *
     * <p>Une pause suffirait à arrêter le décodage, mais laisserait deux pipelines multimédia et
     * leurs tampons en mémoire — sur la machine modeste qui motive cette option, c'est justement ce
     * qu'on veut rendre. {@code dispose()} après avoir détaché la vue est l'ordre imposé par JavaFX :
     * libérer un lecteur encore rattaché à une MediaView laisse celle-ci pointer sur un pipeline
     * mort.
     */
    private void arreterVideos() {
        for (MediaView v : new MediaView[] { view1, view2 }) {
            if (v == null) continue;
            v.setVisible(false);
            try { v.setMediaPlayer(null); } catch (Throwable ignored) {}
            if (corpsFond != null) corpsFond.getChildren().remove(v);
        }
        for (MediaPlayer p : new MediaPlayer[] { player1, player2 }) {
            if (p == null) continue;
            try { p.stop(); }    catch (Throwable ignored) {}
            try { p.dispose(); } catch (Throwable ignored) {}
        }
        view1 = null; view2 = null;
        player1 = null; player2 = null;
        currentPlayer = null;
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

    /** Player1's cycle just ended at its natural boundary. If the "agent"
     *  easter egg has queued a video2 swap (or the 2 % random dice rolls
     *  it), seek player2 back to frame 0 so video2 starts FROM THE START,
     *  then flip view visibility. Player1 keeps looping in the background
     *  but its onRepeat from this point on is a no-op (the
     *  {@code currentPlayer != player1} guard), so video2 plays its full
     *  cycle uninterrupted. */
    private void onPlayer1Repeat() {
        // Guard: this method may also fire while we're showing view2 (player1
        // is INDEFINITE and keeps looping in the background). Don't act
        // unless player1 is the one currently on screen, otherwise the 2 %
        // dice or a stale queueVideo2NextLoop flag could cut video2 short.
        if (currentPlayer != player1) return;
        boolean queued    = queueVideo2NextLoop;
        boolean pickRand2 = RNG.nextDouble() < 0.02;
        if (!queued && !pickRand2) return;            // stay on view1
        queueVideo2NextLoop = false;                  // single-use, consume now
        // player2 has been pre-paused at frame 0 (see installBackground +
        // onPlayer2Repeat). play() flips PAUSED → PLAYING within one render
        // pulse, so the swap is now instant — no demux, no seek, no
        // decoder warm-up the way the previous "seek then show" path had.
        view2.setVisible(true);
        view1.setVisible(false);
        try { player2.setMute(audioMuted); } catch (Throwable ignored) {}   // respect global mute
        try { player2.play(); } catch (Throwable ignored) {}
        currentPlayer = player2;
    }

    /** Player2's cycle just ended. Always swap back to videolauncher1, then
     *  re-arm player2: mute, pause, seek to frame 0 — so the next time the
     *  easter egg or the 2 % dice triggers a swap, player2 is already
     *  positioned at frame 0 and play() is instant. */
    private void onPlayer2Repeat() {
        if (currentPlayer != player2) return;
        view1.setVisible(true);
        view2.setVisible(false);
        try {
            player2.setMute(true);
            player2.pause();
            player2.seek(javafx.util.Duration.ZERO);
        } catch (Throwable ignored) {}
        currentPlayer = player1;
    }

    /** Apply the campfire/ambient slider volume to the easter-egg video2's
     *  audio track. Range 0.0 – 1.0 on input. The user asked (2026-05-16)
     *  for the secret-clip audio to be +100 % vs. the ambient slider so the
     *  campfire crackling reads louder in context — we multiply by 2 and
     *  clamp to [0, 1]. Mute state is managed separately by
     *  {@link #onPlayer1Repeat}/{@link #onPlayer2Repeat} on swap — the
     *  volume sticks regardless of mute. */
    private void applyAmbientVolumeToVideo2(double v) {
        if (player2 == null) return;
        double boosted = Math.min(1.0, Math.max(0.0, v) * 2.0);
        try { player2.setVolume(boosted); } catch (Throwable ignored) {}
    }

    /** Install a scene-level KEY_TYPED filter that watches for the substring
     *  {@code "agent"} appearing in the rolling buffer of the last 8 typed
     *  characters. Match → set {@link #queueVideo2NextLoop} so that at
     *  video1's NEXT natural cycle boundary (no cut), video2 is substituted
     *  for what would have been the next video1 iteration. Video2 then
     *  plays its FULL duration before video1 resumes — explicit user spec:
     *  do not cut video1, do not cut video2.
     *
     *  <p>Re-typing "agent" while video2 is already on screen re-arms the
     *  flag, so after video2 finishes + video1 plays one cycle, video2
     *  plays again. No UI feedback, no sound — purely a side-channel.
     *
     *  <p>Registration is defensive: if the scene is already attached at
     *  install time (e.g. when navigating between views), we register
     *  immediately; otherwise we listen on {@code sceneProperty()} for
     *  the first scene assignment. */
    private Scene agentFilterScene;
    private javafx.event.EventHandler<javafx.scene.input.KeyEvent> agentFilter;

    private void installAgentEasterEgg(StackPane body) {
        // Filter built ONCE and re-used across scene re-attachments. Without
        // this, the prior implementation accumulated a fresh filter every
        // time body.sceneProperty fired non-null (each Settings → Home
        // navigation, now that MainView is cached + reused across scenes),
        // causing the rolling buffer to spawn many times per keystroke.
        final StringBuilder buf = new StringBuilder();
        agentFilter = e -> {
            String ch = e.getCharacter();
            if (ch == null || ch.isEmpty()) return;
            for (int i = 0; i < ch.length(); i++) {
                char c = Character.toLowerCase(ch.charAt(i));
                if (c < 0x20 || c > 0x7E) continue;
                buf.append(c);
            }
            if (buf.length() > 8) buf.delete(0, buf.length() - 8);
            if (buf.toString().endsWith("agent")) {
                buf.setLength(0);
                queueVideo2NextLoop = true;
            }
            // EventFilter does not consume — TextFields still get the key.
        };

        Runnable sync = () -> {
            Scene now = body.getScene();
            if (now == agentFilterScene) return;
            if (agentFilterScene != null) {
                try { agentFilterScene.removeEventFilter(
                        javafx.scene.input.KeyEvent.KEY_TYPED, agentFilter); } catch (Throwable ignored) {}
            }
            if (now != null) {
                try { now.addEventFilter(
                        javafx.scene.input.KeyEvent.KEY_TYPED, agentFilter); } catch (Throwable ignored) {}
            }
            agentFilterScene = now;
        };
        sync.run();
        body.sceneProperty().addListener((obs, oldS, newS) -> sync.run());
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
        // Ce bouton est stylé EN LIGNE (pas via une classe CSS), donc terminal.css ne peut pas
        // l'atteindre : le canal DEV a besoin de sa propre variante, sinon il resterait la seule
        // pastille arrondie blanche au milieu d'une interface à angles droits.
        boolean dev = fr.nylerp.launcher.config.Constants.DEV;
        final String repos = dev ? "rgba(5,8,7,0.80)"  : "rgba(8,8,11,0.62)";
        final String survol= dev ? "rgba(34,255,136,0.22)" : "rgba(20,20,28,0.78)";
        final String cadre = dev ? "rgba(34,255,136,0.45)" : "rgba(255,255,255,0.12)";
        final String rayon = dev ? "0" : "22";
        btn.setStyle(
            "-fx-background-color: " + repos + ";" +
            "-fx-background-radius: " + rayon + ";" +
            "-fx-border-color: " + cadre + ";" +
            "-fx-border-radius: " + rayon + ";" +
            "-fx-border-width: 1;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 0;"
        );
        btn.setOnMouseEntered(e -> btn.setStyle(btn.getStyle().replace(repos, survol)));
        btn.setOnMouseExited(e -> btn.setStyle(btn.getStyle().replace(survol, repos)));
        btn.setTooltip(new Tooltip("Couper / réactiver le son d'ambiance"));

        muteIcon = new SVGPath();
        muteIcon.setFill(Color.web(dev ? "#22FF88" : "#F4F4F7"));
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
        // player2 (easter-egg video): only follow audioMuted if it's
        // currently on screen. Off-screen it MUST stay muted regardless,
        // otherwise its INDEFINITE background loop would leak audio under
        // view1 the moment the global mute is released.
        if (player2 != null && currentPlayer == player2) {
            try { player2.setMute(audioMuted); } catch (Throwable ignored) {}
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

    /**
     * LECTURE / PAUSE DU FOND VIDÉO — owner 2026-08-13 : « les petits PC ont du mal avec la vidéo ».
     *
     * <p>Même gabarit et même style que le bouton de son, canal DEV compris : les deux forment une
     * paire, et une pastille qui ne ressemblerait pas à sa voisine se lirait comme un élément
     * étranger. Le libellé de l'infobulle dit l'effet RÉEL (la vidéo ne sera pas relancée aux
     * prochains démarrages), parce que c'est un réglage qui survit à la fermeture — un joueur doit
     * pouvoir le comprendre sans avoir à le tester deux fois.
     */
    /**
     * Traduit une exception de lancement en phrase que le JOUEUR peut suivre.
     *
     * <p>Le libellé était {@code "Erreur: " + ex.getMessage()}. Sur les exceptions de fichier, ce
     * message vaut le CHEMIN et rien d'autre : un joueur a vu
     * {@code « Erreur: C:\…\jdk-21.0.12+8\bin/ucrtbase.dll »} — barre presque pleine, aucune idée de
     * quoi faire, et le problème revenait à chaque essai. La classe de l'exception, elle, dit
     * précisément ce qui s'est passé ; on s'en sert pour donner la marche à suivre.
     */
    private static String messageLisible(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String detail = cause.getMessage() == null ? "" : cause.getMessage();

        if (cause instanceof java.nio.file.AccessDeniedException
                || cause instanceof java.nio.file.FileSystemException) {
            return "Fichier bloqué par l'antivirus ou déjà utilisé — relance le launcher. "
                    + "Si ça persiste, autorise le dossier NyleLauncher dans ton antivirus.";
        }
        if (cause instanceof java.net.UnknownHostException) {
            return "Pas de connexion Internet (DNS injoignable) — vérifie ta connexion.";
        }
        if (cause instanceof java.net.SocketTimeoutException
                || cause instanceof java.net.ConnectException
                || cause instanceof java.net.http.HttpTimeoutException) {
            return "Connexion au serveur de mise à jour impossible — réessaie dans un instant.";
        }
        if (cause instanceof java.io.EOFException || detail.toLowerCase().contains("zip")) {
            return "Téléchargement incomplet — relance le launcher, il reprendra le fichier.";
        }
        // Un fichier dans un encodage inattendu ne doit plus JAMAIS être ce que voit un joueur :
        // « Input length = 3 » ne veut rien dire pour personne (une joueuse l'a eu le 29/08, son
        // jeu plantait et le launcher se cassait en lisant le journal qui l'expliquait).
        // Les lectures passent désormais par LectureTexte ; ce filet reste pour ce qu'on aurait
        // oublié, et il dit au moins quoi faire.
        if (cause instanceof java.nio.charset.CharacterCodingException) {
            return "Un fichier du jeu est illisible (encodage inattendu) — relance le launcher, "
                    + "il le remplacera. Si ça persiste, envoie un rapport au staff.";
        }
        // Le diagnostic de plantage a déjà rédigé une phrase pour le joueur : on la garde telle
        // quelle plutôt que de la préfixer d'un « Erreur : » qui la ferait passer pour un code.
        if (cause instanceof java.io.IOException && detail.length() > 40
                && (detail.startsWith("Le jeu") || detail.startsWith("Pas assez")
                    || detail.startsWith("Ta carte") || detail.startsWith("Un fichier")
                    || detail.startsWith("Un mod") || detail.startsWith("L'installation")
                    || detail.startsWith("Le moteur"))) {
            return detail;
        }
        return "Erreur : " + (detail.isBlank() ? cause.getClass().getSimpleName() : detail);
    }

    private Region buildVideoButton() {
        Button btn = new Button();
        btn.setMinSize(44, 44);
        btn.setPrefSize(44, 44);
        btn.setMaxSize(44, 44);
        boolean dev = fr.nylerp.launcher.config.Constants.DEV;
        final String repos = dev ? "rgba(5,8,7,0.80)"  : "rgba(8,8,11,0.62)";
        final String survol= dev ? "rgba(34,255,136,0.22)" : "rgba(20,20,28,0.78)";
        final String cadre = dev ? "rgba(34,255,136,0.45)" : "rgba(255,255,255,0.12)";
        final String rayon = dev ? "0" : "22";
        btn.setStyle(
            "-fx-background-color: " + repos + ";" +
            "-fx-background-radius: " + rayon + ";" +
            "-fx-border-color: " + cadre + ";" +
            "-fx-border-radius: " + rayon + ";" +
            "-fx-border-width: 1;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 0;"
        );
        btn.setOnMouseEntered(e -> btn.setStyle(btn.getStyle().replace(repos, survol)));
        btn.setOnMouseExited(e -> btn.setStyle(btn.getStyle().replace(survol, repos)));
        btn.setTooltip(new Tooltip(
                "Couper / relancer la vidéo de fond\nCoupée, elle ne se relancera plus au démarrage"));

        videoIcon = new SVGPath();
        videoIcon.setFill(Color.web(dev ? "#22FF88" : "#F4F4F7"));
        applyVideoIconShape();
        btn.setGraphic(videoIcon);

        btn.setOnAction(e -> toggleVideo());
        return btn;
    }

    /**
     * Bascule le fond vidéo, et RETIENT le choix.
     *
     * <p>Couper libère les lecteurs ({@link #arreterVideos}) plutôt que de les mettre en pause :
     * l'objet de ce bouton est de rendre du processeur et de la mémoire, pas de cacher des images.
     * Relancer reconstruit ce qui a été libéré — et fonctionne donc aussi après un démarrage où
     * les lecteurs n'ont jamais existé.
     */
    private void toggleVideo() {
        videoCoupee = !videoCoupee;
        try {
            if (videoCoupee) arreterVideos();
            else if (corpsFond != null) construireVideos(corpsFond);
        } catch (Throwable t) {
            // Un fond d'écran ne doit jamais empêcher de jouer : en cas d'échec on garde le fond
            // uni et on enregistre quand même le choix, sinon le joueur retrouverait la vidéo au
            // prochain démarrage alors qu'il vient tout juste de demander à s'en passer.
            System.err.println("[MainView] bascule du fond vidéo impossible : " + t);
        }
        applyVideoIconShape();
        Settings.get().launcherVideoDisabled = videoCoupee;
        Settings.get().save();
    }

    private void applyVideoIconShape() {
        if (videoIcon == null) return;
        // Material Icons « play_arrow » quand la vidéo est coupée (le bouton la RELANCE),
        // « pause » quand elle tourne (le bouton la COUPE) : l'icône annonce l'action, pas l'état.
        if (videoCoupee) {
            videoIcon.setContent("M8 5v14l11-7z");
        } else {
            videoIcon.setContent("M6 19h4V5H6v14zm8-14v14h4V5h-4z");
        }
        videoIcon.setScaleX(0.85);
        videoIcon.setScaleY(0.85);
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
        body.getChildren().add(buildOpeningNews());
        body.getChildren().add(buildPartenaireHosterfy());

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
        glassPanel.setPrefHeight(335);
        glassPanel.setMaxHeight(335);
        // Allow the VBox to shrink below its content's minHeight during the
        // collapse animation. Without this, the scroll's minHeight would
        // floor the panel and the chevron toggle would do nothing visible.
        glassPanel.setMinHeight(0);

        // Clip so the scroll content disappears cleanly inside the rounded
        // corners as the panel animates from full height down to just-header.
        Rectangle panelClip = new Rectangle(320, 335);
        panelClip.setArcWidth(44);
        panelClip.setArcHeight(44);
        glassPanel.setClip(panelClip);
        glassPanel.heightProperty().addListener((o, a, b) -> panelClip.setHeight(b.doubleValue()));

        // Collapse / expand — clicking anywhere on the header animates the
        // panel between full (260) and just-the-header (~54). Arrow flips
        // 180° so the chevron always points in the direction content will go.
        final double EXPANDED_H = 335;
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

    /** The single, headline announcement — the server opening — shown as a hero
     *  card: full-width image on top (rounded corners, ratio preserved) with the
     *  tag / title / text stacked underneath, keeping the existing glass DA. */
    private Region buildOpeningNews() {
        // Body content width: panel (320) minus the body's left/right padding (20+20).
        final double CARD_W = 280;

        // Hero image — loaded straight from the bundled resource. fitWidth pins it
        // to the card width, preserveRatio keeps the 1200×551 proportions, smooth
        // gives clean downscaling.
        ImageView cover = new ImageView(new Image(
                getClass().getResourceAsStream("/assets/news/ouverture.jpg")));
        cover.setFitWidth(CARD_W);
        cover.setPreserveRatio(true);
        cover.setSmooth(true);
        // Canal DEV : le filtre phosphore rend cette photo en vert vif, ce qui en faisait de loin
        // l'objet le plus lumineux de l'écran — l'œil y allait avant d'aller au bouton JOUER. On la
        // recule à mi-intensité : elle devient une image scannée sur un terminal, pas un projecteur.
        if (fr.nylerp.launcher.config.Constants.DEV) cover.setOpacity(0.42);

        Label tagLbl = new Label("OUVERTURE");
        tagLbl.setFont(Fonts.black(9));
        tagLbl.getStyleClass().add("news-tag");
        // Wrap so the pill hugs its text instead of stretching across the card.
        HBox tagRow = new HBox(tagLbl);
        tagRow.setAlignment(Pos.CENTER_LEFT);

        Label t = new Label("Ouverture du serveur");
        t.setFont(Fonts.bold(15));
        t.setTextFill(Color.web("#F4F4F7"));

        Label d = new Label("Rendez-vous le 8 juillet à 14h00 pour l'ouverture du serveur !");
        d.setFont(Fonts.medium(11));
        d.setTextFill(Color.web("#A2A2AC"));
        d.setWrapText(true);

        VBox textBlock = new VBox(7, tagRow, t, d);
        textBlock.setPadding(new Insets(13, 14, 14, 14));

        VBox card = new VBox(cover, textBlock);
        card.getStyleClass().add("news-item-glass");
        card.setPrefWidth(CARD_W);
        card.setMaxWidth(CARD_W);

        // Round the card (and, with it, the image's top corners) so the cover sits
        // flush edge-to-edge yet follows the card's rounded outline. Clip tracks the
        // card's live size so the text below is never cut off, whatever it wraps to.
        Rectangle cardClip = new Rectangle();
        cardClip.setArcWidth(24);
        cardClip.setArcHeight(24);
        cardClip.widthProperty().bind(card.widthProperty());
        cardClip.heightProperty().bind(card.heightProperty());
        card.setClip(cardClip);

        Animations.hoverLift(card, 2);
        return card;
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
        // Canal DEV : la ProgressBar reste la SOURCE de vérité (tous les setProgress du launcher
        // continuent de l'alimenter) mais n'est pas ajoutée à la scène ; c'est TerminalProgress,
        // abonnée à sa propriété, qui est affichée. Aucun appelant n'est modifié, donc le chemin
        // de mise à jour de la prod est intouché.
        VBox mid;
        if (fr.nylerp.launcher.config.Constants.DEV) {
            termProgress = new fr.nylerp.launcher.ui.terminal.TerminalProgress(progress.progressProperty());
            installStatusTerminal();
            mid = new VBox(4, status, termProgress);
        } else {
            mid = new VBox(6, status, progress);
        }
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
        if (fr.nylerp.launcher.config.Constants.DEV) {
            // Le libellé passe en chasse fixe pour que la recomposition au survol se fasse sans
            // que la largeur du bouton ne saute d'un caractère à l'autre.
            playLabel.setFont(fr.nylerp.launcher.ui.terminal.TerminalTheme.mono(20));
        }
        playSpinner = new CubeSpinner(28.0);   // fits inside the 44-px-tall button
        HBox playContent = new HBox(10, playIcon, playSpinner, playLabel);
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
        if (fr.nylerp.launcher.config.Constants.DEV) {
            // Survol : le libellé se recompose caractère par caractère. Le reste du survol
            // (cadre, fond, chevron) est traité en CSS — cf. .btn-play:hover dans terminal.css.
            fr.nylerp.launcher.ui.terminal.TerminalHover.recompose(play, playLabel);
        }

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

    /**
     * Canal DEV : habille la ligne d'état comme une sortie de terminal, et donne aux <b>erreurs</b>
     * une signature qui ne repose pas sur la couleur.
     *
     * <p>Le thème DEV n'a qu'une seule teinte (vert) : passer une erreur en rouge est exclu. Elle
     * se distingue donc par <b>trois</b> signaux qu'aucune ligne ordinaire ne porte :
     * <ul>
     *   <li>un préfixe {@code !!} au lieu du chevron {@code >} habituel ;</li>
     *   <li>l'<b>intensité maximale</b> du tube (classe {@code status-error}) alors que l'état
     *       courant est en vert éteint ;</li>
     *   <li>un <b>clignotement lent</b> (1,4 s de période), le seul mouvement de tout l'écran au
     *       repos — l'œil y va tout seul.</li>
     * </ul>
     * Le clignotement ne tourne QUE pendant une erreur : au repos, rien n'est animé.
     */
    private void installStatusTerminal() {
        javafx.animation.Timeline clignote = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(Duration.ZERO,
                        e -> status.setOpacity(1.0)),
                new javafx.animation.KeyFrame(Duration.millis(700),
                        e -> status.setOpacity(0.45)),
                new javafx.animation.KeyFrame(Duration.millis(1400),
                        e -> status.setOpacity(1.0)));
        clignote.setCycleCount(javafx.animation.Animation.INDEFINITE);

        // Le texte brut vient d'une demi-douzaine d'appelants partagés avec la production : on ne
        // les modifie pas, on décore ici, au dernier moment, ce qu'ils ont écrit.
        final String[] dernier = { null };
        status.textProperty().addListener((o, ancien, nouveau) -> {
            if (nouveau == null || nouveau.equals(dernier[0])) return;
            boolean erreur = nouveau.toLowerCase(java.util.Locale.ROOT).startsWith("erreur");
            String decore = (erreur ? "!! " : "> ") + nouveau;
            status.getStyleClass().remove("status-error");
            if (erreur) status.getStyleClass().add("status-error");
            if (erreur) { clignote.playFromStart(); } else { clignote.stop(); status.setOpacity(1.0); }
            dernier[0] = decore;
            status.setText(decore);
        });
        dernier[0] = "> " + status.getText();      // amorce : l'état initial porte déjà l'invite
        status.setText(dernier[0]);
    }

    private static Region spacer(double h) {
        Region r = new Region();
        r.setMinHeight(h); r.setPrefHeight(h); r.setMaxHeight(h);
        return r;
    }

    /**
     * LA SECTION DE NOTRE HÉBERGEUR, sous l'actualité.
     *
     * <p>Owner 2026-08-10 : « met un petit logo hosterfy.com, c'est notre hébergeur partenaire, et
     * on doit leur faire une petite section dédiée sur le launcher ». Une carte, pas une bannière :
     * elle vit dans le flux de l'actualité, se fait discrète, et n'entre jamais en concurrence avec
     * le bouton JOUER.
     *
     * <h2>Pourquoi le logo n'est pas agrandi</h2>
     * La seule image de marque récupérable est leur favicon, en 32×32 — leur site refuse les
     * requêtes automatisées. L'étirer produirait exactement le flou qu'on vient de corriger sur les
     * boutons du bandeau de téléportation. Il est donc affiché en 32 px, sa taille NATIVE, et c'est
     * la typographie qui porte la section. Le jour où un logo vectoriel est fourni, seule la ligne
     * de chargement change.
     */
    private Region buildPartenaireHosterfy() {
        final double CARD_W = 280;
        final String SITE = "https://hosterfy.com";

        ImageView logo = new ImageView(new Image(
                getClass().getResourceAsStream("/assets/partenaires/hosterfy.png")));
        // Taille NATIVE du favicon : ni agrandissement, ni lissage.
        logo.setFitWidth(32);
        logo.setFitHeight(32);
        logo.setPreserveRatio(true);
        logo.setSmooth(false);

        Label tag = new Label("PARTENAIRE");
        tag.setFont(Fonts.black(9));
        tag.getStyleClass().add("news-tag");
        HBox tagRow = new HBox(tag);
        tagRow.setAlignment(Pos.CENTER_LEFT);

        Label nom = new Label("Hosterfy");
        nom.setFont(Fonts.bold(15));
        nom.setTextFill(Color.web("#F4F4F7"));

        Label quoi = new Label("L'hébergeur qui fait tourner nos serveurs.");
        quoi.setFont(Fonts.medium(11));
        quoi.setTextFill(Color.web("#A2A2AC"));
        quoi.setWrapText(true);

        Label lien = new Label("hosterfy.com");
        lien.setFont(Fonts.bold(11));
        // La couleur de leur marque, relevée sur le logo (#0070C0) et éclaircie pour rester
        // lisible sur le fond sombre du panneau.
        lien.setTextFill(Color.web("#4EA8E8"));

        VBox texte = new VBox(4, nom, quoi, lien);
        HBox ligne = new HBox(12, logo, texte);
        ligne.setAlignment(Pos.CENTER_LEFT);

        VBox bloc = new VBox(8, tagRow, ligne);
        bloc.setPadding(new Insets(13, 14, 14, 14));

        VBox card = new VBox(bloc);
        card.getStyleClass().add("news-item-glass");
        card.setPrefWidth(CARD_W);
        card.setMaxWidth(CARD_W);
        card.setStyle("-fx-cursor: hand;");
        card.setOnMouseClicked(e -> openBrowser(SITE));

        Rectangle clip = new Rectangle();
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        clip.widthProperty().bind(card.widthProperty());
        clip.heightProperty().bind(card.heightProperty());
        card.setClip(clip);

        Animations.hoverLift(card, 2);
        return card;
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
        setPlayBusy(true);          // 8-cube ring while the modpack downloads

        new Thread(() -> {
            try {
                // Self-heal stale servers.dat before the sync so the absent file
                // gets re-pulled clean (firstInstallOnly only re-pulls if absent).
                ServerListSanitizer.sweep();
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
                    setPlayBusy(false);
                    refreshPlayButton();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    status.setText(messageLisible(ex));
                    play.setDisable(false);
                    setPlayBusy(false);
                    refreshPlayButton();
                });
            }
        }, "Modpack-Update").start();
    }

    private void startPlay(Button play) {
        play.setDisable(true);
        progress.setProgress(0);
        status.setText("Synchronisation du modpack…");
        // Swap the static play arrow for the animated 8-cube ring so the
        // user has a continuous "something is happening" signal — covers
        // every phase from modpack sync through Fabric install to the MC
        // process being alive. Reset on MC exit + on error below.
        setPlayBusy(true);

        new Thread(() -> {
            try {
                // Self-heal stale servers.dat BEFORE the modpack sync. The clean
                // server list is shipped firstInstallOnly, so it's only re-pulled
                // if the local copy is ABSENT — the sanitizer deletes it when it
                // still carries a deprecated direct-backend entry, letting the
                // sync below restore the proxy-only list.
                ServerListSanitizer.sweep();
                // Migrations one-shot de keybinds (ex: zoom monocle C&B → non assignée).
                fr.nylerp.launcher.update.KeybindDefaults.applyOnce();
                // Migration one-shot : retire l'ancien NYLERP-PACK.zip (désormais embarqué en mod).
                fr.nylerp.launcher.util.LightTextures.cleanupLegacyPack();

                // Sync du modpack. En cas d'échec on ne lance PLUS silencieusement les
                // vieux mods (ce qui laissait le joueur bloqué sur une version périmée sans
                // le savoir) : on affiche un dialog clair et on laisse le joueur choisir —
                // Réessayer / Lancer quand même (cas hors-ligne assumé) / Annuler.
                ModpackUpdater updater = new ModpackUpdater(new ModpackUpdater.Listener() {
                    @Override public void onStatus(String line) {
                        Platform.runLater(() -> status.setText(line));
                    }
                    @Override public void onProgress(int d, int t, long bd, long bt) {
                        Platform.runLater(() -> progress.setProgress(t == 0 ? 0 : (double) d / t));
                    }
                });
                boolean syncCancelled = false;
                while (true) {
                    try {
                        updater.sync();
                        break;                       // sync réussi → on continue le lancement
                    } catch (Exception syncErr) {
                        org.slf4j.LoggerFactory.getLogger("ModpackSync")
                                .warn("Modpack sync failed: {}", syncErr.toString(), syncErr);
                        SyncFailedDialog.Choice choice = SyncFailedDialog.ask(syncErr.getMessage());
                        if (choice == SyncFailedDialog.Choice.RETRY) {
                            Platform.runLater(() -> status.setText("Nouvelle tentative de mise à jour…"));
                            continue;                // reboucle → relance sync()
                        }
                        if (choice == SyncFailedDialog.Choice.LAUNCH_ANYWAY) {
                            Platform.runLater(() ->
                                    status.setText("Lancement local (version possiblement périmée)…"));
                            break;                   // le joueur assume la version locale (hors-ligne/GitHub down)
                        }
                        syncCancelled = true;        // CANCEL → abandon propre du lancement
                        break;
                    }
                }
                if (syncCancelled) {
                    Platform.runLater(() -> {
                        status.setText("Lancement annulé");
                        progress.setProgress(0);
                        play.setDisable(false);
                        setPlayBusy(false);
                        refreshPlayButton();
                    });
                    return;
                }

                // Reconcile optional mods (Bobby, Litematica) AFTER the
                // manifest sync but BEFORE launching MC, so the mods/ folder
                // matches the user's settings before Fabric scans it.
                Platform.runLater(() -> status.setText("Mods optionnels…"));
                OptionalMods.applyAll();

                Account account = fr.nylerp.launcher.auth.AccountStore.active();
                // ── Session TOUJOURS fraîche au lancement (fix « session invalide ») ─────────────
                // Le refresh silencieux du démarrage ne suffit pas : un launcher laissé ouvert (ou un
                // PC sorti de veille) lance le jeu avec un token Minecraft périmé (validité ~24 h) →
                // « session invalide » à l'entrée du serveur. Ici on régénère la chaîne complète
                // (refresh token MS → XBL → XSTS → token MC neuf) juste avant CHAQUE lancement :
                // le jeu part systématiquement avec un token vieux de quelques secondes.
                //  • échec réseau transitoire → on lance avec le token en cache (aucune régression) ;
                //  • refresh token révoqué/expiré (~90 j) → on N'ENVOIE PAS le joueur dans un échec
                //    de session garanti : message clair + retour à l'écran de connexion.
                if (account != null && account.type() == Account.Type.MICROSOFT
                        && account.refreshToken() != null) {
                    Platform.runLater(() -> status.setText("Vérification de ta session Microsoft…"));
                    try {
                        Account fresh = fr.nylerp.launcher.auth.MicrosoftAuth
                                .refresh(account.refreshToken())
                                .get(20, java.util.concurrent.TimeUnit.SECONDS);
                        fr.nylerp.launcher.auth.AccountStore.updateActive(fresh);
                        account = fresh;
                        org.slf4j.LoggerFactory.getLogger("Auth")
                                .info("Session MC rafraîchie avant lancement pour {}", fresh.username());
                    } catch (Exception refreshErr) {
                        String m = String.valueOf(refreshErr);
                        boolean hardReject = m.contains("invalid_grant") || m.contains("revoked")
                                || m.contains("expired_token");
                        if (hardReject) {
                            org.slf4j.LoggerFactory.getLogger("Auth")
                                    .warn("Refresh token rejeté au lancement: {}", m);
                            Platform.runLater(() -> {
                                status.setText("Ta session Microsoft a expiré — reconnecte ton compte.");
                                progress.setProgress(0);
                                play.setDisable(false);
                                setPlayBusy(false);
                                refreshPlayButton();
                            });
                            return;   // pas de lancement voué à la session invalide
                        }
                        org.slf4j.LoggerFactory.getLogger("Auth")
                                .warn("Refresh avant lancement échoué (token en cache conservé): {}", m);
                    }
                }
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
                            setPlayBusy(false);
                            refreshPlayButton();
                        });
                    }, "MC-Watch").start();
                }

                // Fermer le launcher au lancement (réglage « closeOnLaunch », activé par défaut).
                // ~4 s de délai pour laisser la fenêtre Minecraft apparaître avant de quitter,
                // sinon l'utilisateur voit un court instant sans aucune fenêtre.
                if (proc != null && Settings.get().closeOnLaunch) {
                    new Thread(() -> {
                        try { Thread.sleep(4000); } catch (InterruptedException ignored) {}
                        Platform.runLater(() -> { Platform.exit(); System.exit(0); });
                    }, "CloseOnLaunch").start();
                }
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    status.setText(messageLisible(ex));
                    play.setDisable(false);
                    setPlayBusy(false);
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
            String json = fr.nylerp.launcher.util.LectureTexte.lire(f);
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
