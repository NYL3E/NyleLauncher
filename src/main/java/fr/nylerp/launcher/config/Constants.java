package fr.nylerp.launcher.config;

public final class Constants {

    /** true sur un build DEV (voir {@link Channel}). Pilote nom/serveur/pack/thème. */
    public static final boolean DEV = Channel.isDev();

    public static final String APP_NAME     = DEV ? "NyleLauncher DEV" : "NyleLauncher";
    public static final String APP_VERSION  = "0.3.19";
    /** Payload version baked at compile time. Bump together with the
     *  {@code payload-X.Y.Z} git tag. Displayed in the launcher footer so a
     *  player can visually confirm which payload they're running after a
     *  silent bootstrap refresh. */
    public static final String PAYLOAD_VERSION = "1.0.84";
    public static final String MC_VERSION   = "1.21.1";
    public static final String LOADER       = "fabric";

    // ── Serveur de jeu ──────────────────────────────────────────────────────────────────────
    // PROD : domaine → proxy Velocity (robuste si l'IP change).
    // DEV  : SERVER DEV direct (2f350981 = game45-fr.hosterfy.com:20652) — isolé de la prod.
    public static final String SERVER_HOST  = DEV ? "game45-fr.hosterfy.com" : "play.nylerp.fr";
    public static final int    SERVER_PORT  = DEV ? 20652 : 20161;

    public static final String DISCORD_URL  = "https://discord.gg/nylerp"; // TODO: real invite
    public static final String WEBSITE      = "https://nylerp.fr";

    /** Tag GitHub de la release qui héberge le pack. MVP DEV : on tire {@code pack-latest} (mêmes mods
     *  client que la prod → connexion OK au serveur de dev) ; passer à {@code pack-dev} quand on testera
     *  des mods CLIENT en dev (release séparée). */
    private static final String PACK_TAG = "pack-latest";

    /**
     * URL of the remote manifest describing the current modpack state.
     * Points to the GitHub Release asset that holds manifest.json.
     */
    public static final String MANIFEST_URL =
            "https://github.com/NYL3E/NyleLauncher/releases/download/" + PACK_TAG + "/manifest.json";

    /**
     * Base URL from which individual mod files listed in the manifest will be downloaded.
     * Same release as manifest, or another host (R2 later).
     */
    public static final String PACK_BASE_URL =
            "https://github.com/NYL3E/NyleLauncher/releases/download/" + PACK_TAG + "/";

    /** Microsoft Minecraft public client id — used by MultiMC, Prism, etc. */
    public static final String MS_CLIENT_ID = "00000000402b5328";

    private Constants() {}
}
