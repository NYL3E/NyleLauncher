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

    /** Prevents concurrent sync() calls — double-clicking "Mettre à jour" or clicking
     *  "Mettre à jour" while a Play-triggered sync is already running used to spawn two
     *  threads racing on the same files, which manifested as the download stalling halfway
     *  through then re-fetching the manifest mid-way (per user log:
     *  "↓ config/ferritecore.mixin.properties [...] Téléchargement du manifest… [...]
     *  ↓ config/ferritecore.mixin.properties"). */
    private static final java.util.concurrent.atomic.AtomicBoolean SYNCING =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public void sync() throws IOException {
        if (!SYNCING.compareAndSet(false, true)) {
            LOG.info("sync() ignored — another modpack sync is already running");
            status("Synchronisation déjà en cours…");
            return;
        }
        try {
            doSync();
        } finally {
            SYNCING.set(false);
        }
    }

    private void doSync() throws IOException {
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
            // First-install-only entries (options.txt, certain configs) are
            // pulled exactly once. After that, the user's customisations win
            // — even if the publisher pushes a new manifest with a different
            // SHA, we leave the local file untouched so settings like
            // renderDistance, guiScale, keybinds aren't reset on every
            // modpack update.
            if (e.firstInstallOnly && Files.exists(local)) {
                done++;
                bytesDone += Math.max(0, e.size);
                listener.onProgress(done, total, bytesDone, bytesTotal);
                continue;
            }
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

        // Cleanup — remove managed files that disappeared from manifest.
        // Two exclusion lists protect files that the manifest doesn't list
        // but that we must keep:
        //   1. Optional-mod jar filenames toggled by the user via Settings
        //      (Bobby, Litematica, …). They live in mods/ but are NOT in the
        //      manifest, so the naive "delete everything not in manifest"
        //      sweep would wipe them on every sync.
        //   2. config/ subtrees that hold user-tweaked per-mod settings.
        //      We deliberately don't sync user-touchable mod configs through
        //      the manifest (handled by firstInstallOnly above), so cleanup
        //      should leave config/ alone entirely.
        Set<Path> expected = new HashSet<>();
        for (ManifestEntry e : remote.files) expected.add(gameDir.resolve(e.path).normalize());
        Set<String> optionalModNames = new HashSet<>(OptionalMods.filenames());

        for (String sub : new String[]{"mods", "resourcepacks", "shaderpacks"}) {
            Path dir = gameDir.resolve(sub);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> !expected.contains(p.normalize()))
                    .filter(p -> !optionalModNames.contains(p.getFileName().toString()))
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
