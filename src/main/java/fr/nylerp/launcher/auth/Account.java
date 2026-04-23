package fr.nylerp.launcher.auth;

/**
 * A resolved authenticated account — enough info to launch Minecraft.
 *
 * @param type        MICROSOFT or OFFLINE
 * @param username    player display name
 * @param uuid        player UUID (dashed form)
 * @param accessToken MS access token (null for offline)
 * @param refreshToken MS refresh token (for silent re-auth) — null for offline
 */
public record Account(Type type,
                      String username,
                      String uuid,
                      String accessToken,
                      String refreshToken) {

    public enum Type { MICROSOFT, OFFLINE }

    public boolean isOffline() { return type == Type.OFFLINE; }
}
