package fr.nylerp.launcher.auth;

import fr.litarvan.openauth.microsoft.MicrosoftAuthResult;
import fr.litarvan.openauth.microsoft.MicrosoftAuthenticator;
import fr.litarvan.openauth.microsoft.model.response.MinecraftProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public final class MicrosoftAuth {

    private static final Logger LOG = LoggerFactory.getLogger(MicrosoftAuth.class);

    /**
     * Opens a JavaFX WebView, lets the user sign in to Microsoft, and returns an
     * {@link Account} with access + refresh tokens.
     *
     * Must be called from the JavaFX application thread.
     */
    public static CompletableFuture<Account> loginWithWebview() {
        return CompletableFuture.supplyAsync(() -> {
            MicrosoftAuthenticator authenticator = new MicrosoftAuthenticator();
            try {
                MicrosoftAuthResult result = authenticator.loginWithAsyncWebview().get();
                MinecraftProfile profile = result.getProfile();
                LOG.info("Microsoft login OK — {} ({})", profile.getName(), profile.getId());
                return new Account(
                        Account.Type.MICROSOFT,
                        profile.getName(),
                        formatUuid(profile.getId()),
                        result.getAccessToken(),
                        result.getRefreshToken()
                );
            } catch (Exception e) {
                throw new RuntimeException("Connexion Microsoft échouée: " + e.getMessage(), e);
            }
        });
    }

    /** Silent re-auth from a saved refresh token. */
    public static CompletableFuture<Account> refresh(String refreshToken) {
        return CompletableFuture.supplyAsync(() -> {
            MicrosoftAuthenticator authenticator = new MicrosoftAuthenticator();
            try {
                MicrosoftAuthResult result = authenticator.loginWithRefreshToken(refreshToken);
                MinecraftProfile profile = result.getProfile();
                return new Account(
                        Account.Type.MICROSOFT,
                        profile.getName(),
                        formatUuid(profile.getId()),
                        result.getAccessToken(),
                        result.getRefreshToken()
                );
            } catch (Exception e) {
                throw new RuntimeException("Refresh MS échoué: " + e.getMessage(), e);
            }
        });
    }

    private static String formatUuid(String raw) {
        // Minecraft profile ids come dashed-less; normalise to RFC 4122 form.
        if (raw.length() != 32) return raw;
        return raw.substring(0, 8) + "-" +
                raw.substring(8, 12) + "-" +
                raw.substring(12, 16) + "-" +
                raw.substring(16, 20) + "-" +
                raw.substring(20);
    }

    private MicrosoftAuth() {}
}
