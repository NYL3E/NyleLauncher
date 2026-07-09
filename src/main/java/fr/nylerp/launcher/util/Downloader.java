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

    // GitHub Releases répond parfois 5xx/429 de façon TRANSITOIRE (incident 2026-07-09 :
    // un seul HTTP 504 sur un fichier de config a fait échouer toute la synchro et affiché
    // « Mise à jour échouée » à des dizaines de joueurs alors que l'asset était sain).
    // Toute erreur transitoire (5xx, 429, IOException réseau) est donc retentée avec
    // backoff avant d'abandonner. Le 404 garde UNE relance cache-bustée (cache CDN vérolé),
    // pas plus : un vrai fichier manquant doit remonter vite, pas boucler.
    private static final int TRANSIENT_RETRIES = 3;
    private static final long[] BACKOFF_MS = {1_000, 3_000, 6_000};

    public interface Progress {
        void onBytes(long downloaded, long total);
    }

    private static boolean isTransient(int status) {
        return status == 429 || (status >= 500 && status <= 599);
    }

    /**
     * GET avec politique de retry : jusqu'à {@link #TRANSIENT_RETRIES} relances sur
     * 5xx/429/erreur réseau (backoff progressif + cache-buster pour contourner un edge
     * CDN malade), une seule relance cache-bustée sur 404. Retourne la réponse 200.
     */
    private static <T> HttpResponse<T> getWithRetry(String url, Duration timeout,
                                                    HttpResponse.BodyHandler<T> handler) throws IOException {
        IOException lastNetworkError = null;
        int status = -1;
        boolean bustedFor404 = false;
        String attemptUrl = url;
        for (int attempt = 0; attempt <= TRANSIENT_RETRIES; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(BACKOFF_MS[Math.min(attempt - 1, BACKOFF_MS.length - 1)]);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Download interrupted", e);
                }
                // Cache-buster : un edge CDN qui a mis en cache une erreur ne doit pas la resservir
                attemptUrl = url + (url.contains("?") ? "&" : "?") + "cb=" + System.nanoTime();
            }
            try {
                HttpResponse<T> resp = send(attemptUrl, timeout, handler);
                status = resp.statusCode();
                if (status == 200) return resp;
                if (status == 404) {
                    if (bustedFor404 || url.contains("?")) break; // vrai manquant : échouer vite
                    bustedFor404 = true;
                    continue;                                     // une relance cache-bustée
                }
                if (!isTransient(status)) break;                  // 4xx autre : inutile d'insister
                lastNetworkError = null;
            } catch (IOException e) {
                lastNetworkError = e;                             // coupure réseau : retenter aussi
            }
        }
        if (lastNetworkError != null) throw lastNetworkError;
        throw new IOException("HTTP " + status + " on " + url);
    }

    private static <T> HttpResponse<T> send(String url, Duration timeout,
                                            HttpResponse.BodyHandler<T> handler) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", "NyleLauncher/0.1.0")
                .header("Accept", "*/*")
                .GET()
                .build();
        try {
            return HTTP.send(req, handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
    }

    public static void toFile(String url, Path dest, Progress progress) throws IOException {
        Files.createDirectories(dest.getParent());
        Path tmp = dest.resolveSibling(dest.getFileName() + ".part");

        HttpResponse<InputStream> resp = getWithRetry(url, Duration.ofMinutes(5),
                HttpResponse.BodyHandlers.ofInputStream());
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

    public static String toString(String url) throws IOException {
        return getWithRetry(url, Duration.ofSeconds(30), HttpResponse.BodyHandlers.ofString()).body();
    }

    private Downloader() {}
}
