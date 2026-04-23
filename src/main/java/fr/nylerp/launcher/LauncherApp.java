package fr.nylerp.launcher;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.auth.AuthManager;
import fr.nylerp.launcher.config.Constants;
import fr.nylerp.launcher.ui.LoginView;
import fr.nylerp.launcher.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LauncherApp extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(LauncherApp.class);

    private Stage stage;
    private Account account;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle(Constants.APP_NAME);
        stage.setResizable(false);
        stage.setWidth(1000);
        stage.setHeight(620);

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
        LoginView view = new LoginView(this::onAuthenticated);
        Scene scene = new Scene(view);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        stage.setScene(scene);
    }

    public void onAuthenticated(Account account) {
        this.account = account;
        AuthManager.save(account);
        MainView view = new MainView(account, this::onLogout);
        Scene scene = new Scene(view);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        stage.setScene(scene);
    }

    public void onLogout() {
        AuthManager.clear();
        this.account = null;
        showLogin();
    }
}
