package fr.nylerp.launcher.auth;

import java.util.List;

/**
 * Traduit les erreurs brutes du flux Microsoft/Xbox/Minecraft en explications CLAIRES pour le
 * joueur : la cause probable en français simple + les étapes concrètes pour s'en sortir.
 *
 * <p>Contexte : OpenAuth remonte le message HTTP brut (ex. « Server returned HTTP response
 * code: 401 for URL: https://xsts.auth.xboxlive.com/xsts/authorize ») sans lire le corps de la
 * réponse (le code {@code XErr} précis est perdu). Heureusement chaque ÉTAPE du flux n'a que
 * quelques causes réelles — on route donc par l'URL/l'exception :</p>
 * <ul>
 *   <li><b>xsts.auth.xboxlive.com + 401</b> — refus Xbox Live. Dans ~95 % des cas : compte
 *       ENFANT hors famille Microsoft ({@code XErr 2148916238}) ou compte sans profil Xbox
 *       ({@code XErr 2148916233}). Plus rare : Xbox Live indisponible dans le pays.</li>
 *   <li><b>api.minecraftservices.com + 404/profile</b> — le compte Microsoft n'a pas de profil
 *       Minecraft Java (jeu non possédé, ou Game Pass jamais initialisé sur minecraft.net).</li>
 *   <li><b>UnknownHost / timeout / connect</b> — pas d'accès réseau.</li>
 * </ul>
 */
public final class AuthErrorExplainer {

    /** Résultat : titre + message + étapes (puces) + détail technique d'origine. */
    public record Explained(String title, String message, List<String> steps, String technical) {}

    private AuthErrorExplainer() {}

    public static Explained explain(String rawMessage) {
        String raw = rawMessage == null ? "" : rawMessage;
        String low = raw.toLowerCase();

        // ── Refus Xbox Live (XSTS) : compte enfant / pas de profil Xbox ─────────────────────────
        if (low.contains("xsts.auth.xboxlive.com")) {
            return new Explained(
                "Connexion refusée par Xbox Live",
                "Ton compte Microsoft fonctionne, mais Xbox Live a refusé de te connecter. "
                        + "C'est presque toujours l'un de ces deux cas :",
                List.of(
                    "Tu as un COMPTE ENFANT (moins de 18 ans) : un parent doit t'ajouter à sa "
                            + "famille Microsoft sur family.microsoft.com (avec SON compte adulte), "
                            + "puis réessaie ici.",
                    "Ton compte n'a JAMAIS eu de profil Xbox : connecte-toi une fois sur xbox.com "
                            + "avec ce compte pour créer ton profil, puis réessaie.",
                    "Toujours bloqué ? Connecte-toi une fois sur minecraft.net avec ce compte, "
                            + "puis relance le launcher."
                ),
                raw);
        }

        // ── Token Microsoft périmé/refusé en amont (login.live / user.auth) ──────────────────────
        if (low.contains("user.auth.xboxlive.com") || low.contains("login.live.com")) {
            return new Explained(
                "Session Microsoft expirée",
                "Microsoft a refusé la session enregistrée — ça arrive après un changement de mot "
                        + "de passe, d'adresse IP ou une longue inactivité.",
                List.of(
                    "Clique sur « Continuer avec Microsoft » et reconnecte-toi normalement.",
                    "Si la fenêtre se referme aussitôt, redémarre le launcher puis réessaie."
                ),
                raw);
        }

        // ── Pas de profil Minecraft Java sur ce compte ───────────────────────────────────────────
        if (low.contains("minecraftservices") &&
                (low.contains("404") || low.contains("not_found") || low.contains("profile"))) {
            return new Explained(
                "Ce compte ne possède pas Minecraft Java",
                "La connexion Microsoft a réussi, mais ce compte n'a aucun profil Minecraft "
                        + "Java Edition.",
                List.of(
                    "Vérifie que tu t'es connecté(e) avec le BON compte Microsoft (celui qui a "
                            + "acheté le jeu).",
                    "Tu joues via le Game Pass ? Connecte-toi une fois sur minecraft.net et crée "
                            + "ton profil Java, puis réessaie.",
                    "Sinon, le jeu s'achète sur minecraft.net — ou joue en mode « offline » en "
                            + "attendant."
                ),
                raw);
        }

        // ── Réseau ───────────────────────────────────────────────────────────────────────────────
        if (low.contains("unknownhost") || low.contains("timed out") || low.contains("timeout")
                || low.contains("connection refused") || low.contains("connectexception")
                || low.contains("no route") || low.contains("network is unreachable")) {
            return new Explained(
                "Pas de connexion à Microsoft",
                "Le launcher n'arrive pas à joindre les serveurs de Microsoft — c'est un souci "
                        + "de connexion internet, pas ton compte.",
                List.of(
                    "Vérifie ta connexion internet (wifi/câble) et réessaie.",
                    "Si tu utilises un VPN ou un pare-feu, désactive-le le temps de te connecter."
                ),
                raw);
        }

        // ── Générique ────────────────────────────────────────────────────────────────────────────
        return new Explained(
            "Connexion Microsoft échouée",
            "Quelque chose s'est mal passé pendant la connexion à ton compte Microsoft.",
            List.of(
                "Réessaie — la plupart du temps ça passe au 2e essai.",
                "Si ça persiste, connecte-toi une fois sur minecraft.net avec ce compte, "
                        + "puis relance le launcher."
            ),
            raw);
    }
}
