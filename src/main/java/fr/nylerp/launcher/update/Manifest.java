package fr.nylerp.launcher.update;

import java.util.List;

/**
 * Remote manifest describing the current state of the modpack on disk
 * that the launcher should reproduce.
 *
 * Minimum shape:
 * <pre>
 * {
 *   "version":     "2026.04.23-1",
 *   "mcVersion":   "1.21.1",
 *   "loader":      { "type": "fabric", "version": "0.16.5" },
 *   "files": [
 *     {
 *       "path":   "mods/nylecontent-1.3.3.jar",
 *       "sha256": "abcd...",
 *       "size":   4006739,
 *       "url":    "https://github.com/.../nylecontent-1.3.3.jar"
 *     },
 *     ...
 *   ]
 * }
 * </pre>
 */
public final class Manifest {
    public String version;
    public String mcVersion;
    public Loader loader;
    public List<ManifestEntry> files;

    public static final class Loader {
        public String type;     // "fabric", "forge", "neoforge"
        public String version;
    }
}
