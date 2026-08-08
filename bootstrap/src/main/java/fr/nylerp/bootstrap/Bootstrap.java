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
 * No UI, no business logic, no business URLs hardcoded except the two manifest
 * URLs of {@link #manifestUrlFor(String)}.
 * That way the actual launcher (UI, auth, modpack updater, every button & label)
 * lives in the payload and can be replaced 100% remotely without forcing users
 * to reinstall anything.
 *
 * <p><b>Deux canaux, un seul socle</b> (2026-08-08). Le socle est désormais construit deux fois :
 * en canal <b>prod</b> (défaut) et en canal <b>dev</b>. La seule différence est la PAIRE
 * {manifeste, dossier de cache} — tout le reste du code est partagé, ce qui garantit que le
 * canal DEV éprouve exactement le même mécanisme de mise à jour que la production.
 *
 * <p><b>Pourquoi le canal DEV avait besoin de ça.</b> Le paquet DEV était auparavant un
 * {@code jpackage} du PAYLOAD lui-même : aucun socle, donc aucun moyen d'aller chercher une
 * nouvelle version. Les testeurs devaient réinstaller le MSI/DMG/DEB à chaque itération. En
 * repassant par le socle, une nouvelle version de payload DEV est prise au démarrage suivant,
 * sans réinstallation, sans droits d'administrateur et sans toucher au paquet installé (donc
 * sans jamais invalider sa signature macOS).
 *
 * <p><b>Pourquoi des dossiers de cache séparés.</b> Si les deux canaux partageaient
 * {@code NyleRP/payload}, le repli « pas d'internet → dernier payload en cache » d'un socle de
 * PRODUCTION pourrait démarrer un payload DEV — c'est-à-dire envoyer un joueur de prod sur le
 * serveur de développement. Le canal DEV écrit donc dans {@code NyleRP/payload-dev}.
 */
public final class Bootstrap {

    /**
     * Bootstrap version, read at runtime from the {@code bootstrap-version.properties}
     * resource that gradle's processResources writes at build time (see bootstrap/build.gradle).
     * Hard-coding here was a footgun — bumping {@code bootstrap/build.gradle} alone left this
     * constant stale and the payload's "obsolete bootstrap" check fired even on freshly-installed
     * users. Falls back to "0.0.0-dev" only when running from gradle/IDE without the resource.
     *
     * Exposed to the payload at runtime via the {@code nyleauth.installedVersion} system
     * property — that's how SelfUpdater knows what's actually installed on disk.
     */
    public static final String VERSION = readBootstrapVersion();

    private static String readBootstrapVersion() {
        try (InputStream in = Bootstrap.class.getResourceAsStream("/bootstrap-version.properties")) {
            if (in == null) return "0.0.0-dev";
            java.util.Properties p = new java.util.Properties();
            p.load(in);
            String v = p.getProperty("version", "").trim();
            return v.isEmpty() ? "0.0.0-dev" : v;
        } catch (Exception e) {
            return "0.0.0-dev";
        }
    }

    /**
     * Canal du socle : {@code "prod"} (défaut) ou {@code "dev"}, lu au démarrage depuis la
     * ressource {@code bootstrap-channel.properties} écrite par {@code processResources} selon
     * {@code -Pchannel} (voir bootstrap/build.gradle).
     *
     * <p>Repli en {@code "prod"} au moindre doute (ressource absente, illisible, valeur inconnue) :
     * un socle qui ne sait pas qui il est doit se comporter comme celui de production.
     */
    static final String CHANNEL = readChannel();

    private static String readChannel() {
        try (InputStream in = Bootstrap.class.getResourceAsStream("/bootstrap-channel.properties")) {
            if (in == null) return "prod";
            java.util.Properties p = new java.util.Properties();
            p.load(in);
            return normalizeChannel(p.getProperty("channel"));
        } catch (Exception e) {
            return "prod";
        }
    }

    /** Le seul canal reconnu autre que la production est {@code dev} ; tout le reste retombe en prod. */
    static String normalizeChannel(String raw) {
        if (raw == null) return "prod";
        return "dev".equals(raw.trim().toLowerCase(java.util.Locale.ROOT)) ? "dev" : "prod";
    }

    /**
     * Manifeste de PRODUCTION. Valeur historique, inchangée : c'est le contrat des socles déjà
     * installés chez les joueurs. Ne jamais la modifier sans migrer le site.
     */
    static final String PROD_MANIFEST_URL =
            "https://nyle-mc-server.pages.dev/launcher/manifest.json";

    /**
     * Manifeste du canal DEV — un actif de release GitHub sur un tag STABLE ({@code dev-payload}),
     * pas une page du site. Deux raisons : publier un payload DEV ne doit dépendre que de la CI du
     * dépôt (aucun déploiement Cloudflare à faire à la main), et le dépôt utilise déjà exactement ce
     * schéma pour le modpack ({@code pack-dev} / {@code pack-latest}).
     */
    static final String DEV_MANIFEST_URL =
            "https://github.com/NYL3E/NyleLauncher/releases/download/dev-payload/manifest.json";

