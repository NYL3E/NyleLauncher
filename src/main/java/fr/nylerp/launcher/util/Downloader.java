package fr.nylerp.launcher.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

public final class Downloader {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    public interface Progress {
        void onBytes(long downloaded, long total);
    }

    public static void toFile(String url, Path dest, Progress progress) throws IOException {
        Files.createDirectories(dest.getParent());
        Path tmp = dest.resolveSibling(dest.getFileName() + ".part");

        // First attempt
        HttpResponse<InputStream> resp = doGet(url);
        // GitHub Releases occasionally caches a 404 on download URLs. Cache-bust + retry once.
        if (resp.statusCode() == 404 && !url.contains("?")) {
            String bust = url + "?cb=" + System.currentTimeMillis();
            resp = doGet(bust);
        }
        if (resp.statusCode() != 200) {
            throw new IOException("HTTP " + resp.statusCode() + " on " + url);
        }
            long total = resp.headers().firstValueAsLong("content-length").orElse(-1);
            try (InputStream in = resp.body()) {
                try (var out = Files.newOutputStream(tmp)) {
                    byte[] buf = new byte[32 * 1024];
                    long done = 0;
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        done += n;
                        if (progress != null) progress.onBytes(done, total);
                    }
                }
            }
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static HttpResponse<InputStream> doGet(String url) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", "NyleLauncher/0.1.0")
                .header("Accept", "*/*")
                .GET()
                .build();
        try {
            return HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
    }

    public static String toString(String url) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "NyleLauncher/0.1.0")
                .header("Accept", "*/*")
                .GET()
                .build();
        try {
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IOException("HTTP " + resp.statusCode() + " on " + url);
            }
            return resp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
    }

    private Downloader() {}
}
