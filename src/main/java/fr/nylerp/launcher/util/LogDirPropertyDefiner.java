package fr.nylerp.launcher.util;

import ch.qos.logback.core.PropertyDefinerBase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LogDirPropertyDefiner extends PropertyDefinerBase {

    @Override
    public String getPropertyValue() {
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");
        Path p;
        if (os.contains("mac")) {
            p = Paths.get(home, "Library", "Logs", "NyleLauncher");
        } else if (os.contains("win")) {
            String appData = System.getenv("LOCALAPPDATA");
            p = (appData != null ? Paths.get(appData) : Paths.get(home, "AppData", "Local"))
                    .resolve("NyleLauncher").resolve("Logs");
        } else {
            String xdg = System.getenv("XDG_STATE_HOME");
            p = (xdg != null ? Paths.get(xdg) : Paths.get(home, ".local", "state"))
                    .resolve("nylelauncher");
        }
        try { Files.createDirectories(p); } catch (Exception ignored) {}
        return p.toAbsolutePath().toString();
    }
}
