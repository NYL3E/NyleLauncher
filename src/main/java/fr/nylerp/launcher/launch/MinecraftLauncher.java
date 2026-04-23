package fr.nylerp.launcher.launch;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.config.AppPaths;
import fr.nylerp.launcher.config.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Launches Minecraft through OpenLauncherLib + FlowUpdater.
 *
 * Deliberately thin for the MVP — we wire OpenLauncherLib in the next iteration
 * once the UI + update loop + auth are validated end-to-end. For now this class
 * just logs the intent so we can test the pipeline without actually spawning MC.
 */
public final class MinecraftLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(MinecraftLauncher.class);

    public static void launch(Account account, int ramMb, boolean joinServer) {
        LOG.info("Launching Minecraft {} ({}) as {} ({}) — {} MB RAM, joinServer={}",
                Constants.MC_VERSION, Constants.LOADER,
                account.username(), account.type(),
                ramMb, joinServer);
        LOG.info("Game dir: {}", AppPaths.gameDir());
        if (joinServer) {
            LOG.info("Auto-connect target: {}:{}", Constants.SERVER_HOST, Constants.SERVER_PORT);
        }
        // TODO (next iteration):
        //  - use fr.flowarg:flowupdater to install/verify Fabric loader + libraries + assets
        //  - use fr.flowarg:openlauncherlib to build GameInfos + AuthInfos + ExternalLaunchProfile
        //    and call new ExternalLauncher(profile).launch()
        //  - stream child process stdout into the in-launcher console view
        //  - pass --server play.nylerp.fr --port 25565 when joinServer=true
    }

    private MinecraftLauncher() {}
}
