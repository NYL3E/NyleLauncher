package fr.nylerp.launcher;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.auth.AccountStore;
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
        // The window CONTENT (scene) is pinned to exactly 1000 × (BODY_HEIGHT + 64-px bottom bar) in show().
        // We must NOT hardcode the titlebar height here: the old "+ 28" was the macOS chrome, but the Windows
        // chrome (titlebar + borders, DPI-scaled at 125 %/150 %) is taller, so the client area came out too
        // short and the 64-px bottom bar (the black rectangle behind the loading bar + Play button) overflowed
        // the bottom and wrapped back to the top. Instead, stage.sizeToScene() (just before show(), below) sizes
        // the window to the scene + the OS's OWN chrome → correct on Windows AND macOS, no crop, no top slack.
        // MainView.BODY_HEIGHT (= 1000 × 1080/1942 = 556.13 px) is the source of truth for the body height.
        try {
            stage.getIcons().add(new javafx.scene.image.Image(
                    getClass().getResourceAsStream("/images/app_icon.png")));
        } catch (Exception e) {
            LOG.warn("Could not load app icon: {}", e.toString());
        }

        Account saved = AccountStore.active();
        if (saved != null) {
            LOG.info("Resuming saved session for {} ({})", saved.username(), saved.type());
            // Show the main view immediately with the saved Account, then
            // silently refresh the MS access token in the background so the
            // user never has to re-login unless the refresh token is revoked.
            onAuthenticated(saved);
            silentRefresh(saved);
        } else {
            showLogin();
        }
        stage.sizeToScene();   // window = scene content (1000 × BODY_HEIGHT+64) + the platform's OWN chrome
        stage.show();
    }

    /** Background MS token refresh for the given account (no-op for offline).
     *  On hard rejection (revoked/expired) the account is dropped from the
     *  roster ; transient failures keep the cached token. */
    private void silentRefresh(Account acc) {
        if (acc.type() != Account.Type.MICROSOFT || acc.refreshToken() == null) return;
        fr.nylerp.launcher.auth.MicrosoftSystemAuth.refresh(acc.refreshToken())
                .whenComplete((refreshed, err) -> javafx.application.Platform.runLater(() -> {
                    if (err != null) {
                        String msg = err.toString();
                        if (msg.contains("invalid_grant") || msg.contains("expired")
                                || msg.contains("revoked")) {
                            LOG.warn("MS refresh token rejected — removing account: {}", msg);
                            // Only drop it if it's still the active account.
                            if (account != null && !account.isOffline()
                                    && acc.uuid() != null && acc.uuid().equals(account.uuid())) {
                                onLogout();
                            }
                        } else {
                            LOG.warn("Silent MS refresh failed (keeping cached token): {}", msg);
                        }
                    } else {
                        LOG.info("MS token refreshed silently for {}", refreshed.username());
                        AccountStore.updateActive(refreshed);
                        this.account = refreshed;
                    }
                }));
    }

    public void showLogin() {
        show(new LoginView(this::onAuthenticated));
    }

    /** "Ajouter un compte" — login screen with a back arrow to the main view. */
    public void showAddAccount() {
        show(new LoginView(this::onAuthenticated, () -> {
            if (account != null && mainView != null) show(mainView);
            else showLogin();
        }));
    }

    public void onAuthenticated(Account account) {
        if (!AccountStore.addAndActivate(account)) {
            // Roster full of OTHER accounts — the UI normally prevents this
            // (the "add" entry is disabled at 3/3), this is just a backstop.
            javafx.scene.control.Alert a = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.WARNING,
                    "Tu as déjà 3 comptes enregistrés. Retire un compte (bouton "
                    + "Déconnexion) avant d'en ajouter un nouveau.");
            a.setTitle("Comptes");
            a.setHeaderText(null);
            a.showAndWait();
            return;
        }
        this.account = account;
        // Fresh login → fresh MainView (drops any previously cached instance
        // so a different account doesn't see the previous user's media
        // players or auth-derived state).
        mainView = new MainView(account, this::onLogout, this::showSettings,
                                this::switchAccount, this::showAddAccount);
        show(mainView);
    }

    /** Switches to another roster account from the header capsule menu. */
    public void switchAccount(Account target) {
        AccountStore.setActive(target);
        this.account = AccountStore.active();
        mainView = new MainView(this.account, this::onLogout, this::showSettings,
                                this::switchAccount, this::showAddAccount);
        show(mainView);
        silentRefresh(this.account);
    }

    public void showSettings() {
        show(new SettingsView(() -> {
            if (account != null) {
                // Re-use the cached MainView so its background MediaPlayers
                // keep playing through the navigation — frame 0 of video1 is
                // already on the MediaView when the scene attaches, so the
                // user sees video1 immediately with no decode flash.
                if (mainView == null) {
                    mainView = new MainView(account, this::onLogout, this::showSettings,
                                            this::switchAccount, this::showAddAccount);
                }
                show(mainView);
            } else {
                showLogin();
            }
        }));
    }

    /** "Déconnexion" = remove the ACTIVE account from the roster. If other
     *  accounts remain, the first one takes over ; otherwise back to login. */
    public void onLogout() {
        AccountStore.removeActive();
        Account next = AccountStore.active();
        mainView = null;       // drop the cached players too
        if (next != null) {
            this.account = next;
            mainView = new MainView(next, this::onLogout, this::showSettings,
                                    this::switchAccount, this::showAddAccount);
            show(mainView);
        } else {
            this.account = null;
            showLogin();
        }
    }

    /** Single Scene reused for the whole app lifetime. Earlier versions
     *  built a fresh Scene on every navigation, which crashed with
     *  "MainView is already set as root of another scene" the moment we
     *  started caching MainView (1.0.54): JavaFX does NOT auto-detach a
     *  Parent from its previous Scene when you call {@code new Scene(parent)},
     *  it throws IllegalArgumentException. Reusing one Scene + swapping
     *  via {@link Scene#setRoot} is the canonical fix and also avoids
     *  re-loading stylesheets on every nav. */
    private Scene scene;

    private void show(javafx.scene.Parent root) {
        if (scene == null) {
            scene = new Scene(root, 1000, fr.nylerp.launcher.ui.MainView.BODY_HEIGHT + 64.0);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            stage.setScene(scene);
        } else {
            scene.setRoot(root);
        }
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
