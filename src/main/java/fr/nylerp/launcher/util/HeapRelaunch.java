package fr.nylerp.launcher.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Self-relaunch of the payload into a JVM with a decent heap.
 *
 * WHY: the installed bootstrap is a jpackage app whose JVM options are FROZEN in its
 * .cfg at install time. Installs made before bootstrap 0.3.20 run with {@code -Xmx256m},
 * which is not enough for the Microsoft-login WebView (JavaFX WebKit + D3D pipeline):
 * the login OOMs ("D3D Screen Updater" / "HttpClient-…-SelectorManager" threads) and the
 * launcher crash-loops. We cannot patch the installed .cfg remotely — but the payload
 * auto-updates on every start, so the payload itself detects the cramped heap and hands
 * over to a fresh 1 GB JVM before JavaFX is initialised. 100% automatic for the player.
 *
 * Strategies, in order (each one falls through to the next on failure):
 *  1. {@code java.home/bin/java[w]} + {@code -cp payload.jar} — the jpackage-bundled
 *     runtime (has the JavaFX modules jlinked in). NB: jpackage's default jlink options
 *     include {@code --strip-native-commands}, so most player installs have NO bin/java;
 *     this mostly serves dev setups and future runtimes built with launchers kept.
 *  2. Re-exec the launcher executable itself ({@link ProcessHandle}) with
 *     {@code _JAVA_OPTIONS="-Xmx1024m -Dnyle.relaunched=1"}. HotSpot parses
 *     {@code _JAVA_OPTIONS} LAST — after the frozen {@code java-options=-Xmx256m} from
 *     the .cfg — so our 1024m wins. This is the strategy that actually fires on player
 *     machines. The child re-runs the bootstrap (payload is cached, hash matches → fast).
 *  3. {@code java}/{@code javaw} from PATH + {@code -cp payload.jar} — last resort; the
 *     PATH JVM usually lacks JavaFX, in which case the child dies instantly and the
 *     hand-over check below catches it.
 *
 * Anti-loop guard: the child carries BOTH the {@code nyle.relaunched=1} system property
 * and the {@code NYLE_RELAUNCHED=1} environment variable; if either is present we never
 * relaunch again. If the child is somehow still cramped, we stay in-process and force the
 * JavaFX software pipeline ({@code prism.order=sw}) to shed the D3D texture memory.
 *
 * Hand-over check: after {@code start()} we watch the child for 3 s; if it exits that
 * fast the relaunch failed (bad java, missing JavaFX…) and we fall back in-process so the
 * player is never left staring at nothing.
 *
 * Healthy installs (bootstrap ≥ 0.3.20, -Xmx1024m in the .cfg) short-circuit on the very
 * first check ({@code maxMemory() ≥ 512 MB}) — zero behaviour change for them.
 */
public final class HeapRelaunch {

    private static final Logger LOG = LoggerFactory.getLogger(HeapRelaunch.class);

    /** Below this max-heap the Microsoft-login WebView is known to OOM (256m installs). */
    private static final long MIN_COMFORTABLE_HEAP = 512L * 1024 * 1024;

    /** Anti-loop flags carried by the relaunched process. */
    public static final String PROP_FLAG = "nyle.relaunched";
    public static final String ENV_FLAG  = "NYLE_RELAUNCHED";

    /** Exact tokens injected via _JAVA_OPTIONS for strategy 2 — scrubbed from the game env. */
    public static final String INJECTED_XMX  = "-Xmx1024m";
    public static final String INJECTED_PROP = "-D" + PROP_FLAG + "=1";

    private static final String MAIN_CLASS = "fr.nylerp.launcher.Main";

    private HeapRelaunch() {}