    /**
     * Manifeste effectif du canal donné.
     *
     * <p>La propriété système {@code nylerp.devManifestUrl} permet de détourner le socle vers un
     * manifeste local — c'est ce qui rend le chemin de mise à jour ÉPROUVABLE hors CI. Elle n'est
     * lue qu'en canal DEV : un socle de production ne peut être redirigé par aucune propriété ni
     * variable d'environnement, son URL reste littérale.
     */
    static String manifestUrlFor(String channel) {
        if (!"dev".equals(channel)) return PROD_MANIFEST_URL;
        String override = System.getProperty("nylerp.devManifestUrl", "").trim();
        return override.isEmpty() ? DEV_MANIFEST_URL : override;
    }

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
        String manifestUrl = manifestUrlFor(CHANNEL);
        log("bootstrap version=" + VERSION + " canal=" + CHANNEL);
        log("manifeste=" + manifestUrl);

        Path cache = cacheDirFor(CHANNEL);
        Files.createDirectories(cache);
        log("cache=" + cache);

        Manifest manifest;
        try {
            manifest = fetchManifest(manifestUrl);
            log("manifest version=" + manifest.version + " sha=" + shortSha(manifest.sha256));
        } catch (Exception e) {
            log("WARN cannot fetch manifest (" + e.getMessage() + ") — falling back to cache");
            Path cached = mostRecentCached(cache);
            if (cached == null) {
                throw new RuntimeException("No internet AND no cached payload available", e);
            }
            System.setProperty("nyleauth.payloadVersion", versionOfCached(cached));
            launch(cached, args, "fr.nylerp.launcher.Main");
            return;
        }

        // Version RÉELLEMENT chargée, exposée au payload. Le payload embarque bien une constante
        // PAYLOAD_VERSION, mais elle est figée à la compilation et avait déjà dérivé sans que
        // personne ne le voie (1.0.84 affiché pour un payload-1.0.87 publié). La seule source de
        // vérité est le manifeste que le socle vient d'appliquer.
        System.setProperty("nyleauth.payloadVersion", manifest.version);

        Path target = cache.resolve("launcher-" + manifest.version + ".jar");
        boolean needDownload = !Files.exists(target)
                || !sha256(target).equalsIgnoreCase(manifest.sha256);
        if (needDownload) {
            log("downloading payload " + manifest.version + " ← " + manifest.jarUrl);
            // ── Téléchargement RÉSILIENT (2026-07-07) ────────────────────────────────────────────
            // Un joueur a crash-loopé sur « SHA-256 mismatch » : le jar publié était sain (vérifié),
            // c'est SON téléchargement qui arrivait corrompu (tronquage réseau, antivirus/proxy qui
            // modifie le flux). L'ancien code : une seule tentative → delete → throw → launcher mort.
            // Désormais : 3 tentatives (cache-buster pour contourner un cache intermédiaire vérolé),
            // et si tout échoue on démarre sur le DERNIER payload en cache (une version d'avant qui
            // marche vaut infiniment mieux qu'un crash) — le prochain démarrage retentera la MAJ.
            boolean ok = false;
            for (int attempt = 1; attempt <= 3 && !ok; attempt++) {
                try {
                    String url = manifest.jarUrl
                            + (manifest.jarUrl.contains("?") ? "&" : "?") + "r=" + attempt + "." + System.nanoTime();
                    downloadTo(url, target);
                    String got = sha256(target);
                    if (got.equalsIgnoreCase(manifest.sha256)) { ok = true; break; }
                    log("WARN attempt " + attempt + "/3: SHA-256 mismatch (expected "
                            + shortSha(manifest.sha256) + " got " + shortSha(got) + ") — retrying");
                    Files.deleteIfExists(target);
                } catch (Exception dlErr) {
                    log("WARN attempt " + attempt + "/3 failed: " + dlErr.getMessage());
                    try { Files.deleteIfExists(target); } catch (Exception ignored) { }
                }
                if (!ok && attempt < 3) Thread.sleep(1500L * attempt);
            }
            if (!ok) {
                Path cached = mostRecentCached(cache);
                if (cached != null) {
                    log("WARN download corrupted 3x — starting on cached payload " + cached.getFileName()
                            + " (will retry the update next start)");
                    System.setProperty("nyleauth.payloadVersion", versionOfCached(cached));
                    launch(cached, args, "fr.nylerp.launcher.Main");
                    return;
                }
                throw new RuntimeException("Payload download corrupted 3 times AND no cached payload — "
                        + "un antivirus/proxy modifie probablement les téléchargements ; réessaie ou "
                        + "désactive l'inspection HTTPS de l'antivirus");
            }
            log("downloaded + verified");
        } else {
            log("payload " + manifest.version + " already cached, hash matches");
        }

        if ("dev".equals(CHANNEL)) pruneDevCache(cache, target);

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

