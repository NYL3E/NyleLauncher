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
import fr.nylerp.launcher.util.SystemRam;

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
        // Heap — CLAMPED to physical RAM. A default/oversized -Xmx (default 4 GB, slider up to
        // 16 GB) committed up front (the old -Xms==-Xmx, made worse by +AlwaysPreTouch below) is
        // exactly what makes the spawned JVM fail to reserve/commit its heap at launch on common
        // 4–8 GB machines. We cap -Xmx at physicalRam-2GB (floor 2 GB) and start with a SMALL -Xms
        // so the JVM commits lazily and can never fail at init. (physMb < 0 = detection failed →
        // keep the user's value, still lazy + no pre-touch so init is safe.)
        long physMb = SystemRam.totalMb();
        int maxMb = (physMb > 0) ? Math.max(2048, (int) Math.min(ramMb, physMb - 2048)) : ramMb;
        int initMb = Math.min(maxMb, 1024);
        cmd.add("-Xmx" + maxMb + "M");
        cmd.add("-Xms" + initMb + "M");

        // ── G1 GC (back to v1 baseline after ZGC regression) ────────────
        // Tested progression on MrCedriic (RTX 3060, 8 GB heap, 193 mods):
        //   1.0.33 G1 v1 (MaxGCPauseMillis=50, NewSize=20%):
        //     max pause 323 ms, ~7 hitches/min, glfwPollEvents max 1264 ms
        //   1.0.34 G1 v2 (MaxGCPauseMillis=25, NewSize=30%):
        //     max pause 522 ms (WORSE — bigger eden, smaller budget bust)
        //   1.0.35 ZGC:
        //     STW pauses <1 ms (great), BUT Cycles ate CPU concurrently —
        //     when heap hit 75%+ ZGC fired Major cycles back-to-back, each
        //     using ~25 s of CPU concurrent work over 8 cycles, starving
        //     the render thread. Result: glfwPollEvents stalls 2.3-3.7 s
        //     each (vs 1.2 s with G1) — visually MUCH worse for player.
        //
        // Lesson: with 8 GB heap + 193 mods + 20 MB/s sustained alloc,
        // ZGC has no headroom and concurrent collection competes with
        // rendering. G1 v1 with its predictable short young-gen pauses
        // is the better tradeoff until we either:
        //   (a) reduce mod count, (b) raise default heap, or (c) make
        //   Iris+heavy mods optional.
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
        // NOTE: -XX:+AlwaysPreTouch removed — it physically commits the ENTIRE -Xmx at JVM init,
        // which is the main cause of "Failed to launch JVM" on low-RAM machines. With the clamped
        // -Xmx + small -Xms above, the heap commits lazily and start-up never fails on memory.
        cmd.add("-XX:+DisableExplicitGC");
        cmd.add("-XX:+UseStringDeduplication");
        cmd.add("-XX:+PerfDisableSharedMem");
        cmd.add("-XX:ReservedCodeCacheSize=512M");

        // ── AWT / Java2D mitigation (keep — independent of GC choice) ──
        // MenuCanvas (used by Nyle menus) uses BufferedImage + Graphics2D
        // which lazily initialises Windows AWT (sun.awt.windows.WToolkit).
        // Disabling DDraw/D3D/OpenGL Java2D pipelines reduces AWT footprint.
        cmd.add("-Dsun.java2d.noddraw=true");
        cmd.add("-Dsun.java2d.d3d=false");
        cmd.add("-Dsun.java2d.opengl=false");
        cmd.add("-Dsun.awt.noerasebackground=true");
        cmd.add("-Dsun.awt.disablegrab=true");

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
