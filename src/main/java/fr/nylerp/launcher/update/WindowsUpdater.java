package fr.nylerp.launcher.update;

import fr.nylerp.launcher.config.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Sparkle-style Windows installer driver. The previous flow ({@code msiexec /qb /norestart})
 * had three failure modes: (a) the old launcher.exe was still file-locked when MSI tried to
 * replace it, so the upgrade silently downgraded to "no-op"; (b) {@code /qb} runs without
 * verbose logging, so failures are invisible; (c) after install nothing relaunched the new
 * exe — the user just saw the launcher disappear.
 *
 * <p>Fix: write a small {@code .bat} script to {@code %TEMP%}, spawn it detached via
 * {@code cmd /c start "" /B}, then exit. The script:
 *
 * <ol>
 *   <li>Waits 4 s so the parent process is fully gone (file locks released).</li>
 *   <li>Runs {@code msiexec /i ... /passive /norestart /L*v <log>} — passive UI shows the
 *       progress bar but blocks on errors so the user CAN see them, while {@code /L*v}
 *       writes a complete verbose MSI log to %APPDATA%/NyleLauncher/logs/.</li>
 *   <li>If {@code msiexec} returns 0, launches the new launcher executable.</li>
 *   <li>Always tees its own progress to {@code update-script-<ts>.log} for diagnosis.</li>
 * </ol>
 *
 * <p>This is the classic Sparkle / Squirrel update pattern adapted to MSI semantics.
 */
public final class WindowsUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(WindowsUpdater.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public static void runInPlace(Path msi) throws IOException {
        String stamp = LocalDateTime.now().format(TS);
        Path logsDir = Paths.get(System.getenv("APPDATA"), Constants.APP_NAME, "logs");
        Files.createDirectories(logsDir);

        Path msiLog = logsDir.resolve("msi-update-" + stamp + ".log");
        Path scriptLog = logsDir.resolve("update-script-" + stamp + ".log");
        Path script = Files.createTempFile("nyle-update-", ".bat");

        String launcherExe = locateInstalledLauncherExe();

        StringBuilder bat = new StringBuilder();
        bat.append("@echo off\r\n");
        bat.append("setlocal\r\n");
        // Tee everything to scriptLog
        bat.append("echo [").append(stamp).append("] update script started > \"")
           .append(scriptLog).append("\"\r\n");
        bat.append("echo MSI = ").append(msi).append(" >> \"").append(scriptLog).append("\"\r\n");
        bat.append("echo MSI log = ").append(msiLog).append(" >> \"").append(scriptLog).append("\"\r\n");
        bat.append("echo Launcher target = ").append(launcherExe == null ? "(unknown — will skip relaunch)" : launcherExe)
           .append(" >> \"").append(scriptLog).append("\"\r\n");
        // Wait 4 seconds for parent process to fully exit (file locks)
        bat.append("echo Waiting 4 s for parent to exit... >> \"").append(scriptLog).append("\"\r\n");
        bat.append("ping -n 5 127.0.0.1 > nul\r\n");
        // Run msiexec with verbose logging (/L*v) and passive UI (progress bar but no buttons)
        bat.append("echo Running msiexec /i ... /passive /norestart /L*v ... >> \"").append(scriptLog).append("\"\r\n");
        bat.append("msiexec /i \"").append(msi).append("\" /passive /norestart /L*v \"")
           .append(msiLog).append("\"\r\n");
        bat.append("set MSIRC=%ERRORLEVEL%\r\n");
        bat.append("echo msiexec exit code: %MSIRC% >> \"").append(scriptLog).append("\"\r\n");
        // If msiexec succeeded AND we found the new launcher, relaunch it
        bat.append("if %MSIRC%==0 (\r\n");
        if (launcherExe != null) {
            bat.append("  echo Launching new exe... >> \"").append(scriptLog).append("\"\r\n");
            bat.append("  start \"\" \"").append(launcherExe).append("\"\r\n");
        } else {
            bat.append("  echo (no launcher path resolved — user must launch manually) >> \"")
               .append(scriptLog).append("\"\r\n");
        }
        bat.append(") else (\r\n");
        bat.append("  echo Update FAILED with code %MSIRC% — see msi log >> \"")
           .append(scriptLog).append("\"\r\n");
        bat.append(")\r\n");
        bat.append("endlocal\r\n");

        Files.writeString(script, bat.toString(),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        LOG.info("Update script: {}", script);
        LOG.info("MSI log will be at: {}", msiLog);
        LOG.info("Script log will be at: {}", scriptLog);

        // Spawn the batch detached so it survives our exit. cmd /c start "" /B <script>
        // creates a new process group not tied to our STDIN/OUT, ensuring it keeps running
        // after the launcher closes.
        ProcessBuilder pb = new ProcessBuilder(
                "cmd.exe", "/c", "start", "\"\"", "/B", script.toString());
        pb.start();
        LOG.info("Update script spawned, exiting launcher in 1.5 s");
    }

    /** Best-effort guess of where the installed NyleLauncher.exe lives — needed so the
     *  batch script can re-launch the freshly-updated version. We walk up from
     *  {@code java.home} (jpackage runtime path) → {@code <APP>/runtime/bin/Java/bin}
     *  to {@code <APP>/NyleLauncher.exe}. Returns null if we can't resolve. */
    private static String locateInstalledLauncherExe() {
        try {
            String javaHome = System.getProperty("java.home");
            if (javaHome == null) return null;
            Path p = Paths.get(javaHome).toAbsolutePath();
            // jpackage runtime lives at <App>/runtime/bin/<jdk>/. Walk up max 6 levels.
            for (int i = 0; i < 6 && p != null; i++) {
                Path candidate = p.resolve(Constants.APP_NAME + ".exe");
                if (Files.exists(candidate)) return candidate.toString();
                p = p.getParent();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private WindowsUpdater() {}
}
