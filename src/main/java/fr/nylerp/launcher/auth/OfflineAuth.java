package fr.nylerp.launcher.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class OfflineAuth {

    /** Produce the UUID that Minecraft itself derives from a username for offline mode. */
    public static String deriveOfflineUuid(String username) {
        // Matches the algorithm used by vanilla Minecraft offline mode.
        byte[] bytes = ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8);
        UUID uuid = UUID.nameUUIDFromBytes(bytes);
        return uuid.toString();
    }

    public static Account login(String username) {
        if (username == null) throw new IllegalArgumentException("username cannot be null");
        String trimmed = username.trim();
        if (trimmed.length() < 3 || trimmed.length() > 16 || !trimmed.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException(
                    "Pseudo invalide (3-16 caractères, lettres/chiffres/underscore uniquement)");
        }
        return new Account(
                Account.Type.OFFLINE,
                trimmed,
                deriveOfflineUuid(trimmed),
                // offline mode requires any non-empty token for Minecraft startup
                "0".repeat(32),
                null
        );
    }

    private OfflineAuth() {}
}
