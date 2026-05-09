package fr.nylerp.launcher.update;

import fr.nylerp.launcher.config.AppPaths;
import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.util.Downloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Manages optional client-side mods that the player toggles in
 * {@link fr.nylerp.launcher.ui.SettingsView} but are NOT shipped in the base
 * modpack manifest.
 *
 * <p>For each entry: when the matching settings flag is true and the jar is
 * absent from {@code mods/}, fetch from Modrinth and place it; when the flag
 * is false and the jar is present, delete it. Run once before each launch via
 * {@link #applyAll()}.
 *
 * <p>The base modpack pipeline must NOT list these jars in its manifest —
 * otherwise {@link ModpackUpdater} would re-delete them on cleanup. The
 * filenames here are also threaded through {@link ModpackUpdater#OPTIONAL_MOD_FILENAMES}
 * so cleanup leaves them alone.
 */
public final class OptionalMods {

    private static final Logger LOG = LoggerFactory.getLogger(OptionalMods.class);

    /** A single optional mod definition.
     *  @param fileName  exact filename to use under mods/
     *  @param url       direct CDN url (Modrinth release link)
     *  @param sha512    expected SHA-512 from the Modrinth API (verified after download) */
    public record Entry(String fileName, String url, String sha512, BooleanSupplier enabled) {}

    public static final List<Entry> ENTRIES = List.of(
            new Entry(
                "litematica-fabric-1.21-0.19.60.jar",
                "https://cdn.modrinth.com/data/bEpr0Arc/versions/aEvrmYqW/litematica-fabric-1.21-0.19.60.jar",
                "6808c6987a15c6659ceadd7757dcceefbbe3e45dbf5c017d2a7606b6e7d4dcffdeac3206b42950c2024b3f613d55fdb66d93e831d0ed83d874f5e2508c525218",
                () -> Settings.get().optionalLitematica),
            // Distant Horizons 3.0.3-b — first DH branch with full Iris 1.8.x +
            // Sodium 0.6.x compatibility. The older 2.x branch is what was
            // pulled out of earlier modpack iterations because it shimmered
            // with shaders and dropped frames against Sodium 0.6 — the 3.x
            // rewrite resolves both. ~30 MB; uses extra RAM (~1 GB on top of
            // base allocation), warned about in the SettingsView row.
            new Entry(
                "DistantHorizons-3.0.3-b-1.21.1-fabric-neoforge.jar",
                "https://cdn.modrinth.com/data/uCdwusMi/versions/oYXIfeus/DistantHorizons-3.0.3-b-1.21.1-fabric-neoforge.jar",
                "8b39994ee6c5d71b8afacc80c2d13dd92fad10281374392c0049d1b6aebc823d7e137125268dee7383d3ff753eacf708fbe87d773cf0087d7b6057a05cf18ad3",
                () -> Settings.get().optionalDistantHorizons)
    );

    /** Filenames the launcher itself shipped as optional but that have been
     *  removed from the registry (e.g. Bobby — superseded by Distant
     *  Horizons). {@link #applyAll()} unconditionally deletes any of these
     *  jars from {@code mods/}, so a user who previously toggled them on
     *  doesn't keep an orphan jar that the modpack manifest no longer manages
     *  and the optional registry no longer knows about. */
    private static final List<String> DEPRECATED_FILENAMES = List.of(
            "bobby-5.2.4+mc1.21.jar"
    );

    /** Filenames declared as optional mods — used by {@link ModpackUpdater}
     *  cleanup so they're not deleted as "unmanaged" files. Includes the
     *  deprecated set so a still-installed orphan jar isn't deleted by the
     *  modpack-cleanup pass *before* {@link #applyAll()} gets the chance to
     *  log the removal as an explicit "deprecated" cleanup. */
    public static List<String> filenames() {
        List<String> out = new ArrayList<>(ENTRIES.size() + DEPRECATED_FILENAMES.size());
        for (Entry e : ENTRIES) out.add(e.fileName);
        out.addAll(DEPRECATED_FILENAMES);
        return out;
    }

    /** Reconcile mods/ with current Settings:
     *  <ul>
     *    <li>flag ON + file missing → download + verify</li>
     *    <li>flag OFF + file present → delete</li>
     *    <li>otherwise → no-op</li>
     *  </ul>
     *  Errors are logged but never bubble up — a failed optional mod must not
     *  block launch. */
    public static void applyAll() {
        Path modsDir = AppPaths.modsDir();
        // Always-clean step: jars that were optional in a previous launcher
        // version but have been retired. Run before applying current entries
        // so orphans are gone even if the registry shrinks further.
        for (String name : DEPRECATED_FILENAMES) {
            Path p = modsDir.resolve(name);
            if (Files.exists(p)) {
                try {
                    Files.deleteIfExists(p);
                    LOG.info("Removed deprecated optional mod: {}", name);
                } catch (Exception ex) {
                    LOG.warn("Could not delete deprecated optional {}: {}", name, ex.toString());
                }
            }
        }
        for (Entry e : ENTRIES) {
            try {
                applyOne(modsDir, e);
            } catch (Exception ex) {
                LOG.warn("Optional mod {} failed: {}", e.fileName, ex.toString());
            }
        }
    }

    private static void applyOne(Path modsDir, Entry e) throws IOException {
        Path target = modsDir.resolve(e.fileName);
        boolean wanted = e.enabled.getAsBoolean();
        boolean present = Files.exists(target);
        if (wanted && !present) {
            LOG.info("Installing optional mod: {}", e.fileName);
            Downloader.toFile(e.url, target, null);
            // Verify SHA-512 (Modrinth's primary digest); if it mismatches the
            // file is corrupt or upstream replaced the version — delete and
            // log so a retry next launch re-downloads cleanly.
            String got = sha512Hex(target);
            if (!got.equalsIgnoreCase(e.sha512)) {
                Files.deleteIfExists(target);
                throw new IOException("SHA mismatch for " + e.fileName);
            }
        } else if (!wanted && present) {
            LOG.info("Removing optional mod (toggled off): {}", e.fileName);
            Files.deleteIfExists(target);
        }
    }

    private static String sha512Hex(Path file) throws IOException {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-512");
            try (var in = Files.newInputStream(file)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            }
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException nse) {
            throw new IOException(nse);
        }
    }

    private OptionalMods() {}
}
