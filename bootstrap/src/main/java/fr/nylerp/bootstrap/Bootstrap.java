package fr.nylerp.bootstrap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * NyleLauncher bootstrap.
 *
 * Single responsibility: fetch the manifest, download (or reuse a cached copy
 * of) the payload JAR, verify its SHA-256, then load it and invoke its main().
 *
 * No UI, no business logic, no business URLs hardcoded except {@link #MANIFEST_URL}.
 * That way the actual launcher (UI, auth, modpack updater, every button & label)
 * lives in the payload and can be replaced 100% remotely without forcing users
 * to reinstall anything.
 */
public final class Bootstrap {

    /**
     * Bootstrap version. This is the SOURCE OF TRUTH for "what is the user running".
     * jpackage stamps this same string into the .app bundle's Info.plist so OS-level updates
     * see it too. Bumped together with the launcher's user-visible release tag (v0.3.5, …).
     *
     * Exposed to the payload at runtime via the {@code nyleauth.installedVersion} system
     * property — that's how SelfUpdater knows whether an update is needed instead of relying
     * on the payload's own (independently-versioned) {@code Constants.APP_VERSION}.
     */
    public static final String VERSION = "0.3.5";

    /** The only business URL inside the bootstrap. Stable forever. */
    private static final String MANIFEST_URL =
            "https://nyle-mc-server.pages.dev/launcher/manifest.json";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    public static void main(String[] args) throws Exception {
        // Capture any uncaught exception (incl. JVM/JavaFX init failures) into a crash log
        // file so the next launcher start can prompt the user to send it to the dev.
        Thread.setDefaultUncaughtExceptionHandler(Bootstrap::writeCrash);
        try {
            mainImpl(args);
        } catch (Throwable t) {
            writeCrash(Thread.currentThread(), t);
            throw t;
        }
    }

    private static void mainImpl(String[] args) throws Exception {
        // Expose the bootstrap version to the payload. SelfUpdater reads this to know
        // what's actually installed on disk instead of relying on the payload's own
        // (independently-versioned) Constants.APP_VERSION, which would create infinite
        // update loops when the bootstrap is bumped without a coordinated payload bump.
        System.setProperty("nyleauth.installedVersion", VERSION);
        log("bootstrap version=" + VERSION);

        Path cache = cacheDir();
        Files.createDirectories(cache);

        Manifest manifest;
        try {
            manifest = fetchManifest();
            log("manifest version=" + manifest.version + " sha=" + shortSha(manifest.sha256));
        } catch (Exception e) {
            log("WARN cannot fetch manifest (" + e.getMessage() + ") — falling back to cache");
            Path cached = mostRecentCached(cache);
            if (cached == null) {
                throw new RuntimeException("No internet AND no cached payload available", e);
            }
            launch(cached, args, "fr.nylerp.launcher.Main");
            return;
        }

        Path target = cache.resolve("launcher-" + manifest.version + ".jar");
        boolean needDownload = !Files.exists(target)
                || !sha256(target).equalsIgnoreCase(manifest.sha256);
        if (needDownload) {
            log("downloading payload " + manifest.version + " ← " + manifest.jarUrl);
            downloadTo(manifest.jarUrl, target);
            String got = sha256(target);
            if (!got.equalsIgnoreCase(manifest.sha256)) {
                Files.deleteIfExists(target);
                throw new RuntimeException("SHA-256 mismatch on payload — expected "
                        + manifest.sha256 + " got " + got);
            }
            log("downloaded + verified");
        } else {
            log("payload " + manifest.version + " already cached, hash matches");
        }

        launch(target, args, manifest.mainClass);
    }

    // ── Launching ────────────────────────────────────────────────────────────

    private static void launch(Path jar, String[] args, String mainClass) throws Exception {
        log("launch " + mainClass + " from " + jar.getFileName());
        URL[] urls = { jar.toUri().toURL() };
        // Don't close this classloader: the payload may keep classes loaded long
        // after main() returns (JavaFX app starts on its own thread).
        URLClassLoader cl = new URLClassLoader("payload", urls, Bootstrap.class.getClassLoader());
        Thread.currentThread().setContextClassLoader(cl);
        Class<?> klass = Class.forName(mainClass, true, cl);
        Method main = klass.getMethod("main", String[].class);
        main.invoke(null, (Object) args);
    }

    // ── HTTP ─────────────────────────────────────────────────────────────────

    private static Manifest fetchManifest() throws IOException, InterruptedException {
        // Cache-buster so CDN never serves a stale manifest.
        String url = MANIFEST_URL + "?t=" + System.currentTimeMillis();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "NyleLauncherBootstrap/1.0")
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("HTTP " + resp.statusCode() + " on " + MANIFEST_URL);
        }
        JsonObject o = JsonParser.parseString(resp.body()).getAsJsonObject();
        Manifest m = new Manifest();
        m.version = o.get("version").getAsString();
        m.jarUrl = o.get("jar_url").getAsString();
        m.mainClass = o.get("main_class").getAsString();
        m.sha256 = o.get("sha256").getAsString();
        return m;
    }

    private static void downloadTo(String url, Path dest) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "NyleLauncherBootstrap/1.0")
                .GET()
                .build();
        HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) {
            throw new IOException("HTTP " + resp.statusCode() + " downloading " + url);
        }
        Path tmp = dest.resolveSibling(dest.getFileName() + ".part");
        try (InputStream in = resp.body()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String sha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static Path mostRecentCached(Path cacheDir) {
        try (Stream<Path> s = Files.list(cacheDir)) {
            return s
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith("launcher-") && n.endsWith(".jar");
                    })
                    .max(Comparator.comparingLong(p -> {
                        try { return Files.getLastModifiedTime(p).toMillis(); }
                        catch (Exception e) { return 0L; }
                    }))
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** Per-OS cache dir. Cross-platform conventions, no admin needed. */
    private static Path cacheDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appdata = System.getenv("LOCALAPPDATA");
            if (appdata != null && !appdata.isEmpty()) {
                return Paths.get(appdata, "NyleRP", "payload");
            }
            return Paths.get(home, "AppData", "Local", "NyleRP", "payload");
        }
        if (os.contains("mac")) {
            return Paths.get(home, "Library", "Application Support", "NyleRP", "payload");
        }
        return Paths.get(home, ".nylerp", "payload");
    }

    private static String shortSha(String sha) {
        return sha == null || sha.length() < 12 ? String.valueOf(sha) : sha.substring(0, 12);
    }

    private static void log(String msg) {
        System.err.println("[bootstrap] " + msg);
    }

    /** Cross-platform crash log directory — must match {@code CrashReporter.resolveCrashDir} in
     *  the payload so the payload picks up bootstrap-level crashes too. */
    private static Path crashDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("mac")) return Paths.get(home, "Library", "Logs", "NyleLauncher");
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            return Paths.get(appdata != null ? appdata : home, "NyleLauncher", "logs");
        }
        return Paths.get(home, ".config", "NyleLauncher", "logs");
    }

    private static void writeCrash(Thread t, Throwable e) {
        try {
            Path dir = crashDir();
            Files.createDirectories(dir);
            String stamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path file = dir.resolve("crash-bootstrap-" + stamp + ".log");
            StringBuilder sb = new StringBuilder();
            sb.append("# NyleLauncher BOOTSTRAP crash report\n");
            sb.append("ts=").append(java.time.LocalDateTime.now()).append('\n');
            sb.append("os=").append(System.getProperty("os.name"))
              .append(' ').append(System.getProperty("os.version"))
              .append(' ').append(System.getProperty("os.arch")).append('\n');
            sb.append("java=").append(System.getProperty("java.version")).append('\n');
            sb.append("thread=").append(t.getName()).append('\n');
            sb.append("---\n");
            try (java.io.StringWriter sw = new java.io.StringWriter();
                 java.io.PrintWriter pw = new java.io.PrintWriter(sw)) {
                e.printStackTrace(pw);
                sb.append(sw);
            }
            Files.writeString(file, sb.toString(), java.nio.file.StandardOpenOption.CREATE_NEW);
            log("crash captured at " + file);
        } catch (Throwable t2) {
            // last resort: stderr
            System.err.println("[bootstrap] meta-failure writing crash: " + t2);
            e.printStackTrace();
        }
    }

    private static class Manifest {
        String version;
        String jarUrl;
        String mainClass;
        String sha256;
    }

    private Bootstrap() {}
}
