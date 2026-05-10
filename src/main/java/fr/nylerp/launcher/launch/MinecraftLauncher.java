package fr.nylerp.launcher.launch;

import com.google.gson.JsonObject;
import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.config.AppPaths;
import fr.nylerp.launcher.config.Constants;
import fr.nylerp.launcher.util.Downloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * End-to-end Minecraft launcher:
 *  1) ensures a Temurin 21 JRE is available
 *  2) ensures vanilla Minecraft 1.21.1 (version json, client jar, libraries, assets) is installed
 *  3) ensures Fabric loader is installed and produces the merged classpath
 *  4) spawns the game process with the right args (auth, --server, --gameDir, etc.)
 */
public final class MinecraftLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(MinecraftLauncher.class);

    public interface Listener {
        void onStatus(String s);
        void onProgress(long done, long total);
    }

    public static Process launch(Account account, int ramMb, boolean joinServer, Listener listener)
            throws IOException {
        return launch(account, ramMb, joinServer, null, listener);
    }

    public static Process launch(Account account, int ramMb, boolean joinServer,
                                 String fabricVersion, Listener listener)
            throws IOException {
        Listener l = listener != null ? listener : new Listener() {
            public void onStatus(String s) {}
            public void onProgress(long d, long t) {}
        };

        // Fall back to a reasonable default if the manifest didn't specify one
        String loaderVer = (fabricVersion == null || fabricVersion.isBlank())
                ? "0.18.4" : fabricVersion;

        l.onStatus("Préparation du runtime Java…");
        Path java = JavaRuntime.ensure((d, t) -> l.onProgress(d, t));

        l.onStatus("Téléchargement de Minecraft " + Constants.MC_VERSION + "…");
        JsonObject vanilla = MojangInstaller.install(Constants.MC_VERSION, (d, t) -> l.onProgress(d, t));

        l.onStatus("Installation de Fabric " + Constants.LOADER + " " + loaderVer + "…");
        FabricInstaller.Result fabric = FabricInstaller.install(Constants.MC_VERSION, loaderVer);

        Path gameDir = AppPaths.gameDir();
        // Ensure the modpack's required resource pack is in the active list of
        // options.txt — without this, a player who already had options.txt
        // (firstInstallOnly path) sees the pack downloaded into resourcepacks/
        // but inactive in their pack stack.
        OptionsTxtMigration.ensureResourcePackEnabled(gameDir, "NYLERP-PACK.zip");

        Path mcRoot  = AppPaths.rootDir().resolve("minecraft");
        Path clientJar = mcRoot.resolve("versions").resolve(Constants.MC_VERSION)
                .resolve(Constants.MC_VERSION + ".jar");
        Path assetsDir = mcRoot.resolve("assets");

        List<Path> cp = new ArrayList<>();
        cp.addAll(fabric.extraClasspath());
        cp.addAll(MojangInstaller.classpath(vanilla));
        cp.add(clientJar);

        String sep = System.getProperty("path.separator");
        String cpStr = cp.stream().map(Path::toString).collect(Collectors.joining(sep));

        String assetIndex = vanilla.getAsJsonObject("assetIndex").get("id").getAsString();

        List<String> cmd = new ArrayList<>();
        cmd.add(java.toString());
        // macOS: GLFW requires the main thread to be thread 0, done via -XstartOnFirstThread.
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            cmd.add("-XstartOnFirstThread");
        }
        // Heap: -Xmx == -Xms so the JVM never has to resize the heap
        // mid-session (a resize triggers a full GC stall + perceived
        // freeze). Worth the +RAM up front because the player picked
        // their RAM budget knowing the cost.
        cmd.add("-Xmx" + ramMb + "M");
        cmd.add("-Xms" + ramMb + "M");

        // ── Low-pause GC tuning (G1) ────────────────────────────────────
        // Replaces the JDK 21 default G1 args, which produce 250-300 ms
        // young-generation pauses on a 6-8 GB heap with 80+ mods (proven
        // in MrCedriic + Nonolcad freeze reports — both excellent PCs
        // had recurring micro-freezes that aligned with G1 Young pauses).
        // After this tuning, target pauses drop to ~30-50 ms — perceptible
        // only as a tiny "stutter" once per ~30 sec of intense allocation
        // instead of the multi-100ms drops that ruined the play feel.
        //
        // Each flag's role:
        //   MaxGCPauseMillis=50            target pause budget per young GC
        //   G1HeapRegionSize=8M            larger regions reduce per-GC overhead on big heaps
        //   G1NewSizePercent=20            keep eden small so young GCs are fast
        //   G1MaxNewSizePercent=40         cap young so old gen has room for long-lived mod state
        //   G1ReservePercent=20            headroom to avoid evac failures
        //   G1HeapWastePercent=5           tolerate 5% fragmentation, no extra mixed GCs
        //   InitiatingHeapOccupancyPercent=15  start concurrent marking early, prevents full GC
        //   G1MixedGCCountTarget=4         spread mixed GCs across cycles
        //   SurvivorRatio=32               most young objects die — small survivor space is fine
        //   +AlwaysPreTouch                touch all heap pages at start to avoid PF stalls
        //   +DisableExplicitGC             ignore mod-triggered System.gc() calls
        //   +UseStringDeduplication        dedup repeated String values, ~5-10% heap saving
        //   +ParallelRefProcEnabled        parallel reference processing during GC pauses
        //   +PerfDisableSharedMem          skip /tmp/hsperfdata file IO (small win)
        //   ReservedCodeCacheSize=512M     JIT compiler artifacts cache (80-mod load fills the default 240M)
        cmd.add("-XX:+UseG1GC");
        cmd.add("-XX:MaxGCPauseMillis=50");
        cmd.add("-XX:+UnlockExperimentalVMOptions");
        cmd.add("-XX:+ParallelRefProcEnabled");
        cmd.add("-XX:G1HeapRegionSize=8M");
        cmd.add("-XX:G1NewSizePercent=20");
        cmd.add("-XX:G1MaxNewSizePercent=40");
        cmd.add("-XX:G1ReservePercent=20");
        cmd.add("-XX:G1HeapWastePercent=5");
        cmd.add("-XX:InitiatingHeapOccupancyPercent=15");
        cmd.add("-XX:G1MixedGCCountTarget=4");
        cmd.add("-XX:SurvivorRatio=32");
        cmd.add("-XX:+AlwaysPreTouch");
        cmd.add("-XX:+DisableExplicitGC");
        cmd.add("-XX:+UseStringDeduplication");
        cmd.add("-XX:+PerfDisableSharedMem");
        cmd.add("-XX:ReservedCodeCacheSize=512M");

        cmd.add("-Djava.library.path=" + mcRoot.resolve("natives"));
        cmd.add("-Dminecraft.launcher.brand=nylelauncher");
        cmd.add("-Dminecraft.launcher.version=" + readAppVersion());
        cmd.add("-cp");
        cmd.add(cpStr);
        cmd.add(fabric.mainClass());

        // Minecraft game arguments
        cmd.add("--username");   cmd.add(account.username());
        cmd.add("--version");    cmd.add(Constants.MC_VERSION);
        cmd.add("--gameDir");    cmd.add(gameDir.toString());
        cmd.add("--assetsDir");  cmd.add(assetsDir.toString());
        cmd.add("--assetIndex"); cmd.add(assetIndex);
        cmd.add("--uuid");       cmd.add(stripDashes(account.uuid()));
        cmd.add("--accessToken");cmd.add(account.accessToken() != null ? account.accessToken() : "0");
        cmd.add("--userType");   cmd.add(account.isOffline() ? "legacy" : "msa");
        cmd.add("--versionType");cmd.add("release");

        if (joinServer) {
            cmd.add("--quickPlayMultiplayer");
            cmd.add(Constants.SERVER_HOST + ":" + Constants.SERVER_PORT);
        }

        LOG.info("Launching Minecraft as {} ({})", account.username(), account.type());
        LOG.debug("Command ({}): {}", cmd.size(), String.join(" ", cmd));

        // Redirect child stdout+stderr to a dedicated file so we can diagnose crashes
        Path mcLog = pickMinecraftLogFile();
        Files.createDirectories(mcLog.getParent());

        l.onStatus("Lancement de Minecraft…");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(gameDir.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.to(mcLog.toFile()));
        try {
            Process proc = pb.start();
            LOG.info("Minecraft pid = {}, logs → {}", proc.pid(), mcLog);

            // Wait 4 s to see if it crashes at boot and report the first lines of its log
            boolean finishedEarly = proc.waitFor(4, TimeUnit.SECONDS);
            if (finishedEarly) {
                int rc = proc.exitValue();
                String head = Files.exists(mcLog)
                        ? Files.readString(mcLog).lines().limit(60).reduce("", (a, b) -> a + "\n" + b)
                        : "(no log)";
                LOG.error("Minecraft exited early rc={} — first lines of log:\n{}", rc, head);
                throw new IOException("Minecraft a planté immédiatement (code " + rc + "). "
                        + "Voir " + mcLog + " — extraits :\n" + head);
            }
            LOG.info("Minecraft is running (pid {}).", proc.pid());
            return proc;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrompu", ie);
        } catch (IOException e) {
            throw new IOException("Impossible de lancer Minecraft: " + e.getMessage(), e);
        }
    }

    private static Path pickMinecraftLogFile() {
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");
        Path base;
        if (os.contains("mac")) {
            base = Path.of(home, "Library", "Logs", "NyleLauncher");
        } else if (os.contains("win")) {
            String la = System.getenv("LOCALAPPDATA");
            base = (la != null ? Path.of(la) : Path.of(home, "AppData", "Local"))
                    .resolve("NyleLauncher").resolve("Logs");
        } else {
            String xdg = System.getenv("XDG_STATE_HOME");
            base = (xdg != null ? Path.of(xdg) : Path.of(home, ".local", "state"))
                    .resolve("nylelauncher");
        }
        return base.resolve("minecraft.log");
    }

    private static String stripDashes(String uuid) {
        return uuid == null ? "" : uuid.replace("-", "");
    }

    private static String readAppVersion() {
        return "0.1.0";
    }

    private MinecraftLauncher() {}
}