    private static Manifest fetchManifest(String manifestUrl) throws IOException, InterruptedException {
        // Cache-buster so CDN never serves a stale manifest.
        String url = manifestUrl + (manifestUrl.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "NyleLauncherBootstrap/1.0")
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("HTTP " + resp.statusCode() + " on " + manifestUrl);
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
        long expected = resp.headers().firstValueAsLong("Content-Length").orElse(-1L);
        Path tmp = dest.resolveSibling(dest.getFileName() + ".part");
        long copied = 0;
        // Deadline murale sur le CORPS : HttpRequest.timeout ne borne que l'attente des en-têtes.
        // Pendant l'incident CDN GitHub du 2026-07-10/11 le corps « gouttait » à ~3 Ko/s : sans
        // cette borne, chaque tentative pouvait pendre indéfiniment au lieu d'échouer et de
        // laisser la main au fallback « payload en cache ».
        long deadline = System.currentTimeMillis() + 10 * 60_000L;
        try (InputStream in = resp.body();
             var out = Files.newOutputStream(tmp, java.nio.file.StandardOpenOption.CREATE,
                     java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                copied += n;
                if (System.currentTimeMillis() > deadline) {
                    throw new IOException("Payload download exceeded 10 min (CDN trickling) — aborting to fall back");
                }
            }
        }
        // Un flux coupé en route passe silencieusement dans Files.copy — on compare au Content-Length
        // déclaré pour échouer TÔT (et déclencher le retry) plutôt que sur le hash final.
        if (expected >= 0 && copied != expected) {
            Files.deleteIfExists(tmp);
            throw new IOException("Truncated download: " + copied + "/" + expected + " bytes");
        }
        // Détection « page d'erreur HTML » (proxy captif, rate-limit) : un jar commence par PK.
        try (InputStream check = Files.newInputStream(tmp)) {
            if (!(check.read() == 'P' && check.read() == 'K')) {
                Files.deleteIfExists(tmp);
                throw new IOException("Downloaded content is not a jar (proxy/AV interference?)");
            }
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

    /**
     * Dossier de cache du payload, par système ET par canal.
     *
     * <p>Conventions par OS, toujours dans l'espace utilisateur : c'est ce qui permet d'écrire la
     * nouvelle charge <b>sans aucun droit d'administrateur</b> sous Windows ({@code %LOCALAPPDATA%},
     * jamais {@code Program Files}) et <b>sans toucher au paquet installé</b> sous macOS (donc sans
     * jamais invalider la signature du {@code .app}).
     *
     * <p>Le canal DEV a son propre dossier ({@code payload-dev}) : sans cette séparation, le repli
     * hors-ligne d'un socle de PRODUCTION ({@link #mostRecentCached}) pourrait démarrer un payload
     * DEV et envoyer un joueur sur le serveur de développement.
     */
    static Path cacheDirFor(String channel) {
        String leaf = "dev".equals(channel) ? "payload-dev" : "payload";
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appdata = System.getenv("LOCALAPPDATA");
            if (appdata != null && !appdata.isEmpty()) {
                return Paths.get(appdata, "NyleRP", leaf);
            }
            return Paths.get(home, "AppData", "Local", "NyleRP", leaf);
        }
        if (os.contains("mac")) {
            return Paths.get(home, "Library", "Application Support", "NyleRP", leaf);
        }
        return Paths.get(home, ".nylerp", leaf);
    }

    /**
     * Nombre de charges conservées dans le cache DEV. Le payload pèse ~76 Mo et le canal DEV publie
     * plusieurs fois par jour : sans purge, le disque d'un testeur se remplit en une semaine. On
     * garde la charge courante plus les {@value #DEV_CACHE_KEEP}−1 plus récentes, ce qui préserve le
     * repli « démarrer sur la version d'avant » de {@link #mostRecentCached}.
     *
     * <p>La purge est <b>volontairement limitée au canal DEV</b> : le cache de production garde le
     * comportement exact qu'il a aujourd'hui.
     */
    private static final int DEV_CACHE_KEEP = 3;

    /** Supprime les charges les plus anciennes, sans jamais toucher à {@code keep}. */
    private static void pruneDevCache(Path cacheDir, Path keep) {
        try (Stream<Path> s = Files.list(cacheDir)) {
            java.util.List<Path> jars = s
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith("launcher-") && n.endsWith(".jar");
                    })
                    .sorted(Comparator.<Path>comparingLong(p -> {
                        try { return Files.getLastModifiedTime(p).toMillis(); }
                        catch (Exception e) { return 0L; }
                    }).reversed())
                    .toList();
            int kept = 0;
            for (Path p : jars) {
                boolean isCurrent = keep != null && p.toAbsolutePath().equals(keep.toAbsolutePath());
                if (isCurrent || kept < DEV_CACHE_KEEP) {
                    if (!isCurrent) kept++;
                    continue;
                }
                Files.deleteIfExists(p);
                log("cache dev: purge " + p.getFileName());
            }
        } catch (Exception e) {
            log("WARN purge du cache dev impossible: " + e.getMessage());
        }
    }

    /** Déduit la version d'une charge en cache depuis son nom {@code launcher-<version>.jar}. */
    private static String versionOfCached(Path jar) {
        String n = jar.getFileName().toString();
        if (n.startsWith("launcher-") && n.endsWith(".jar")) {
            return n.substring("launcher-".length(), n.length() - ".jar".length());
        }
        return "inconnue";
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
