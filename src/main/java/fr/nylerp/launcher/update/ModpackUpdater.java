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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        Path modsDir = gameDir.resolve("mods");

        // Filenames of mods/ entries in the current manifest (the canonical
        // versions we must end up with exactly once each).
        Set<String> modsManifestNames = new HashSet<>();
        for (ManifestEntry e : remote.files) {
            String pth = e.path.replace('\\', '/');
            if (pth.startsWith("mods/")) modsManifestNames.add(pth.substring(pth.lastIndexOf('/') + 1));
        }

        // One-time startup dedup: if a previous (record-gated) cleanup left an
        // OLD version of a managed mod next to the new one, purge the stale
        // duplicate now, keeping the manifest-listed version. Robust against a
        // stale/empty .nyle_managed_mods record and asset-name sanitization.
        dedupExistingMods(modsDir, modsManifestNames);

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
            // For a mod jar, FIRST delete any existing jar that is the SAME mod
            // under a different filename (same stable prefix, different version).
            // This guarantees exactly ONE version of each managed mod, regardless
            // of the .nyle_managed_mods record or GitHub asset-name sanitization,
            // BEFORE the new jar lands next to the stale one.
            String pth = e.path.replace('\\', '/');
            if (pth.startsWith("mods/")) {
                removeOtherVersions(modsDir, local.getFileName().toString());
            }
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
        // Three exclusion lists protect files we must keep:
        //   1. Optional-mod jar filenames toggled by the user via Settings
        //      (Bobby, Litematica, …). They live in mods/ but are NOT in the
        //      manifest, so the naive "delete everything not in manifest"
        //      sweep would wipe them on every sync.
        //   2. config/ subtrees that hold user-tweaked per-mod settings.
        //      We deliberately don't sync user-touchable mod configs through
        //      the manifest (handled by firstInstallOnly above), so cleanup
        //      doesn't even walk config/.
        //   3. shaderpacks/ — REMOVED from the cleanup loop on 2026-05-17
        //      because the modpack never ships shader packs ourselves: every
        //      .zip in there is user-installed. The previous behaviour wiped
        //      them on every sync; user reported losing both the pack and
        //      effectively their settings (which still lived in config/iris/
        //      but were useless without the pack file). Shaderpacks now live
        //      under the user's exclusive control.
        //   4. resourcepacks/ — REMOVED from the cleanup loop on 2026-06-21.
        //      The modpack's own NYLERP-PACK.zip is still synced via the
        //      manifest (so it's installed/updated), but any OTHER .zip in
        //      resourcepacks/ is user-installed; the previous sweep wiped
        //      user-added texture packs on every sync. Resource packs now
        //      live under the user's exclusive control, like shaderpacks.
        Set<Path> expected = new HashSet<>();
        for (ManifestEntry e : remote.files) expected.add(gameDir.resolve(e.path).normalize());
        Set<String> optionalModNames = new HashSet<>(OptionalMods.filenames());

        // ── Les mods AJOUTÉS PAR LE JOUEUR sont SACRÉS (jamais supprimés) ───
        //   5. mods/ — on ne supprime QUE les mods que CE LAUNCHER a installés :
        //      un jar présent dans un manifeste PRÉCÉDENT (enregistré dans
        //      .nyle_managed_mods). Un jar que le joueur a déposé lui-même dans
        //      mods/ n'est jamais dans ce record → il survit à chaque sync. Un
        //      mod managé n'est supprimé que quand il quitte le manifeste
        //      (ex. un bump de pack qui retire l'ancien fichier).
        Path managedRecord = gameDir.resolve(".nyle_managed_mods");
        Set<String> newManaged = new HashSet<>();
        for (ManifestEntry e : remote.files) {
            String pth = e.path.replace('\\', '/');
            if (pth.startsWith("mods/")) newManaged.add(pth.substring(pth.lastIndexOf('/') + 1));
        }
        Set<String> prevTmp = readManaged(managedRecord);
        // Premier lancement sur ce build (pas de record) : on adopte le manifeste
        // courant comme set managé, pour ne JAMAIS effacer un jar perso préexistant
        // lors de la mise à jour vers cette version du launcher.
        final Set<String> prevManaged = prevTmp.isEmpty() ? new HashSet<>(newManaged) : prevTmp;

        for (String sub : new String[]{"mods"}) {
            Path dir = gameDir.resolve(sub);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> prevManaged.contains(p.getFileName().toString())) // launcher-managé uniquement — jars perso intouchés
                    .filter(p -> !expected.contains(p.normalize()))
                    .filter(p -> !optionalModNames.contains(p.getFileName().toString()))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                            LOG.info("Removed stale managed mod: {}", gameDir.relativize(p));
                        } catch (Exception ex) {
                            LOG.warn("Could not delete {}: {}", p, ex.toString());
                        }
                    });
            }
        }
        writeManaged(managedRecord, newManaged);

        status("Modpack à jour (" + total + " fichiers)");
    }

    private void status(String line) {
        LOG.info(line);
        if (listener != null) listener.onStatus(line);
    }

    /**
     * Matches a versioned jar name: {@code <name>-<version>.jar} where the version
     * starts with a digit. Group 1 captures the stable mod prefix INCLUDING the
     * trailing dash (e.g. {@code nylecontent-} from {@code nylecontent-1.5.91.jar},
     * {@code fabric-api-} from {@code fabric-api-0.116.12+1.21.1.jar}). The version
     * token may contain digits, dots, {@code +}, {@code _} and further dashes
     * (so multi-segment loader versions like {@code 1.21.1-1.3.3} stay in the
     * version part, not the prefix).
     */
    private static final Pattern VERSIONED_JAR =
            Pattern.compile("^(.*?-)\\d[0-9A-Za-z.+_-]*\\.jar$", Pattern.CASE_INSENSITIVE);

    /**
     * Derives the stable mod prefix from a jar filename by stripping the trailing
     * {@code -<version>.jar}. Returns {@code null} when the name has no recognisable
     * version segment (so non-versioned jars are never grouped/deleted by prefix).
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code nylecontent-1.5.91.jar} → {@code nylecontent-}</li>
     *   <li>{@code nyleessentials-2.16.84.jar} → {@code nyleessentials-}</li>
     *   <li>{@code fabric-api-0.116.12+1.21.1.jar} → {@code fabric-api-}</li>
     *   <li>{@code spark.jar} → {@code null}</li>
     * </ul>
     */
    static String modPrefix(String jarName) {
        if (jarName == null) return null;
        Matcher m = VERSIONED_JAR.matcher(jarName);
        return m.matches() ? m.group(1) : null;
    }

    /**
     * Before copying a freshly-downloaded manifest jar into {@code mods/}, remove
     * every OTHER local jar that is the SAME mod under a different filename
     * (i.e. shares the same {@link #modPrefix} but has a different version). This
     * guarantees exactly ONE version of each managed mod regardless of the state
     * of the {@code .nyle_managed_mods} record or any GitHub asset-name
     * sanitization that could have desynced it. The target file itself is never
     * deleted. Non-versioned jars (no prefix) are left untouched.
     */
    private static void removeOtherVersions(Path modsDir, String keepFileName) {
        String prefix = modPrefix(keepFileName);
        if (prefix == null || !Files.isDirectory(modsDir)) return;
        try (Stream<Path> list = Files.list(modsDir)) {
            list.filter(Files::isRegularFile)
                .filter(p -> {
                    String n = p.getFileName().toString();
                    return n.endsWith(".jar")
                            && !n.equals(keepFileName)
                            && prefix.equals(modPrefix(n));
                })
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                        LOG.info("Removed duplicate version of {}: {}", prefix, p.getFileName());
                    } catch (Exception ex) {
                        LOG.warn("Could not delete duplicate {}: {}", p, ex.toString());
                    }
                });
        } catch (Exception ex) {
            LOG.warn("removeOtherVersions failed for {}: {}", keepFileName, ex.toString());
        }
    }

    /**
     * One-time dedup pass run at the START of every sync: for any mod that has
     * MULTIPLE versioned jars in {@code mods/}, keep the one whose filename the
     * current manifest lists and delete the rest. If the manifest lists none of
     * them (e.g. a mod dropped from the pack — handled by the normal cleanup
     * below), this pass leaves them alone. Only acts on prefixes that actually
     * have a duplicate, so single-version mods and user-added jars are untouched.
     *
     * @param modsManifestNames filenames of {@code mods/} entries in the current manifest
     */
    private void dedupExistingMods(Path modsDir, Set<String> modsManifestNames) {
        if (!Files.isDirectory(modsDir)) return;
        // Group local versioned jars by stable prefix.
        Map<String, List<Path>> byPrefix = new HashMap<>();
        try (Stream<Path> list = Files.list(modsDir)) {
            list.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".jar"))
                .forEach(p -> {
                    String pre = modPrefix(p.getFileName().toString());
                    if (pre != null) byPrefix.computeIfAbsent(pre, k -> new ArrayList<>()).add(p);
                });
        } catch (Exception ex) {
            LOG.warn("dedupExistingMods scan failed: {}", ex.toString());
            return;
        }
        for (Map.Entry<String, List<Path>> en : byPrefix.entrySet()) {
            List<Path> jars = en.getValue();
            if (jars.size() < 2) continue; // no duplicate for this mod
            // Which of these does the manifest list as the canonical version?
            Path keep = jars.stream()
                    .filter(p -> modsManifestNames.contains(p.getFileName().toString()))
                    .findFirst().orElse(null);
            if (keep == null) continue; // none manifest-listed → leave to normal cleanup/user control
            for (Path p : jars) {
                if (p.equals(keep)) continue;
                try {
                    Files.deleteIfExists(p);
                    LOG.info("Dedup: removed stale duplicate {} (kept {})",
                            p.getFileName(), keep.getFileName());
                } catch (Exception ex) {
                    LOG.warn("Dedup could not delete {}: {}", p, ex.toString());
                }
            }
        }
    }

    /** Lit le set des mods managés par le launcher ({@code .nyle_managed_mods}). Vide si absent. */
    private static Set<String> readManaged(Path record) {
        Set<String> s = new HashSet<>();
        try {
            if (Files.exists(record)) {
                for (String line : Files.readAllLines(record)) {
                    String t = line.trim();
                    if (!t.isEmpty()) s.add(t);
                }
            }
        } catch (Exception ignored) {}
        return s;
    }

    /** Persiste le set des mods managés (filenames de {@code mods/} du manifeste courant). */
    private static void writeManaged(Path record, Set<String> names) {
        try {
            Files.write(record, new java.util.ArrayList<>(names));
        } catch (Exception e) {
            LOG.warn("Could not write managed-mods record {}: {}", record, e.toString());
        }
    }
}
