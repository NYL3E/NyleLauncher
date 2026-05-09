package fr.nylerp.launcher.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * User preferences persisted on disk across runs.
 * One instance — always read/saved through the static helpers.
 */
public final class Settings {

    private static final Logger LOG = LoggerFactory.getLogger(Settings.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public int ramMb = 4096;
    public boolean optionalLitematica       = false;
    public boolean optionalDistantHorizons  = false;
    /** Persists the launcher's mute toggle (ambient + music) across sessions
     *  so players who silenced the launcher once don't have to do it on
     *  every start. */
    public boolean launcherAudioMuted       = false;
    // optionalBobby was retired in 1.0.28 (replaced by Distant Horizons). The
    // field is left as a no-op deserialisation target so existing saved
    // settings.json files don't fail to parse for users who had it on; Gson
    // tolerates unknown fields, but keeping the slot avoids a future Gson
    // strict-mode bump biting us. Remove after a few versions of grace.
    @SuppressWarnings("unused") public boolean optionalBobby = false;

    private static Settings current;

    public static Settings get() {
        if (current != null) return current;
        Path file = AppPaths.settingsFile();
        if (Files.exists(file)) {
            try {
                current = GSON.fromJson(Files.readString(file), Settings.class);
                if (current == null) current = new Settings();
            } catch (Exception e) {
                LOG.warn("Settings unreadable, resetting: {}", e.toString());
                current = new Settings();
            }
        } else {
            current = new Settings();
        }
        return current;
    }

    public void save() {
        try {
            Files.writeString(AppPaths.settingsFile(), GSON.toJson(this));
        } catch (Exception e) {
            LOG.warn("Could not save settings: {}", e.toString());
        }
    }
}
