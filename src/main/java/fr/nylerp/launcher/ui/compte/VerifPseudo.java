package fr.nylerp.launcher.ui.compte;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Vérifie, avant d'enregistrer un compte crack, que le pseudo n'appartient pas déjà à un compte
 * Minecraft ACHETÉ. Sur nos serveurs, NyleAuth impose la connexion Microsoft à tout pseudo connu de
 * Mojang (mémoire reference_nyleauth : « lookup Mojang, existe → ForcePremium ») : un joueur crack
 * qui prend un tel pseudo serait refusé à la connexion sans comprendre pourquoi. On le lui dit ici.
 *
 * <p>Jamais bloquant sur une panne : réseau coupé, délai dépassé ou réponse inattendue donnent
 * {@link Resultat#INCONNU} et l'écran laisse passer (le serveur reste l'arbitre).
 */
public final class VerifPseudo {

    public enum Resultat { LIBRE, ACHETE, INCONNU }

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static CompletableFuture<Resultat> verifier(String pseudo) {
        String p = pseudo == null ? "" : pseudo.trim();
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/"
                        + URLEncoder.encode(p, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", "NyleLauncher")
                .GET().build();
        return CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> interpreter(r.statusCode(), r.body()))
                .exceptionally(e -> Resultat.INCONNU);
    }

    /** 200 avec un identifiant = pseudo pris ; 204/404 = libre ; le reste (429, 5xx…) = inconnu. */
    static Resultat interpreter(int code, String corps) {
        if (code == 200 && corps != null && corps.contains("\"id\"")) return Resultat.ACHETE;
        if (code == 204 || code == 404) return Resultat.LIBRE;
        return Resultat.INCONNU;
    }

    private VerifPseudo() {}
}
