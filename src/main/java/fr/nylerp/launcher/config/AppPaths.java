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

    /**
     * Le dossier de jeu du MODE COURANT.
     *
     * <p>Chaque mode a son instance complète et séparée : deux univers qui partageraient un même
     * dossier {@code mods} s'écraseraient l'un l'autre à chaque bascule, et le joueur
     * retéléchargerait tout son pack à chaque aller-retour.
     *
     * <p><b>NyleRP garde {@code game/}, et ce n'est pas négociable</b> : c'est là que se trouve
     * l'instance de tous les launchers déjà installés. Le jour où ils reçoivent le sélecteur,
     * ils doivent démarrer comme la veille, sans un octet à retélécharger. Ce qui est nouveau
     * s'installe à côté ({@code game-pokenyle/}), jamais à la place.
     */
    public static Path gameDir()      { return ensure(rootDir().resolve(ModeDeJeu.courant().dossier)); }
    public static Path modsDir()      { return ensure(gameDir().resolve("mods")); }
    public static Path configDir()    { return ensure(gameDir().resolve("config")); }
    public static Path resourcePacks(){ return ensure(gameDir().resolve("resourcepacks")); }
    public static Path shaderPacks()  { return ensure(gameDir().resolve("shaderpacks")); }
    public static Path launcherState(){ return ensure(rootDir().resolve("state")); }
    public static Path sessionFile()  { return launcherState().resolve("session.json"); }
    public static Path settingsFile() { return launcherState().resolve("settings.json"); }
    /**
     * Le manifeste du pack, tel qu'on l'a appliqué la dernière fois — <b>par mode</b>.
     *
     * <p>Un seul fichier pour deux univers serait un piège silencieux : après une bascule, le
     * launcher croirait son instance à jour parce que le manifeste de L'AUTRE mode s'y trouve,
     * et lancerait le jeu avec des mods incomplets. NyleRP conserve le nom historique pour que
     * rien ne bouge chez les joueurs déjà installés.
     */
    public static Path manifestCache(){
        ModeDeJeu m = ModeDeJeu.courant();
        return launcherState().resolve(m == ModeDeJeu.NYLERP ? "manifest.json"
                                                             : "manifest-" + m.id + ".json");
    }

    private static Path ensure(Path p) {
        try { Files.createDirectories(p); } catch (Exception ignored) {}
        return p;
    }

    private AppPaths() {}
}
