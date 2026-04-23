package fr.nylerp.launcher.update;

/** One file described by the manifest. Path is relative to the game directory. */
public final class ManifestEntry {
    /** Relative to game dir, e.g. "mods/mymod.jar" or "config/mymod.toml". */
    public String path;
    /** SHA-256 hex, lowercase. */
    public String sha256;
    /** Size in bytes. Optional (informational). */
    public long size;
    /** Absolute URL where to fetch the file from. */
    public String url;
}
