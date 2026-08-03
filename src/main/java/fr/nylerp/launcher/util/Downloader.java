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
        // Deadline murale sur le CORPS : HttpRequest.timeout ne borne que les en-têtes — un CDN
        // qui « goutte » (incident GitHub 2026-07-10/11) laissait ce read() tourner sans fin, le
        // verrou SYNCING restait pris et chaque clic « Jouer » répondait « Synchronisation déjà en
        // cours… » à l'infini. 10 min par fichier = large pour le plus gros jar du pack, et l'échec
        // retombe sur la boucle de retry/dialog existante au lieu de bloquer le launcher.
        long deadline = System.currentTimeMillis() + 10 * 60_000L;
        try (InputStream in = resp.body()) {
            try (var out = Files.newOutputStream(tmp)) {
                byte[] buf = new byte[32 * 1024];
                long done = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    if (progress != null) progress.onBytes(done, total);
                    if (System.currentTimeMillis() > deadline) {
                        throw new IOException("Téléchargement trop lent (>10 min) : " + url);
                    }
                }
            }
        }
        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public static String toString(String url) throws IOException {
        return getWithRetry(url, Duration.ofSeconds(30), HttpResponse.BodyHandlers.ofString()).body();
    }

    /**
     * Récupère une petite ressource texte (le manifest) avec une DEADLINE murale de 12 s qui couvre
     * TOUT l'échange, corps compris, et SANS la boucle de retry transitoire. Pourquoi pas un simple
     * {@code HttpRequest.timeout} : il ne borne que l'attente des en-têtes — pendant l'incident CDN
     * GitHub Releases du 2026-07-10/11 (corps servi à ~2,8 Ko/s), les en-têtes arrivaient vite puis
     * le corps « gouttait » pendant des minutes : aucun timeout ne partait et le launcher restait
     * bloqué à l'infini sur « Téléchargement du manifest… ». Le futur {@code sendAsync().get(12 s)}
     * borne l'échange complet ; l'appelant retombe alors sur le manifest en cache. Un CDN sain sert
     * ces ~350 Ko en < 1 s ; 12 s est large.
     */
    /**
     * Récupération PATIENTE d'un document, avec réessais et repli exponentiel.
     *
     * <h2>Pourquoi elle existe (bug du 2026-08-03)</h2>
     * {@link #toStringQuick} abandonne au bout de <b>12 secondes</b>. C'était suffisant quand le
     * manifeste du pack pesait quelques dizaines de kilo-octets ; il en fait aujourd'hui
     * <b>310 Ko</b>. Or le commentaire de {@code ModpackUpdater} documente lui-même des incidents
     * de CDN GitHub à <b>~2,8 Ko/s</b> : à ce débit, 310 Ko demandent <b>110 secondes</b>. Le
     * délai était donc dépassé <i>systématiquement</i>, l'exception remontait, et l'appelant
     * concluait « pas de mise à jour ».
     *
     * <p>Effet observé : le launcher est resté bloqué sur le manifeste {@code prod-session2-56103}
     * pendant six versions de pack, en lançant un client périmé <b>sans jamais rien afficher</b>.
     * Le joueur voyait un jeu à jour ; il ne l'était pas.
     *
     * <p>La leçon vaut au-delà de ce cas : <b>un délai d'attente est un budget, et un budget se
     * calcule sur la TAILLE de ce qu'on transfère</b>, jamais sur une constante posée une fois.
     * Un fichier qui grossit finit toujours par le dépasser, et il le dépasse en silence.
     *
     * @param url      l'adresse
     * @param seconds  budget de temps pour UNE tentative
     * @param attempts nombre de tentatives (repli 1 s, 2 s, 4 s… entre chacune)
     */
    public static String toStringPatient(String url, int seconds, int attempts) throws IOException {
        IOException last = null;
        for (int i = 0; i < Math.max(1, attempts); i++) {
            if (i > 0) {
                try { Thread.sleep(1000L << (i - 1)); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
            try {
                return toStringWithin(url, seconds);
            } catch (IOException e) {
                last = e;
            }
        }
        throw last != null ? last : new IOException("échec de récupération : " + url);
    }

    /** Une tentative, avec un budget explicite. Cœur commun de {@code Quick} et {@code Patient}. */
    private static String toStringWithin(String url, int seconds) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(seconds))
                .header("User-Agent", "NyleLauncher/0.1.0")
                .header("Accept", "*/*")
                .header("Accept-Encoding", "gzip")   // 310 Ko de JSON se compriment massivement
                .GET()
                .build();
        java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> future =
                HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray());
        try {
            HttpResponse<byte[]> resp = future.get(seconds, java.util.concurrent.TimeUnit.SECONDS);
            if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode() + " on " + url);
            byte[] body = resp.body();
            boolean gz = resp.headers().firstValue("content-encoding")
                    .map(v -> v.toLowerCase().contains("gzip")).orElse(false);
            if (gz) {
                try (var in = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(body))) {
                    body = in.readAllBytes();
                }
            }
            return new String(body, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw new IOException("Serveur de mise à jour trop lent (>" + seconds + " s) — CDN dégradé");
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable c = e.getCause();
            throw c instanceof IOException io ? io : new IOException(String.valueOf(c));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrompu");
        }
    }

    public static String toStringQuick(String url) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "NyleLauncher/0.1.0")
                .header("Accept", "*/*")
                .GET()
                .build();
        java.util.concurrent.CompletableFuture<HttpResponse<String>> future =
                HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString());
        try {
            HttpResponse<String> resp = future.get(12, java.util.concurrent.TimeUnit.SECONDS);
            if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode() + " on " + url);
            return resp.body();
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw new IOException("Serveur de mise à jour trop lent (>12 s) — CDN dégradé");
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable c = e.getCause();
            throw c instanceof IOException io ? io : new IOException(String.valueOf(c));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
    }

    private Downloader() {}
}
