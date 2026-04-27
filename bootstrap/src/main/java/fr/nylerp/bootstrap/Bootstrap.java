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

    /** The only business URL inside the bootstrap. Stable forever. */
    private static final String MANIFEST_URL =
            "https://nyle-mc-server.pages.dev/launcher/manifest.json";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    public static void main(String[] args) throws Exception {
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

    private static class Manifest {
        String version;
        String jarUrl;
        String mainClass;
        String sha256;
    }

    private Bootstrap() {}
}
