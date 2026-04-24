package fr.nylerp.launcher.auth;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.nylerp.launcher.config.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Microsoft login via the <b>device code flow</b>. The pre-registered public
 * client id <code>00000000402b5328</code> (shared by MultiMC / Prism /
 * HMCL / OpenAuth — the standard MC launcher client) does not accept
 * arbitrary redirect URIs, so a loopback authorization code flow gets
 * "invalid_request: redirect_uri not registered". Device code doesn't need
 * a redirect URI at all: Microsoft hands us a short user_code, the user
 * enters it at microsoft.com/link in their own browser (session + password
 * manager + 2FA all native), and we poll the token endpoint until granted.
 *
 * After the user completes the browser step we run the standard
 * MS → Xbox Live → XSTS → Minecraft token exchange and return the final
 * in-game Account.
 *
 * Docs:
 *  - device code flow: https://learn.microsoft.com/en-us/azure/active-directory/develop/v2-oauth2-device-code
 *  - MC auth chain: https://wiki.vg/Microsoft_Authentication_Scheme
 */
public final class MicrosoftSystemAuth {

    private static final Logger LOG = LoggerFactory.getLogger(MicrosoftSystemAuth.class);
    private static final Gson GSON = new Gson();

    // The shared Minecraft third-party client id (00000000402b5328) is only
    // registered on the legacy Live endpoint, not on AAD v2. Hitting the v2
    // /devicecode endpoint with this id returns AADSTS700016 ("application
    // not found in the directory 'Microsoft Accounts'"). The Live endpoint
    // supports device code flow for this id natively, so we use it.
    private static final String DEVICE_URL = "https://login.live.com/oauth20_connect.srf";
    private static final String TOKEN_URL  = "https://login.live.com/oauth20_token.srf";
    private static final String XBL_URL    = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL   = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN   = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE = "https://api.minecraftservices.com/minecraft/profile";

    // Legacy MSA scope that matches the pre-registered redirects/flows for
    // client 00000000402b5328. Returns an XBL-ready RPS ticket directly.
    private static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";

    /** Info passed to the UI so it can show the code + open the browser. */
    public record DeviceCode(String userCode, String verificationUri) {}

    /**
     * Start the device-code flow. {@code onCodeReady} is invoked on the JavaFX
     * application thread as soon as Microsoft returns a user code, so the UI
     * can show it, copy it to clipboard, and open microsoft.com/link.
     */
    public static CompletableFuture<Account> login(java.util.function.Consumer<DeviceCode> onCodeReady) {
        return CompletableFuture.supplyAsync(() -> {
            try { return doLogin(onCodeReady); }
            catch (Exception e) {
                LOG.error("Microsoft login failed", e);
                Throwable c = e;
                while (c.getCause() != null && c.getCause() != c) c = c.getCause();
                throw new RuntimeException("Connexion Microsoft échouée: " + c.getMessage(), c);
            }
        });
    }

