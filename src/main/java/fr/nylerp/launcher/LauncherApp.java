package fr.nylerp.launcher;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.auth.AuthManager;
import fr.nylerp.launcher.config.Constants;
import fr.nylerp.launcher.ui.LoginView;
import fr.nylerp.launcher.ui.MainView;
import fr.nylerp.launcher.ui.SettingsView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LauncherApp extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(LauncherApp.class);

    private Stage stage;
    private Account account;

    @Override
    public void start(Stage stage) {
        loadFonts();
        this.stage = stage;
        stage.setTitle(Constants.APP_NAME);
        stage.setResizable(false);
        stage.setWidth(1000);
        stage.setHeight(720);

        Account saved = AuthManager.loadSaved();
        if (saved != null) {
            LOG.info("Resuming saved session for {} ({})", saved.username(), saved.type());
            onAuthenticated(saved);
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
        show(new MainView(account, this::onLogout, this::showSettings));
    }

    public void showSettings() {
        show(new SettingsView(() -> {
            if (account != null) {
                show(new MainView(account, this::onLogout, this::showSettings));
            } else {
                showLogin();
            }
        }));
    }

    public void onLogout() {
        AuthManager.clear();
        this.account = null;
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
                javafx.scene.text.Font.loadFont(
                        LauncherApp.class.getResourceAsStream("/fonts/" + f), 14);
                LOG.debug("Loaded font: {}", f);
            } catch (Exception e) {
                LOG.warn("Could not load font {}: {}", f, e.toString());
            }
        }
    }
}