    /**
     * Detects a cramped heap and hands over to a relaunched 1 GB JVM.
     *
     * @return {@code true} if a relaunched process took over — the caller MUST
     *         {@code System.exit(0)} immediately and touch nothing else (especially not
     *         JavaFX). {@code false} → continue in-process exactly as before.
     */
    public static boolean relaunchIfCramped(String[] args) {
        long max = Runtime.getRuntime().maxMemory();
        if (max >= MIN_COMFORTABLE_HEAP) return false; // healthy install → untouched flow

        try {
            if ("1".equals(System.getProperty(PROP_FLAG)) || "1".equals(System.getenv(ENV_FLAG))) {
                // Already relaunched once and STILL cramped (heap override was lost?).
                // Never loop — degrade rendering instead.
                LOG.warn("Heap still cramped ({} MB max) after a relaunch — staying in-process", max >> 20);
                applyLowMemoryRendering();
                return false;
            }

            LOG.warn("Cramped heap detected ({} MB max — old 256m install) — relaunching into a 1 GB JVM", max >> 20);
            if (relaunchWithBundledJava(args))   return true;
            if (relaunchWithAppExecutable(args)) return true;
            if (relaunchWithPathJava(args))      return true;

            LOG.warn("All relaunch strategies failed — continuing in-process with software rendering");
            applyLowMemoryRendering();
        } catch (Throwable t) {
            // NEVER break the normal flow because of the relaunch machinery.
            LOG.warn("Heap relaunch failed unexpectedly — continuing in-process", t);
            try { applyLowMemoryRendering(); } catch (Throwable ignored) { }
        }
        return false;
    }

    // ── Strategy 1: jpackage-bundled java ───────────────────────────────────

    private static boolean relaunchWithBundledJava(String[] args) {
        try {
            Path javaBin = bundledJavaBinary();
            if (javaBin == null) {
                LOG.info("relaunch: no java binary under java.home (jpackage strips native commands) — next strategy");
                return false;
            }
            Path jar = payloadJar();
            if (jar == null) {
                LOG.info("relaunch: payload code source is not a jar file — next strategy");
                return false;
            }
            List<String> cmd = new ArrayList<>();
            cmd.add(javaBin.toString());
            cmd.add(INJECTED_XMX);
            cmd.add(INJECTED_PROP);
            cmd.add("-cp");
            cmd.add(jar.toString());
            cmd.add(MAIN_CLASS);
            cmd.addAll(List.of(args));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put(ENV_FLAG, "1");
            return handOver(pb, "bundled java (" + javaBin + ")");
        } catch (Throwable t) {
            LOG.warn("relaunch via bundled java failed: {}", t.toString());
            return false;
        }
    }

