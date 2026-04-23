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

    public static void save(Account account) {
        Path file = AppPaths.sessionFile();
        try {
            Files.writeString(file, GSON.toJson(account));
        } catch (Exception e) {
            LOG.warn("Could not save session: {}", e.toString());
        }
    }

    public static Account loadSaved() {
        Path file = AppPaths.sessionFile();
        if (!Files.exists(file)) return null;
        try {
            String json = Files.readString(file);
            return GSON.fromJson(json, Account.class);
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
