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
        LOG.info("Microsoft login starting (OpenAuth async webview)…");
        // Session Microsoft PROPRE à chaque tentative : on remplace le gestionnaire de cookies
        // par un neuf (vide) avant d'ouvrir le WebView. Le WebView JavaFX lit
        // CookieHandler.getDefault() dynamiquement, donc ceci force Microsoft à TOUJOURS
        // réafficher la page de connexion. Sans ça, la session du compte précédent persiste
        // dans la même session du launcher → au 2e essai Microsoft auto-login le compte
        // précédent SANS réafficher la page → refus immédiat (il fallait quitter/relancer).
        clearMicrosoftSession();
        return CompletableFuture.supplyAsync(() -> {
            MicrosoftAuthenticator authenticator = new MicrosoftAuthenticator();
            try {
                LOG.debug("Calling loginWithAsyncWebview()…");
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
                LOG.error("Microsoft login FAILED: {}", e.toString(), e);
                // Unwrap ExecutionException to get the real cause message
                Throwable cause = e;
                while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
                throw new RuntimeException("Connexion Microsoft échouée: " + cause.getMessage(), cause);
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

    /** Remplace le gestionnaire de cookies par un neuf (vide) → session Microsoft repartie de zéro
     *  pour le prochain WebView, ce qui garantit que la page de connexion se réaffiche et qu'on peut
     *  changer de compte / réessayer après un échec sans redémarrer le launcher. */
    private static void clearMicrosoftSession() {
        try {
            java.net.CookieManager fresh = new java.net.CookieManager();
            fresh.setCookiePolicy(java.net.CookiePolicy.ACCEPT_ALL);
            java.net.CookieHandler.setDefault(fresh);
        } catch (Exception e) {
            LOG.warn("clearMicrosoftSession failed (non-fatal): {}", e.toString());
        }
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