    private static Path bundledJavaBinary() {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isBlank()) return null;
        Path bin = Paths.get(javaHome, "bin");
        if (isWindows()) {
            // javaw.exe = GUI subsystem → no black console window behind the launcher.
            Path javaw = bin.resolve("javaw.exe");
            if (Files.isExecutable(javaw)) return javaw;
            Path java = bin.resolve("java.exe");
            return Files.isExecutable(java) ? java : null;
        }
        Path java = bin.resolve("java");
        return Files.isExecutable(java) ? java : null;
    }

    // ── Strategy 2: re-exec the launcher executable with _JAVA_OPTIONS ──────

    private static boolean relaunchWithAppExecutable(String[] args) {
        try {
            Optional<String> exeOpt = ProcessHandle.current().info().command();
            if (exeOpt.isEmpty()) {
                LOG.info("relaunch: current executable unknown — next strategy");
                return false;
            }
            Path exe = Paths.get(exeOpt.get());
            String name = exe.getFileName().toString().toLowerCase(Locale.ROOT);
            // A bare java/javaw process = gradle/IDE dev run; re-execing it would just
            // restart a JVM with no classpath. Skip.
            if (name.equals("java") || name.equals("java.exe")
                    || name.equals("javaw") || name.equals("javaw.exe")) {
                LOG.info("relaunch: current process is a bare JVM (dev run) — next strategy");
                return false;
            }
            if (!Files.isExecutable(exe)) return false;

            List<String> cmd = new ArrayList<>();
            cmd.add(exe.toString());
            cmd.addAll(List.of(args));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            // HotSpot parses _JAVA_OPTIONS LAST — after the .cfg's frozen -Xmx256m — so
            // our -Xmx1024m wins. Also smuggles the anti-loop property into the child.
            String injected = INJECTED_XMX + " " + INJECTED_PROP;
            String existing = System.getenv("_JAVA_OPTIONS");
            pb.environment().put("_JAVA_OPTIONS",
                    (existing == null || existing.isBlank()) ? injected : existing + " " + injected);
            pb.environment().put(ENV_FLAG, "1");
            return handOver(pb, "launcher executable (" + exe.getFileName() + ")");
        } catch (Throwable t) {
            LOG.warn("relaunch via launcher executable failed: {}", t.toString());
            return false;
        }
    }

    // ── Strategy 3: java from PATH (last resort) ─────────────────────────────

    private static boolean relaunchWithPathJava(String[] args) {
        try {
            Path jar = payloadJar();
            if (jar == null) return false;
            List<String> cmd = new ArrayList<>();
            cmd.add(isWindows() ? "javaw" : "java"); // resolved via PATH by ProcessBuilder
            cmd.add(INJECTED_XMX);
            cmd.add(INJECTED_PROP);
            cmd.add("-cp");
            cmd.add(jar.toString());
            cmd.add(MAIN_CLASS);
            cmd.addAll(List.of(args));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put(ENV_FLAG, "1");
            // If the PATH JVM lacks JavaFX the child dies instantly → handOver() catches it.
            return handOver(pb, "PATH java");
        } catch (Throwable t) {
            LOG.warn("relaunch via PATH java failed: {}", t.toString());
            return false;
        }
    }

    // ── Shared plumbing ──────────────────────────────────────────────────────

    /**
     * Starts the child and confirms the hand-over: if it dies within 3 s (bad binary,
     * missing JavaFX, rejected option…) the relaunch is considered FAILED and the caller
     * falls back in-process — the player must never end up with no window at all.
     */
    private static boolean handOver(ProcessBuilder pb, String label) throws Exception {
        pb.inheritIO();
        Process p = pb.start();
        try {
            if (p.waitFor(3, TimeUnit.SECONDS)) {
                LOG.warn("relaunch via {} exited immediately (rc={}) — falling back", label, p.exitValue());
                return false;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            // Can't tell — the child is most likely alive; do NOT double-launch the UI.
        }
        LOG.info("Relaunched into a 1 GB JVM via {} (pid {}) — this process exits now", label, p.pid());
        return true;
    }

    /** The payload jar we are running from, or null when running from classes (dev). */
    private static Path payloadJar() {
        try {
            CodeSource cs = fr.nylerp.launcher.Main.class.getProtectionDomain().getCodeSource();
            if (cs == null || cs.getLocation() == null) return null;
            Path p = Paths.get(cs.getLocation().toURI());
            if (!Files.isRegularFile(p)) return null;
            if (!p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) return null;
            return p;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * In-process last resort on a cramped heap: force the JavaFX SOFTWARE pipeline.
     * Drops the D3D/Metal texture pipeline and its "D3D Screen Updater" thread — the
     * main consumers that OOM during the Microsoft-login WebView. WebView and Media
     * still render (sw is JavaFX's supported universal fallback pipeline), just slower.
     * Must run BEFORE the JavaFX toolkit is initialised — which is the case, since we
     * are called from Main.main() ahead of Application.launch().
     */
    private static void applyLowMemoryRendering() {
        if (System.getProperty("prism.order") == null) {
            System.setProperty("prism.order", "sw");
            LOG.warn("prism.order=sw applied (low-memory in-process fallback)");
        }
    }

    /**
     * Removes OUR injected heap override from a child-process environment. Called before
     * spawning Minecraft: the relaunched launcher's env carries
     * {@code _JAVA_OPTIONS=-Xmx1024m -Dnyle.relaunched=1}, which HotSpot parses LAST —
     * it would silently cap the game's heap at 1 GB. Only our exact tokens are stripped;
     * a player's own _JAVA_OPTIONS content is preserved. No-op when this process was not
     * relaunched.
     */
    public static void scrubInjectedEnv(Map<String, String> env) {
        try {
            if (!"1".equals(System.getenv(ENV_FLAG)) && !"1".equals(System.getProperty(PROP_FLAG))) return;
            env.remove(ENV_FLAG);
            String opts = env.get("_JAVA_OPTIONS");
            if (opts != null) {
                String cleaned = opts
                        .replace(INJECTED_PROP, " ")
                        .replace(INJECTED_XMX, " ")
                        .replaceAll("\\s+", " ")
                        .trim();
                if (cleaned.isEmpty()) env.remove("_JAVA_OPTIONS");
                else env.put("_JAVA_OPTIONS", cleaned);
            }
        } catch (Throwable t) {
            LOG.warn("scrubInjectedEnv failed (non-fatal): {}", t.toString());
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
