package fr.nylerp.launcher.update;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Sparkle-style in-place auto-update for macOS.
 *
 * The launcher cannot overwrite its own .app bundle while it is running, so we:
 *
 *   1. Mount the freshly downloaded DMG to a private mount point.
 *   2. Resolve the .app inside the mount.
 *   3. Copy it to {@code /tmp/NyleLauncherUpdate.staged.app} (read+write copy off the mount).
 *   4. Write a small shell script that, in order: waits for our PID to exit, swaps the staged copy
 *      over our installed bundle, detaches the DMG, removes the temp DMG file, then re-opens
 *      the freshly installed app.
 *   5. Spawn the script with {@code nohup} so it survives our process death, then quit ourselves.
 *
 * This mirrors how Sparkle (the de-facto macOS auto-update framework) does it. The trick is
 * isolating the file-replacement work into a process that does NOT have any file handles inside
 * the bundle being replaced.
 */
public final class MacUpdater {
    private static final Logger LOG = LoggerFactory.getLogger(MacUpdater.class);

    public static void runInPlace(Path dmg) throws IOException {
        Path installedApp = currentAppBundle();
        if (installedApp == null) {
            throw new IOException("Cannot determine the current .app bundle path — aborting in-place update");
        }
        LOG.info("In-place update target: {}", installedApp);

        // 1) Mount DMG
        Path mountPoint = Paths.get("/tmp/nyle-update-mount-" + System.currentTimeMillis());
        run("hdiutil", "attach", "-nobrowse", "-quiet", "-mountpoint", mountPoint.toString(), dmg.toString());

        // 2) Find .app
        Path sourceApp = findAppIn(mountPoint);
        if (sourceApp == null) {
            tryDetach(mountPoint);
            throw new IOException("No .app bundle found inside DMG: " + dmg);
        }
        LOG.info("Found bundle in DMG: {}", sourceApp);

        // 3) Stage copy outside the mount
        Path staged = Paths.get("/tmp/NyleLauncherUpdate-" + System.currentTimeMillis() + ".app");
        run("cp", "-R", sourceApp.toString(), staged.toString());
        LOG.info("Staged copy: {}", staged);

        // 4) Build update script
        Path script = Paths.get("/tmp/nyle-update-" + System.currentTimeMillis() + ".sh");
        long pid = ProcessHandle.current().pid();
        String content = String.format("""
                #!/bin/bash
                set -e
                # Wait until the parent NyleLauncher process exits (max 30s).
                for i in $(seq 1 30); do
                  if ! kill -0 %d 2>/dev/null; then break; fi
                  sleep 1
                done
                # Replace the installed bundle.
                rm -rf "%s"
                mv "%s" "%s"
                # Detach the DMG and clean up.
                hdiutil detach "%s" -quiet || true
                rm -f "%s"
                # Re-launch the new app via Launch Services.
                open "%s"
                # Self-cleanup.
                rm -f "$0"
                """,
                pid,
                installedApp.toAbsolutePath(),
                staged.toAbsolutePath(), installedApp.toAbsolutePath(),
                mountPoint.toAbsolutePath(),
                dmg.toAbsolutePath(),
                installedApp.toAbsolutePath()
        );
        Files.writeString(script, content, StandardOpenOption.CREATE_NEW);
        run("chmod", "+x", script.toString());
        LOG.info("Update script: {}", script);

        // 5) Spawn the script detached and quit
        ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c",
                "nohup " + script.toAbsolutePath() + " >/tmp/nyle-update.log 2>&1 &");
        pb.start();
        LOG.info("Update script launched in background. Quitting current launcher.");
    }

    /** Locate the currently running .app bundle by walking up from {@code java.home}. */
    static Path currentAppBundle() {
        // NyleLauncher.app/Contents/runtime/Contents/Home   ←  java.home is here when launched via jpackage
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isEmpty()) return null;
        Path p = Paths.get(javaHome).toAbsolutePath();
        for (int i = 0; i < 8 && p != null; i++) {
            if (p.getFileName() != null && p.getFileName().toString().endsWith(".app")) return p;
            p = p.getParent();
        }
        return null;
    }

    private static Path findAppIn(Path mount) throws IOException {
        try (var stream = Files.list(mount)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".app"))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static void run(String... cmd) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            Process pr = pb.start();
            int rc = pr.waitFor();
            if (rc != 0) {
                String out = new String(pr.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                throw new IOException("Command failed: " + String.join(" ", cmd) + " — " + out);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted running " + String.join(" ", cmd));
        }
    }

    private static void tryDetach(Path mount) {
        try { run("hdiutil", "detach", mount.toString(), "-quiet", "-force"); }
        catch (Exception ignored) {}
    }

    private MacUpdater() {}
}
