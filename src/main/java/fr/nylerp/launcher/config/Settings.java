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

    // Default heap = 4 GB per user request. Refurbished Furniture (the chronic
    // tick allocator at 1.29M ms cumulative across MrCedriic's sessions) has
    // been removed from the modpack so allocation rate drops back to a level
    // 4 GB can sustain without pushing G1 into the Old Generation Compaction
    // pause zone. Players can manually bump in Settings if they want more.
    public int ramMb = 4096;
    /** Legacy field kept for backwards-compatible Gson deserialisation —
     *  earlier payload (1.0.46) used this to one-time bump 6 GB users to
     *  8 GB. We no longer enforce a minimum, so the field is read-only
     *  here. Don't delete: Gson would reject the unknown property on
     *  existing settings.json files. */
    @SuppressWarnings("unused") public boolean heapMigratedToV2 = false;
    public boolean optionalLitematica       = false;
    public boolean optionalDistantHorizons  = false;
    /** Iris shaders toggle. Independent of DH so a player can run shader packs
     *  without DH's far-LOD load, but DH unconditionally force-installs Iris
     *  too — see {@code OptionalMods.ENTRIES} — because DH's render pipeline
     *  produces 1+ second hitches on the no-Iris fallback path. */
    public boolean optionalIris             = false;
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
        // No auto-bump — the user is the source of truth on their RAM
        // setting. Fresh installs get the default (4 GB) and players who
        // already chose a value above or below keep it.
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
