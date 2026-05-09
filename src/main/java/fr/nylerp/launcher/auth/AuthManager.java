package fr.nylerp.launcher.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.nylerp.launcher.config.AppPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public final class AuthManager {

    private static final Logger LOG = LoggerFactory.getLogger(AuthManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Wrapper persisted on disk: pairs an Account with the bootstrap version that
     *  owned it. New JSON shape — old sessions deserialize with null account → cleared. */
    private record SessionFile(Account account, String bootstrapVersion) {}

    public static void save(Account account) {
        Path file = AppPaths.sessionFile();
        try {
            String bs = fr.nylerp.launcher.update.SelfUpdater.installedVersion();
            Files.writeString(file, GSON.toJson(new SessionFile(account, bs)));
        } catch (Exception e) {
            LOG.warn("Could not save session: {}", e.toString());
        }
    }

    public static Account loadSaved() {
        Path file = AppPaths.sessionFile();
        if (!Files.exists(file)) return null;
        try {
            String json = Files.readString(file);
            SessionFile sf = GSON.fromJson(json, SessionFile.class);
            if (sf == null || sf.account == null) {
                // Either the file is from a pre-wrapped era (raw Account record) or empty.
                // Treat both as "no session" — the user gets the login screen and logs back
                // in once. Log so we know if this fires unexpectedly.
                LOG.info("Saved session has no account (likely pre-v0.3.12 format). Clearing.");
                clear();
                return null;
            }

            // Reset on bootstrap upgrade — every fresh install of a NEW launcher version
            // wipes the saved session. First launch after upgrade always lands on login.
            String currentBootstrap = fr.nylerp.launcher.update.SelfUpdater.installedVersion();
            if (sf.bootstrapVersion == null
                || !sf.bootstrapVersion.equals(currentBootstrap)) {
                LOG.info("Saved session was for bootstrap {} — currently running {}. " +
                         "Forcing re-login on the new launcher version.",
                         sf.bootstrapVersion, currentBootstrap);
                clear();
                return null;
            }
            return sf.account;
        } catch (Exception e) {
            LOG.warn("Could not read saved session: {}", e.toString());
            return null;
        }
    }

    public static void clear() {
        try { Files.deleteIfExists(AppPaths.sessionFile()); } catch (Exception ignored) {}
    }

    private AuthManager() {}
}
