package fr.nylerp.launcher.util;

import fr.nylerp.launcher.config.Constants;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Payload de REPLI de la refonte 2026-10 : ce payload (1.0.101) ne connaît pas le mode fond. Si la
 * refonte (1.1.x) a posé une entrée de démarrage automatique, on la retire au premier lancement,
 * sinon la fenêtre s'ouvrirait à chaque ouverture de session. Silencieux, sans effet si rien n'existe.
 */
public final class RetraitDemarrageAuto {

    private RetraitDemarrageAuto() { }

    public static void executer() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String home = System.getProperty("user.home");
            if (os.contains("win")) {
                new ProcessBuilder("reg", "delete", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                        "/v", Constants.APP_NAME, "/f").redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            } else if (os.contains("mac")) {
                Files.deleteIfExists(Paths.get(home, "Library", "LaunchAgents",
                        (Constants.DEV ? "fr.nylerp.launcher.dev" : "fr.nylerp.launcher") + ".plist"));
            } else {
                String xdg = System.getenv("XDG_CONFIG_HOME");
                Path config = (xdg != null && !xdg.isBlank()) ? Paths.get(xdg) : Paths.get(home, ".config");
                Files.deleteIfExists(config.resolve("autostart")
                        .resolve((Constants.DEV ? "nylelauncher-dev" : "nylelauncher") + ".desktop"));
            }
        } catch (Throwable ignore) {
            // jamais bloquant
        }
    }
}
