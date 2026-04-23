package fr.nylerp.launcher.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppPaths {

    /** Root folder for all launcher data on the current OS. */
    public static Path rootDir() {
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");
        Path p;
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            p = (appData != null ? Paths.get(appData) : Paths.get(home, "AppData", "Roaming"))
                    .resolve(Constants.APP_NAME);
        } else if (os.contains("mac")) {
            p = Paths.get(home, "Library", "Application Support", Constants.APP_NAME);
        } else {
            String xdg = System.getenv("XDG_DATA_HOME");
            p = (xdg != null ? Paths.get(xdg) : Paths.get(home, ".local", "share"))
                    .resolve(Constants.APP_NAME.toLowerCase());
        }
        ensure(p);
        return p;
    }

    public static Path gameDir()      { return ensure(rootDir().resolve("game")); }
    public static Path modsDir()      { return ensure(gameDir().resolve("mods")); }
    public static Path configDir()    { return ensure(gameDir().resolve("config")); }
    public static Path resourcePacks(){ return ensure(gameDir().resolve("resourcepacks")); }
    public static Path shaderPacks()  { return ensure(gameDir().resolve("shaderpacks")); }
    public static Path launcherState(){ return ensure(rootDir().resolve("state")); }
    public static Path sessionFile()  { return launcherState().resolve("session.json"); }
    public static Path settingsFile() { return launcherState().resolve("settings.json"); }
    public static Path manifestCache(){ return launcherState().resolve("manifest.json"); }

    private static Path ensure(Path p) {
        try { Files.createDirectories(p); } catch (Exception ignored) {}
        return p;
    }

    private AppPaths() {}
}
