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

    // Default heap bumped 6 → 8 GB. MrCedriic 12:32 report showed a G1 Old
    // Generation Compaction Pause of 926 ms triggered at 4 GB used on a 6 GB
    // heap (66% occupancy). With 8 GB, the same working set sits at 50% so
    // G1 stays in young-only collection mode and the mixed/compaction
    // pauses don't fire. 193-mod modpack with Iris+Sodium+PointBlank needs
    // the extra headroom.
    public int ramMb = 8192;
    /** Sentinel that we've already one-time-bumped this user's heap to the
     *  current minimum recommended value. Without it, existing players with
     *  saved values below the new floor (6 GB Cedric / 4 GB Nyle) keep
     *  running with too little RAM after a default bump — the user's
     *  saved settings.json wins over our updated default. */
    public boolean heapMigratedToV2 = false;
    /** Minimum heap we want every player to run with on this modpack. The
     *  one-time migration above bumps any saved {@code ramMb} below this
     *  value up to it. Players can still manually go BELOW this in the
     *  Settings UI after the migration — we only enforce once. */
    public static final int MIN_RECOMMENDED_RAM_MB = 8192;
    public boolean optionalLitematica       = false;
    public boolean optionalDistantHorizons  = false;
    public boolean optionalSkinLayer3D      = false;
    /** Persists the launcher's mute toggle (ambient + music) across sessions
     *  so players who silenced the launcher once don't have to do it on
     *  every start. */
    public boolean launcherAudioMuted       = false;
    /** Volume of the ambient campfire loop. Range 0.0–1.0. Default 0.125
     *  matches the design brief — a soft sub-music layer that adds texture
     *  without dominating the foreground music. */
    public double ambientVolume             = 0.125;
    /** Volume of the foreground music loop. Range 0.0–1.0. Default 0.30
     *  matches the design brief — present and listenable without burying
     *  speech or notifications. */
    public double musicVolume               = 0.30;
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
        // One-shot migration: bump any saved ramMb below the new floor up to
        // it. Sentinel flag prevents re-bumping if the player later chooses
        // to go below the floor manually.
        if (!current.heapMigratedToV2 && current.ramMb < MIN_RECOMMENDED_RAM_MB) {
            LOG.info("Heap migration v2: ramMb {} → {} MB (one-time bump)",
                    current.ramMb, MIN_RECOMMENDED_RAM_MB);
            current.ramMb = MIN_RECOMMENDED_RAM_MB;
            current.heapMigratedToV2 = true;
            current.save();
        } else if (!current.heapMigratedToV2) {
            // Already above the floor — just mark the migration done so we
            // don't re-evaluate every launch.
            current.heapMigratedToV2 = true;
            current.save();
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