    public static CompletableFuture<Account> refresh(String refreshToken) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpClient http = httpClient();
                String body = "client_id=" + urlEnc(Constants.MS_CLIENT_ID)
                        + "&refresh_token=" + urlEnc(refreshToken)
                        + "&grant_type=refresh_token"
                        + "&scope=" + urlEnc(SCOPE);
                JsonObject tokenResp = postForm(http, TOKEN_URL, body);
                String accessToken = tokenResp.get("access_token").getAsString();
                String newRefresh = tokenResp.has("refresh_token")
                        ? tokenResp.get("refresh_token").getAsString()
                        : refreshToken;
                return minecraftFromMsToken(http, accessToken, newRefresh);
            } catch (Exception e) {
                throw new RuntimeException("Refresh MS échoué: " + e.getMessage(), e);
            }
        });
    }

    private static Account doLogin(java.util.function.Consumer<DeviceCode> onCodeReady) throws Exception {
        HttpClient http = httpClient();

        // 1. Request a device code from Microsoft (Live endpoint)
        String devBody = "client_id=" + urlEnc(Constants.MS_CLIENT_ID)
                + "&scope=" + urlEnc(SCOPE)
                + "&response_type=device_code";
        JsonObject dev = postForm(http, DEVICE_URL, devBody);
        String deviceCode = dev.get("device_code").getAsString();
        String userCode   = dev.get("user_code").getAsString();
        String verifyUri  = dev.get("verification_uri").getAsString();
        int intervalSec   = dev.has("interval") ? dev.get("interval").getAsInt() : 5;
        int expiresIn     = dev.has("expires_in") ? dev.get("expires_in").getAsInt() : 900;

        LOG.info("Device code ready — user_code={}, verification_uri={}, expires in {}s",
                userCode, verifyUri, expiresIn);

        // 2. Notify the UI and open the browser
        onCodeReady.accept(new DeviceCode(userCode, verifyUri));
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(verifyUri));
            }
        } catch (Exception openErr) {
            LOG.warn("Could not auto-open browser: {}", openErr.toString());
        }

        // 3. Poll the token endpoint until the user completes the browser step
        long deadline = System.currentTimeMillis() + expiresIn * 1000L;
        String accessToken = null, refreshToken = null;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(intervalSec * 1000L);
            String tokenBody = "client_id=" + urlEnc(Constants.MS_CLIENT_ID)
                    + "&grant_type=" + urlEnc("urn:ietf:params:oauth:grant-type:device_code")
                    + "&device_code=" + urlEnc(deviceCode);
            HttpRequest req = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(tokenBody))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonObject body = GSON.fromJson(resp.body(), JsonObject.class);
            if (resp.statusCode() == 200) {
                accessToken  = body.get("access_token").getAsString();
                refreshToken = body.has("refresh_token") ? body.get("refresh_token").getAsString() : null;
                break;
            }
            String err = body != null && body.has("error") ? body.get("error").getAsString() : "";
            switch (err) {
                case "authorization_pending":
                    // normal — user hasn't completed yet, keep polling
                    continue;
                case "slow_down":
                    intervalSec += 5;
                    continue;
                case "expired_token":
                case "code_expired":
                    throw new RuntimeException("Le code de connexion a expiré, relance la connexion Microsoft.");
                case "authorization_declined":
                    throw new RuntimeException("Connexion refusée côté Microsoft.");
                case "bad_verification_code":
                    throw new RuntimeException("Code de vérification invalide.");
                default:
                    throw new RuntimeException("Microsoft a rejeté la connexion: "
                            + (body != null ? body.toString() : "HTTP " + resp.statusCode()));
            }
        }
        if (accessToken == null) {
            throw new RuntimeException("Délai dépassé — tu n'as pas saisi le code dans le navigateur à temps.");
        }

        return minecraftFromMsToken(http, accessToken, refreshToken);
    }

    private static Account minecraftFromMsToken(HttpClient http, String msAccess, String refresh) throws Exception {
        // 1. Xbox Live — legacy MSA tokens from the Live endpoint are used as
        // the RPS ticket directly (no "d=" prefix). That prefix is for AAD v2
        // bearer tokens.
        JsonObject xblBody = new JsonObject();
        JsonObject xblProps = new JsonObject();
        xblProps.addProperty("AuthMethod", "RPS");
        xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
        xblProps.addProperty("RpsTicket", msAccess);
        xblBody.add("Properties", xblProps);
        xblBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblBody.addProperty("TokenType", "JWT");
        JsonObject xbl = postJson(http, XBL_URL, xblBody.toString());
        String xblToken = xbl.get("Token").getAsString();
        String uhs = xbl.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0).getAsJsonObject()
                .get("uhs").getAsString();

        // 2. XSTS
        JsonObject xstsBody = new JsonObject();
        JsonObject xstsProps = new JsonObject();
        JsonArray tokens = new JsonArray();
        tokens.add(xblToken);
        xstsProps.add("UserTokens", tokens);
        xstsProps.addProperty("SandboxId", "RETAIL");
        xstsBody.add("Properties", xstsProps);
        xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsBody.addProperty("TokenType", "JWT");
        JsonObject xsts;
        try {
            xsts = postJson(http, XSTS_URL, xstsBody.toString());
        } catch (XstsException e) {
            throw explainXsts(e.xerr);
        }
        String xstsToken = xsts.get("Token").getAsString();

        // 3. Minecraft auth
        JsonObject mcBody = new JsonObject();
        mcBody.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        JsonObject mc = postJson(http, MC_LOGIN, mcBody.toString());
        String mcToken = mc.get("access_token").getAsString();

        // 4. Minecraft profile (name + uuid)
        HttpRequest profReq = HttpRequest.newBuilder(URI.create(MC_PROFILE))
                .header("Authorization", "Bearer " + mcToken)
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> prof = http.send(profReq, HttpResponse.BodyHandlers.ofString());
        if (prof.statusCode() == 404) {
            throw new RuntimeException("Ce compte Microsoft ne possède pas Minecraft Java Edition.");
        }
        if (prof.statusCode() != 200) {
            throw new RuntimeException("Récupération du profil Minecraft échouée (HTTP " + prof.statusCode() + ")");
        }
        JsonObject pj = GSON.fromJson(prof.body(), JsonObject.class);
        String name = pj.get("name").getAsString();
        String uuid = pj.get("id").getAsString();
        LOG.info("Microsoft login OK — {} ({})", name, uuid);
        return new Account(Account.Type.MICROSOFT, name, formatUuid(uuid), mcToken, refresh);
    }

    private static JsonObject postForm(HttpClient http, String url, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() < 200 || r.statusCode() >= 300) {
            throw new RuntimeException("HTTP " + r.statusCode() + " on " + url + ": " + r.body());
        }
        return GSON.fromJson(r.body(), JsonObject.class);
    }

    private static JsonObject postJson(HttpClient http, String url, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() == 401 && url.equals(XSTS_URL)) {
            JsonObject err = GSON.fromJson(r.body(), JsonObject.class);
            throw new XstsException(err.has("XErr") ? err.get("XErr").getAsLong() : 0);
        }
        if (r.statusCode() < 200 || r.statusCode() >= 300) {
            throw new RuntimeException("HTTP " + r.statusCode() + " on " + url + ": " + r.body());
        }
        return GSON.fromJson(r.body(), JsonObject.class);
    }

    private static RuntimeException explainXsts(long xerr) {
        String msg = switch ((int) xerr) {
            case 0x8015DC04 -> "Compte Microsoft mineur — un adulte doit l'ajouter à une famille Microsoft.";
            case 0x8015DC06 -> "Compte Xbox Live non créé — connecte-toi une fois à xbox.com.";
            case 0x8015DC08 -> "Ce compte n'existe pas.";
            case 0x8015DC09 -> "Compte non accessible depuis ce pays/région.";
            default -> "Xbox Live a refusé l'authentification (XErr=" + Long.toHexString(xerr) + ").";
        };
        return new RuntimeException(msg);
    }

    private static HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    private static String urlEnc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String formatUuid(String raw) {
        if (raw.length() != 32) return raw;
        return raw.substring(0, 8) + "-" + raw.substring(8, 12) + "-"
                + raw.substring(12, 16) + "-" + raw.substring(16, 20) + "-" + raw.substring(20);
    }

    private static final class XstsException extends RuntimeException {
        final long xerr;
        XstsException(long xerr) { super("XSTS XErr=" + Long.toHexString(xerr)); this.xerr = xerr; }
    }

    private MicrosoftSystemAuth() {}
}
