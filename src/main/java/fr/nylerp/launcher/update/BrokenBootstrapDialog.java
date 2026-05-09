package fr.nylerp.launcher.update;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.awt.Desktop;
import java.net.URI;

/**
 * Hard-stop screen shown when the bootstrap.exe / .app the user installed is too old to
 * keep working with the current payload. Displayed BEFORE the rest of the launcher UI so
 * the user can't proceed past it. Single button → opens nylerp.fr/telecharger and exits.
 *
 * <p>Why we check at all: the bootstrap auto-replaces the payload jar at every launch, but
 * the bootstrap itself only updates when the user re-runs the MSI/DMG/DEB. If a user is
 * stuck on bootstrap 0.3.5 (e.g. their MSI auto-update silently failed), they will keep
 * running an outdated install indefinitely. The payload — which we control via the
 * manifest — runs on every launch and is the ONE thing we know all users will execute, so
 * it's the only place we can centrally force a redownload.
 */
public final class BrokenBootstrapDialog {

    /** Bumped each time we make a backwards-incompatible bootstrap change that warrants
     *  a forced redownload (e.g. JavaFX bundling, install-script swap, …). Bootstraps
     *  older than this trigger the dialog. */
    public static final String MIN_REQUIRED_BOOTSTRAP = "0.3.10";

    private static final String DOWNLOAD_URL = "https://nyle-mc-server.pages.dev/telecharger/";

    /** Invoke at app start; returns true when the dialog was shown (caller should exit). */
    public static boolean showIfBootstrapTooOld(String installedBootstrapVersion) {
        if (installedBootstrapVersion == null || installedBootstrapVersion.isBlank()) {
            return false;  // bootstrap injection missing — likely running from gradle/IDE
        }
        if (compare(installedBootstrapVersion, MIN_REQUIRED_BOOTSTRAP) >= 0) {
            return false;
        }
        Platform.runLater(() -> show(installedBootstrapVersion));
        return true;
    }

    private static void show(String installed) {
        Stage stage = new Stage(StageStyle.UNDECORATED);
        stage.initModality(Modality.APPLICATION_MODAL);

        Label title = new Label("Launcher obsolète");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: 800;");

        Label body = new Label(
            "Ton launcher (v" + installed + ") est trop ancien pour continuer.\n" +
            "Une version corrigée et beaucoup plus stable est disponible.\n\n" +
            "Re-télécharge le launcher depuis le site officiel.");
        body.setStyle("-fx-text-fill: #b6a8c6; -fx-font-size: 13px;");
        body.setWrapText(true);

        Button download = new Button("Aller sur nylerp.fr");
        download.setStyle(
            "-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: 700; " +
            "-fx-padding: 12 28 12 28; -fx-background-radius: 6; -fx-font-size: 13px;");
        download.setOnAction(e -> {
            openInBrowser(DOWNLOAD_URL);
            Platform.exit();
            System.exit(0);
        });

        Button quit = new Button("Quitter");
        quit.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #7a6f88; " +
            "-fx-padding: 12 18 12 18; -fx-background-radius: 6;");
        quit.setOnAction(e -> { Platform.exit(); System.exit(0); });

        HBox actions = new HBox(8, download, quit);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(16, title, body, actions);
        root.setPadding(new Insets(28));
        root.setStyle("-fx-background-color: #1a0e20; -fx-border-color: #3f2a55; -fx-border-width: 1;");
        root.setPrefWidth(440);

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle("NyleLauncher");
        stage.setOnCloseRequest(e -> { Platform.exit(); System.exit(0); });
        stage.show();
    }

    private static void openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) {}
        // OS fallback
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception ignored) {}
    }

    /** Compare semver-ish version strings: 0.3.5 vs 0.3.10 → 0.3.10 wins. */
    private static int compare(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int ia = i < pa.length ? parse(pa[i]) : 0;
            int ib = i < pb.length ? parse(pb[i]) : 0;
            if (ia != ib) return Integer.compare(ia, ib);
        }
        return 0;
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); }
        catch (Exception e) { return 0; }
    }

    private BrokenBootstrapDialog() {}
}
