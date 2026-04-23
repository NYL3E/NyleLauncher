package fr.nylerp.launcher.launch;

import fr.nylerp.launcher.config.AppPaths;
import fr.nylerp.launcher.util.Downloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.stream.Stream;

/**
 * Ensures a usable Java 21 JRE is available locally. Downloads Eclipse Temurin
 * from adoptium.net on first run and caches under ~/.NyleLauncher/runtime/jdk-21.
 */
public final class JavaRuntime {

    private static final Logger LOG = LoggerFactory.getLogger(JavaRuntime.class);

    public static Path ensure(Downloader.Progress progress) throws IOException {
        Path root = AppPaths.rootDir().resolve("runtime").resolve("jdk-21");
        Path javaBin = javaBinary(root);
        if (javaBin != null && Files.isExecutable(javaBin)) {
            LOG.info("JRE already present: {}", javaBin);
            return javaBin;
        }
        Files.createDirectories(root);

        String os = osSlug();
        String arch = archSlug();
        String ext = os.equals("windows") ? "zip" : "tar.gz";
        String url = "https://api.adoptium.net/v3/binary/latest/21/ga/"
                + os + "/" + arch + "/jdk/hotspot/normal/eclipse";
        Path archive = root.resolve("jdk21." + ext);

        LOG.info("Downloading Temurin 21 for {}/{} → {}", os, arch, archive);
        Downloader.toFile(url, archive, progress);

        LOG.info("Extracting JRE…");
        extract(archive, root, ext);
        Files.deleteIfExists(archive);

        // The archive unpacks to a single top-level dir like "jdk-21.0.10+7". Flatten it.
        flattenSingleChild(root);

        javaBin = javaBinary(root);
        if (javaBin == null || !Files.isExecutable(javaBin)) {
            throw new IOException("JRE extracted but 'java' binary not found under " + root);
        }
        LOG.info("JRE installed at {}", javaBin);
        return javaBin;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static Path javaBinary(Path root) {
        String os = osSlug();
        if ("windows".equals(os)) {
            return root.resolve("bin").resolve("java.exe");
        } else if ("mac".equals(os)) {
            return root.resolve("Contents").resolve("Home").resolve("bin").resolve("java");
        } else {
            return root.resolve("bin").resolve("java");
        }
    }

    private static String osSlug() {
        String n = System.getProperty("os.name").toLowerCase();
        if (n.contains("win")) return "windows";
        if (n.contains("mac") || n.contains("darwin")) return "mac";
        return "linux";
    }

    private static String archSlug() {
        String a = System.getProperty("os.arch").toLowerCase();
        if (a.contains("aarch64") || a.contains("arm64")) return "aarch64";
        if (a.contains("amd64") || a.contains("x86_64")) return "x64";
        return "x64";
    }

    private static void extract(Path archive, Path dest, String ext) throws IOException {
        ProcessBuilder pb;
        if ("zip".equals(ext)) {
            // Use the JVM's own zip capabilities via a command — Windows has 'powershell Expand-Archive'
            pb = new ProcessBuilder("powershell", "-Command",
                    "Expand-Archive -Path \"" + archive + "\" -DestinationPath \"" + dest + "\" -Force");
        } else {
            pb = new ProcessBuilder("tar", "-xzf", archive.toString(), "-C", dest.toString());
        }
        pb.inheritIO();
        try {
            int rc = pb.start().waitFor();
            if (rc != 0) throw new IOException("Extract failed rc=" + rc);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Extract interrupted", ie);
        }
    }

    /**
     * If the archive extracted into a single subfolder (e.g. "jdk-21.0.10+7"),
     * move all of its contents up to {@code root} so our layout stays stable.
     */
    private static void flattenSingleChild(Path root) throws IOException {
        try (Stream<Path> s = Files.list(root)) {
            long dirCount = s.filter(Files::isDirectory).count();
            if (dirCount != 1) return;
        }
        Path child;
        try (Stream<Path> s = Files.list(root)) {
            child = s.filter(Files::isDirectory).findFirst().orElseThrow();
        }
        try (Stream<Path> s = Files.list(child)) {
            s.forEach(p -> {
                try {
                    Files.move(p, root.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        Files.deleteIfExists(child);
    }

    private JavaRuntime() {}
}
