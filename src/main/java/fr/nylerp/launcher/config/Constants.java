package fr.nylerp.launcher.config;

public final class Constants {
    public static final String APP_NAME     = "NyleLauncher";
    public static final String APP_VERSION  = "0.3.4";
    /** Payload version baked at compile time. Bump together with the
     *  {@code payload-X.Y.Z} git tag. Displayed in the launcher footer so a
     *  player can visually confirm which payload they're running after a
     *  silent bootstrap refresh. */
    public static final String PAYLOAD_VERSION = "1.0.50";
    public static final String MC_VERSION   = "1.21.1";
    public static final String LOADER       = "fabric";
    public static final String SERVER_HOST  = "83.143.117.51";
    public static final int    SERVER_PORT  = 20652;
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
