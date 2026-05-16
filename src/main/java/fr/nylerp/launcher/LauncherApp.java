package fr.nylerp.launcher;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.auth.AuthManager;
import fr.nylerp.launcher.config.Constants;
import fr.nylerp.launcher.ui.LoginView;
import fr.nylerp.launcher.ui.MainView;
import fr.nylerp.launcher.ui.SettingsView;
import fr.nylerp.launcher.util.CrashReporter;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LauncherApp extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(LauncherApp.class);

    private Stage stage;
    private Account account;
    /** Cached MainView instance — reused across {@code Home → Settings → Home}
     *  navigation so the background MediaPlayers stay alive (and frame 0 of
     *  videolauncher1 is already decoded + rendering by the time the scene
     *  reattaches). Without this, every {@code show(new MainView(...))} call
     *  built fresh players whose first decoded frame arrived ~50–200 ms
     *  AFTER the scene was visible — that's the gap where the legacy
     *  fond-launcher.png used to flash through (now removed; the gap itself
     *  is closed by this cache). Rebuilt only on a fresh login. */
    private MainView mainView;

    @Override
    public void start(Stage stage) {
        loadFonts();
        // Hard-stop screen for users running a bootstrap that's too old. Returns true
        // when the dialog was shown, in which case we skip the rest of the start-up so
        // the user can't bypass the redownload by interacting with a half-initialised UI.
        String installedBs = fr.nylerp.launcher.update.SelfUpdater.installedVersion();
        LOG.info("Bootstrap version detected: {} (min required: {})",
                installedBs, fr.nylerp.launcher.update.BrokenBootstrapDialog.MIN_REQUIRED_BOOTSTRAP);
        if (fr.nylerp.launcher.update.BrokenBootstrapDialog.showIfBootstrapTooOld(installedBs)) {
            LOG.warn("Bootstrap {} is below the required minimum {} — showing redownload dialog",
                    installedBs, fr.nylerp.launcher.update.BrokenBootstrapDialog.MIN_REQUIRED_BOOTSTRAP);
            return;
        }
        // After JavaFX is up, surface any pending crash reports from previous runs.
        CrashReporter.promptIfPending();
        this.stage = stage;
        stage.setTitle(Constants.APP_NAME);
        stage.setResizable(false);
        stage.setWidth(1000);
        // Stage outer height = body (matches video display rect at width
        // 1000) + bottom bar 64 px + macOS chrome 28 px ≈ 648 px.
        // {@code MainView.BODY_HEIGHT} (= 1000 × 1080/1942 = 556.13) is
        // the source of truth — see comment there. Updating the videos to
        // a different aspect requires bumping both constants together.
        //
        // 2026-05-16 — was 680 px (assumed up to 50-px chrome) but that
        // left ~30 px of slack above the video, exposing the fallback
        // background image. 648 fits the inner content edge-to-edge with
        // no slack at the top.
        stage.setHeight(
                fr.nylerp.launcher.ui.MainView.BODY_HEIGHT
                + 64.0    // bottom bar
                + 28.0    // macOS standard non-resizable titlebar
        );
        try {
            stage.getIcons().add(new javafx.scene.image.Image(
                    getClass().getResourceAsStream("/images/app_icon.png")));
        } catch (Exception e) {
            LOG.warn("Could not load app icon: {}", e.toString());
        }

        Account saved = AuthManager.loadSaved();
        if (saved != null) {
            LOG.info("Resuming saved session for {} ({})", saved.username(), saved.type());
            // Show the main view immediately with the saved Account, then
            // silently refresh the MS access token in the background so the
            // user never has to re-login unless the refresh token is revoked.
            onAuthenticated(saved);
            if (saved.type() == Account.Type.MICROSOFT && saved.refreshToken() != null) {
                fr.nylerp.launcher.auth.MicrosoftSystemAuth.refresh(saved.refreshToken())
                        .whenComplete((refreshed, err) -> javafx.application.Platform.runLater(() -> {
                            if (err != null) {
                                String msg = err.toString();
                                // Token revoked / expired — kick the user back to the login
                                // screen so they reconnect once. Everything else (network
                                // hiccups, MS being down) keeps the cached token.
                                if (msg.contains("invalid_grant") || msg.contains("expired")
                                        || msg.contains("revoked")) {
                                    LOG.warn("MS refresh token rejected — logging out: {}", msg);
                                    onLogout();
                                } else {
                                    LOG.warn("Silent MS refresh failed (keeping cached token): {}", msg);
                                }
                            } else {
                                LOG.info("MS token refreshed silently for {}", refreshed.username());
                                AuthManager.save(refreshed);
                                this.account = refreshed;
                            }
                        }));
            }
        } else {
            showLogin();
        }
        stage.show();
    }

    public void showLogin() {
        show(new LoginView(this::onAuthenticated));
    }

    public void onAuthenticated(Account account) {
        this.account = account;
        AuthManager.save(account);
        // Fresh login → fresh MainView (drops any previously cached instance
        // so a different account doesn't see the previous user's media
        // players or auth-derived state).
        mainView = new MainView(account, this::onLogout, this::showSettings);
        show(mainView);
    }

    public void showSettings() {
        show(new SettingsView(() -> {
            if (account != null) {
                // Re-use the cached MainView so its background MediaPlayers
                // keep playing through the navigation — frame 0 of video1 is
                // already on the MediaView when the scene attaches, so the
                // user sees video1 immediately with no decode flash.
                if (mainView == null) {
                    mainView = new MainView(account, this::onLogout, this::showSettings);
                }
                show(mainView);
            } else {
                showLogin();
            }
        }));
    }

    public void onLogout() {
        AuthManager.clear();
        this.account = null;
        mainView = null;       // drop the cached players too
        showLogin();
    }

    private void show(javafx.scene.Parent root) {
        Scene s = new Scene(root);
        s.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        stage.setScene(s);
    }

    private static void loadFonts() {
        String[] files = {
                "Montserrat-Regular.ttf",
                "Montserrat-Medium.ttf",
                "Montserrat-SemiBold.ttf",
                "Montserrat-Bold.ttf",
                "Montserrat-Black.ttf"
        };
        for (String f : files) {
            try {
                javafx.scene.text.Font font = javafx.scene.text.Font.loadFont(
                        LauncherApp.class.getResourceAsStream("/fonts/" + f), 14);
                if (font != null) {
                    LOG.info("Font loaded: file='{}' family='{}' name='{}' style='{}'",
                            f, font.getFamily(), font.getName(), font.getStyle());
                } else {
                    LOG.warn("Font.loadFont returned null for {}", f);
                }
            } catch (Exception e) {
                LOG.warn("Could not load font {}: {}", f, e.toString());
            }
        }
    }
}
