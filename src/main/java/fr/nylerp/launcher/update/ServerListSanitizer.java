package fr.nylerp.launcher.update;

import fr.nylerp.launcher.config.AppPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One-shot self-heal for stale {@code servers.dat} entries.
 *
 * <p>Background: {@code servers.dat} is shipped {@code firstInstallOnly} in the
 * manifest — pulled exactly once on first install and never re-pulled after
 * that (see {@link ModpackUpdater}'s firstInstallOnly guard, which preserves
 * the user's last-played / favourites). Older installs therefore keep server
 * entries that point DIRECTLY at a backend
 * ({@code game45-fr.hosterfy.com:20652}, {@code 83.143.117.51:20652},
 * {@code play.nylerp.fr:25565}). Those backends are now proxy-only and REJECT
 * direct connections, so the player gets intermittent
 * "connecte-toi via play.nylerp.fr" failures. The clean pack ships a single
 * correct entry ({@code NYLE RP → play.nylerp.fr}).
 *
 * <p>{@link #sweep()} detects any deprecated host:port substring in the raw
 * bytes of {@code servers.dat} and, if found, deletes the file. Deleting it
 * makes it ABSENT, so the firstInstallOnly guard no longer short-circuits and
 * {@link ModpackUpdater} re-pulls the clean copy from the manifest on the very
 * next sync. This MUST therefore run BEFORE the modpack sync.
 *
 * <p>We deliberately do NOT parse the NBT. {@code servers.dat} is a tiny
 * uncompressed NBT blob where host strings are stored verbatim as UTF-8; a raw
 * byte-substring check is reliable and carries zero risk of corrupting the file
 * through a partial/buggy re-encode. The decision is binary: clean, or delete
 * and let the manifest re-pull.
 */
public final class ServerListSanitizer {

    private static final Logger LOG = LoggerFactory.getLogger(ServerListSanitizer.class);

    /** Deprecated direct-backend markers. Presence of ANY of these in
     *  servers.dat means the file predates the proxy migration and must be
     *  re-pulled clean. Stored as ASCII byte patterns (host strings are plain
     *  UTF-8/ASCII inside the NBT). */
    private static final String[] DEPRECATED_MARKERS = {
            "game45-fr.hosterfy.com",
            "83.143.117.51:20652",
            "play.nylerp.fr:25565",
    };

    /**
     * Inspects {@code <gameDir>/servers.dat} and deletes it if it still carries
     * any deprecated direct-backend entry, so the firstInstallOnly manifest
     * pull restores the clean proxy-only list. Never throws — a self-heal
     * failure must not block launch.
     */
    public static void sweep() {
        Path serversDat = AppPaths.gameDir().resolve("servers.dat");

        // RESET UNIQUE (v2) — purge l'ancienne liste multi-serveurs et ne garde QUE play.nylerp.fr.
        // On supprime servers.dat une seule fois (gardé par un marqueur d'état) : la liste propre à
        // entrée unique est alors re-pull via l'entrée firstInstallOnly du manifest. Le marqueur
        // évite de réinitialiser à chaque lancement → on ne combat pas les serveurs que le joueur
        // ajouterait ensuite. Sur une install neuve servers.dat est absent : deleteIfExists est un
        // no-op sûr et le marqueur est quand même posé (pas de reset répété).
        try {
            Path marker = AppPaths.launcherState().resolve("serverlist_reset_v2");
            if (!Files.exists(marker)) {
                Files.deleteIfExists(serversDat);
                Files.writeString(marker, "done");
                LOG.info("Reset unique de la liste serveurs : servers.dat purgé → re-pull "
                        + "de la liste propre (play.nylerp.fr uniquement)");
                return;
            }
        } catch (Exception ex) {
            LOG.warn("server list one-time reset failed (left untouched): {}", ex.toString());
        }

        if (!Files.exists(serversDat)) {
            return; // nothing to heal — ModpackUpdater will pull a fresh copy
        }
        try {
            byte[] bytes = Files.readAllBytes(serversDat);
            String marker = findDeprecatedMarker(bytes);
            if (marker == null) {
                return; // already clean
            }
            Files.deleteIfExists(serversDat);
            LOG.info("Self-heal: deleted stale servers.dat (found deprecated entry '{}') — "
                    + "modpack sync will re-pull the clean proxy-only server list", marker);
        } catch (Exception ex) {
            LOG.warn("servers.dat self-heal failed (left untouched): {}", ex.toString());
        }
    }

    /** Returns the first deprecated marker found in the raw bytes, or null if
     *  the file is clean. Uses an ASCII byte-substring scan — no NBT parsing. */
    private static String findDeprecatedMarker(byte[] haystack) {
        for (String marker : DEPRECATED_MARKERS) {
            if (indexOf(haystack, marker.getBytes(StandardCharsets.US_ASCII)) >= 0) {
                return marker;
            }
        }
        return null;
    }

    /** Naive byte-substring search. servers.dat is a few hundred bytes, so the
     *  O(n*m) scan is negligible and avoids any charset/decoding ambiguity. */
    private static int indexOf(byte[] haystack, byte[] needle) {
        if (needle.length == 0 || haystack.length < needle.length) return -1;
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private ServerListSanitizer() {}
}
