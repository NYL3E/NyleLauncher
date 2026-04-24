package fr.nylerp.launcher.update;

import com.google.gson.Gson;
import fr.nylerp.launcher.config.AppPaths;
import fr.nylerp.launcher.config.Constants;
import fr.nylerp.launcher.util.Downloader;
import fr.nylerp.launcher.util.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Reconciles the local game directory with the remote manifest:
 *  1. Downloads the latest manifest.json
 *  2. Compares SHA-256 of each local file to the manifest entry
 *  3. Downloads missing/outdated files
 *  4. Deletes local files under /mods, /config, /resourcepacks that are no longer listed
 *     (so removing a mod on server side removes it client side at next launch)
 */
public final class ModpackUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(ModpackUpdater.class);
    private static final Gson GSON = new Gson();

    public interface Listener {
        void onStatus(String line);
        void onProgress(int done, int total, long bytesDone, long bytesTotal);
    }

    private final Listener listener;

    public ModpackUpdater(Listener listener) {
        this.listener = listener;
    }

    /**
     * Checks whether the remote manifest's version differs from the cached one.
     * Returns true when an update is available (including the first-run case
     * where the local cache doesn't exist yet). Network failures are treated
     * as "no update" so the UI stays usable offline.
     */
    public static boolean hasUpdate() {
        try {
            String remoteJson = Downloader.toString(manifestUrl());
            Manifest remote = GSON.fromJson(remoteJson, Manifest.class);
            if (remote == null || remote.version == null) return false;

            Path cache = AppPaths.manifestCache();
            if (!Files.exists(cache)) return true;
            Manifest local = GSON.fromJson(Files.readString(cache), Manifest.class);
            if (local == null || local.version == null) return true;
            return !remote.version.equals(local.version);
        } catch (Exception e) {
            LOG.warn("Modpack update check failed: {}", e.toString());
            return false;
        }
    }

    /**
     * Manifest URL with a cache-busting query string. GitHub Releases' CDN
     * sometimes serves stale manifests for several minutes after re-upload,
     * leaving the launcher convinced there's "nothing to download". Appending
     * a unique ?t= param forces a fresh fetch each call.
     */
    private static String manifestUrl() {
        return Constants.MANIFEST_URL + "?t=" + System.currentTimeMillis();
    }

    public void sync() throws IOException {
        status("Téléchargement du manifest…");
        String json = Downloader.toString(manifestUrl());
        Manifest remote = GSON.fromJson(json, Manifest.class);
        if (remote == null || remote.files == null) {
            throw new IOException("Manifest invalide ou vide");
        }

        // Cache it locally so we know what was installed
        Files.writeString(AppPaths.manifestCache(), json);

        Path gameDir = AppPaths.gameDir();
        int total = remote.files.size();
        int done = 0;
        long bytesTotal = remote.files.stream().mapToLong(f -> Math.max(0, f.size)).sum();
        long bytesDone = 0;

        for (ManifestEntry e : remote.files) {
            Path local = gameDir.resolve(e.path);
            String localHash = Hashing.sha256(local);
            if (e.sha256 != null && e.sha256.equalsIgnoreCase(localHash)) {
                done++;
                bytesDone += Math.max(0, e.size);
                listener.onProgress(done, total, bytesDone, bytesTotal);
                continue;
            }
            status("↓ " + e.path);
            Downloader.toFile(e.url, local, null);
            String after = Hashing.sha256(local);
            if (e.sha256 != null && !e.sha256.equalsIgnoreCase(after)) {
                throw new IOException("Checksum KO pour " + e.path);
            }
            done++;
            bytesDone += Math.max(0, e.size);
            listener.onProgress(done, total, bytesDone, bytesTotal);
        }

        // Cleanup — remove managed files that disappeared from manifest
        Set<Path> expected = new HashSet<>();
        for (ManifestEntry e : remote.files) expected.add(gameDir.resolve(e.path).normalize());

        for (String sub : new String[]{"mods", "config", "resourcepacks", "shaderpacks"}) {
            Path dir = gameDir.resolve(sub);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> !expected.contains(p.normalize()))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                            LOG.info("Removed stale file: {}", gameDir.relativize(p));
                        } catch (Exception ex) {
                            LOG.warn("Could not delete {}: {}", p, ex.toString());
                        }
                    });
            }
        }

        status("Modpack à jour (" + total + " fichiers)");
    }

    private void status(String line) {
        LOG.info(line);
        if (listener != null) listener.onStatus(line);
    }
}
