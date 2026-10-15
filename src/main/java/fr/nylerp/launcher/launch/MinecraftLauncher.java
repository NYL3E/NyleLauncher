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
        // One-shot keybind defaults for existing players (Tom's terminal + PointBlank
        // unbound, perspective Y→F5). New players get these from the shipped options.txt.
        OptionsTxtMigration.applyKeybindDefaultsOnce(gameDir);
        // Pokényle : Complementary Reimagined choisi dans Iris + profil allégé, UNE SEULE FOIS
        // par instance (marqueur .nyle_shaders_v1) — le choix du joueur (touche K) tient ensuite.
        fr.nylerp.launcher.update.ShaderDefaults.applyIrisDefaultsOnce(
                gameDir, fr.nylerp.launcher.config.ModeDeJeu.courant());

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
        int maxMb = clampHeapMb(ramMb, SystemRam.totalMb());
        int initMb = Math.min(maxMb, 1024);
        cmd.add("-Xmx" + maxMb + "M");
        cmd.add("-Xms" + initMb + "M");

        // ── G1 GC : voir gcFlags() et les constantes GC_* plus bas ──────
        cmd.addAll(gcFlags());

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
            cmd.add(Constants.serverHost() + ":" + Constants.serverPort());
        }

        LOG.info("Launching Minecraft as {} ({})", account.username(), account.type());
        LOG.debug("Command ({}): {}", cmd.size(), String.join(" ", cmd));

        // Redirect child stdout+stderr to a dedicated file so we can diagnose crashes
        Path mcLog = pickMinecraftLogFile();
        Files.createDirectories(mcLog.getParent());

        l.onStatus("Lancement de Minecraft…");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(gameDir.toFile());
        // If this launcher process was heap-relaunched (HeapRelaunch), its environment
        // carries _JAVA_OPTIONS=-Xmx1024m … which HotSpot parses LAST — it would silently
        // cap the game's heap at 1 GB. Strip our injected tokens from the game's env.
        fr.nylerp.launcher.util.HeapRelaunch.scrubInjectedEnv(pb.environment());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.to(mcLog.toFile()));
        try {
            Process proc = pb.start();
            LOG.info("Minecraft pid = {}, logs → {}", proc.pid(), mcLog);

            // Wait 4 s to see if it crashes at boot and report the first lines of its log
            boolean finishedEarly = proc.waitFor(4, TimeUnit.SECONDS);
            if (finishedEarly) {
                int rc = proc.exitValue();
                // Le journal du jeu vient d'un process qui écrit dans la page de codes du
                // SYSTÈME — sur un Windows français, un seul accent suffisait à faire échouer
                // une lecture UTF-8 stricte, et la joueuse voyait « Input length = 3 » à la
                // place de la panne réelle. On lit donc en tolérant, et jamais autrement.
                String head = fr.nylerp.launcher.util.LectureTexte.lireOuDefaut(mcLog, "(no log)")
                        .lines().limit(60).reduce("", (a, b) -> a + "\n" + b);
                LOG.error("Minecraft exited early rc={} — first lines of log:\n{}", rc, head);
                // Le journal complet reste sur le disque et part avec le rapport ; ce que le
                // JOUEUR reçoit, c'est la marche à suivre. Une pile d'appels dans une barre
                // d'état n'a jamais aidé personne à relancer son jeu.
                String conseil = DiagnosticCrash.conseil(head, rc);
                if (conseil == null) {
                    String parlante = DiagnosticCrash.ligneParlante(head);
                    conseil = "Le jeu s'est fermé au démarrage (code " + rc + ")."
                            + (parlante.isBlank() ? "" : " " + parlante)
                            + " Relance le launcher ; si ça recommence, envoie un rapport au staff.";
                }
                throw new IOException(conseil);
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

    // ── Mémoire ─────────────────────────────────────────────────────────────────────────────

    /** Plafond physique du -Xmx : RAM − 2 Go, plancher 2 Go ; {@code physMb <= 0} (détection
     *  ratée) → la valeur demandée telle quelle. */
    public static int clampHeapMb(int requestedMb, long physMb) {
        return (physMb > 0) ? Math.max(2048, (int) Math.min(requestedMb, physMb - 2048)) : requestedMb;
    }

    // ── Drapeaux G1 du CLIENT (15/10/2026) ───────────────────────────────────────────────────
    // Historique : jeu « Aikar » (drapeaux de SERVEUR à gros tas), calibré en mai 2026 sur un tas
    // de 8 Go (MrCedriic, RTX 3060, 193 mods) — G1 v1 gardé après la régression ZGC (1.0.35 :
    // cycles concurrents qui affamaient le fil de rendu, glfwPollEvents figé 2,3-3,7 s).
    //
    // Diagnostic du 15/10/2026 (perf-shaders, RAPPORT § 2) : client pokényle à 4 Go de tas,
    // ensemble vivant 3,3-3,6 Go, 2 187 nettoyages complets en 3 jours (~0,7 s de gel chacun).
    // Les réserves de serveur mangeaient la marge : 20 % minimum de jeune génération + 20 % de
    // réserve laissaient ~2,5 Go « confortables » pour 3,4 Go d'objets durables, et un IHOP fixe
    // à 15 % gardait un marquage concurrent quasi permanent (~6 pauses/min) qui prenait du CPU au
    // rendu. Ces valeurs sont en cours de mesure au banc : les régler ICI, nulle part ailleurs.

    /** Cible de pause G1 (ms). Inchangée. */
    static final int GC_MAX_PAUSE_MS = 50;
    /** Taille de région G1. Inchangée (8 Mo : peu d'objets « humongous » pour les gros tableaux de chunks). */
    static final String GC_HEAP_REGION_SIZE = "8M";
    /** Jeune génération MINIMUM (% du tas). 20 → 5 : le défaut G1 ; laisse G1 rétrécir la jeune
     *  génération quand l'ensemble vivant remplit le tas, au lieu de forcer un nettoyage complet. */
    static final int GC_NEW_SIZE_PERCENT = 5;
    /** Jeune génération maximum (% du tas). Inchangée. */
    static final int GC_MAX_NEW_SIZE_PERCENT = 40;
    /** Réserve contre les échecs de promotion (% du tas). 20 → 10 : le défaut G1 ; 20 % de 4 Go
     *  immobilisaient 800 Mo dont le client a besoin pour ses objets durables. */
    static final int GC_RESERVE_PERCENT = 10;
    /** Déchet toléré avant nettoyage mixte (% du tas). Inchangé. */
    static final int GC_HEAP_WASTE_PERCENT = 5;
    /** Nombre cible de nettoyages mixtes après un marquage. Inchangé. */
    static final int GC_MIXED_COUNT_TARGET = 4;
    /** Rapport eden / survivant. Inchangé. */
    static final int GC_SURVIVOR_RATIO = 32;
    /** Seuil de déclenchement du marquage concurrent (% du tas), ou {@code -1} = IHOP ADAPTATIF
     *  de G1 (défaut de la JVM). Était 15 : marquage quasi permanent. Supprimé le 15/10/2026. */
    static final int GC_INITIATING_HEAP_OCCUPANCY_PERCENT = -1;
    /** Cache de code JIT. Inchangé (beaucoup de mods = beaucoup de code compilé). */
    static final String GC_RESERVED_CODE_CACHE = "512M";

    /** Les drapeaux JVM du ramasse-miettes passés au jeu, dans l'ordre. */
    static List<String> gcFlags() {
        List<String> f = new ArrayList<>();
        f.add("-XX:+UseG1GC");
        f.add("-XX:MaxGCPauseMillis=" + GC_MAX_PAUSE_MS);
        // G1NewSizePercent / G1MaxNewSizePercent sont des options EXPÉRIMENTALES de HotSpot.
        f.add("-XX:+UnlockExperimentalVMOptions");
        f.add("-XX:+ParallelRefProcEnabled");
        f.add("-XX:G1HeapRegionSize=" + GC_HEAP_REGION_SIZE);
        f.add("-XX:G1NewSizePercent=" + GC_NEW_SIZE_PERCENT);
        f.add("-XX:G1MaxNewSizePercent=" + GC_MAX_NEW_SIZE_PERCENT);
        f.add("-XX:G1ReservePercent=" + GC_RESERVE_PERCENT);
        f.add("-XX:G1HeapWastePercent=" + GC_HEAP_WASTE_PERCENT);
        if (GC_INITIATING_HEAP_OCCUPANCY_PERCENT >= 0) {
            f.add("-XX:InitiatingHeapOccupancyPercent=" + GC_INITIATING_HEAP_OCCUPANCY_PERCENT);
        }
        f.add("-XX:G1MixedGCCountTarget=" + GC_MIXED_COUNT_TARGET);
        f.add("-XX:SurvivorRatio=" + GC_SURVIVOR_RATIO);
        // NOTE: -XX:+AlwaysPreTouch removed — it physically commits the ENTIRE -Xmx at JVM init,
        // which is the main cause of "Failed to launch JVM" on low-RAM machines. With the clamped
        // -Xmx + small -Xms, the heap commits lazily and start-up never fails on memory.
        f.add("-XX:+DisableExplicitGC");
        f.add("-XX:+UseStringDeduplication");
        f.add("-XX:+PerfDisableSharedMem");
        f.add("-XX:ReservedCodeCacheSize=" + GC_RESERVED_CODE_CACHE);
        return f;
    }

    private static String stripDashes(String uuid) {
        return uuid == null ? "" : uuid.replace("-", "");
    }

    private static String readAppVersion() {
        return "0.1.0";
    }

    private MinecraftLauncher() {}
}
