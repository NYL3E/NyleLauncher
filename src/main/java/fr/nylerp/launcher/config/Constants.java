package fr.nylerp.launcher.config;

public final class Constants {
    public static final String APP_NAME     = "NyleLauncher";
    public static final String MC_VERSION   = "1.21.1";
    public static final String LOADER       = "fabric";
    public static final String SERVER_HOST  = "play.nylerp.fr";
    public static final int    SERVER_PORT  = 25565;
    public static final String DISCORD_URL  = "https://discord.gg/nylerp"; // TODO: real invite
    public static final String WEBSITE      = "https://nylerp.fr";

    /**
     * URL of the remote manifest describing the current modpack state.
     * Points to the GitHub Release asset that holds manifest.json.
     */
    public static final String MANIFEST_URL =
            "https://github.com/NYL3E/NyleLauncher/releases/download/pack-latest/manifest.json";

    /**
     * Base URL from which individual mod files listed in the manifest will be downloaded.
     * Same release as manifest, or another host (R2 later).
     */
    public static final String PACK_BASE_URL =
            "https://github.com/NYL3E/NyleLauncher/releases/download/pack-latest/";

    /** Microsoft Minecraft public client id — used by MultiMC, Prism, etc. */
    public static final String MS_CLIENT_ID = "00000000402b5328";

    private Constants() {}
}
