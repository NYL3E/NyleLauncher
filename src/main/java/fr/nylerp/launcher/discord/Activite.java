package fr.nylerp.launcher.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * CE QUE VOIENT LES AMIS DU JOUEUR sur Discord, selon l'état du launcher.
 *
 * <p>Champs d'après la doc (Gateway Events, objet Activity) : {@code details} est la première ligne sous le
 * nom ; {@code timestamps.start} fait le chrono (secondes, comme la bibliothèque officielle discord-rpc et
 * l'exemple de la doc RPC) ; {@code assets.large_image} est une clé d'image de l'application ;
 * {@code buttons} : deux au plus, libellé de 1 à 32 caractères, adresse de 1 à 512.
 *
 * <p>Partagé tel quel avec le mod client {@code nylepresence} (même fichier, à garder identique) : le launcher
 * se sert de LANCEUR, MISE_A_JOUR et EN_JEU ; le mod, de ECRAN_TITRE, SOLO et EN_JEU.
 */
public final class Activite {

    /** Le nom de l'application Discord. Envoyé aussi dans {@code name}, sans en dépendre (même valeur). */
    static final String NOM = "NyleLauncher";
    /** Clé d'image par défaut (portail Discord, Rich Presence, Art Assets) ; remplaçable par la config ({@code discordImage}). */
    static final String CLE_IMAGE = "logo";
    /** Longueur maximale admise pour une image venue de la config (clé ou adresse). */
    static final int IMAGE_MAX = 256;
    static final String BOUTON = "Rejoindre le serveur";
    /** Racines des sites dont le launcher ouvre déjà la boutique ({@code ui/coquille/Liens}). */
    static final String SITE_POKENYLE = "https://pokerayou.academy/";
    static final String SITE_NYLERP = "https://www.nylerp.fr/";

    static final String TEXTE_LANCEUR = "Dans le lanceur";
    static final String TEXTE_MISE_A_JOUR = "Mise à jour du jeu…";
    static final String TEXTE_EN_JEU_POKENYLE = "En jeu sur Pokényle";
    static final String TEXTE_EN_JEU_NYLERP = "En jeu sur NyleRP";
    static final String TEXTE_ECRAN_TITRE = "Sur l'écran titre";
    static final String TEXTE_SOLO = "En solo";

    /** {@code status_display_type} : 0 = nom de l'activité dans la liste des membres, 2 = champ {@code details}. */
    static final int STATUT_NOM = 0;
    static final int STATUT_DETAILS = 2;

    public enum Phase { LANCEUR, MISE_A_JOUR, EN_JEU, ECRAN_TITRE, SOLO }

    /**
     * Tout ce dont dépend l'activité, figé à un instant.
     *
     * @param fenetreVisible la fenêtre du launcher est à l'écran (sans effet hors de la phase LANCEUR)
     * @param pokenyle       l'univers concerné (celui du jeu lancé, ou celui choisi dans le launcher)
     * @param debutJeu       lancement du jeu, en secondes depuis 1970 (phase EN_JEU)
     * @param enFond         config distante {@code discordEnFond} : activité même quand le launcher est seul dans la barre
     * @param statutDetails  config distante {@code discordStatut = "details"} : en jeu, la liste des membres montre « Pokényle »
     * @param image          config {@code discordImage} : clé d'image ou adresse https ; null ou invalide = {@link #CLE_IMAGE}
     */
    public record Situation(Phase phase, boolean fenetreVisible, boolean pokenyle, long debutJeu,
                     boolean enFond, boolean statutDetails, String image) { }

    /**
     * L'image venue de la config, si elle est admissible : une clé simple ({@code [a-z0-9_-]}, 1 à 64 caractères, comme
     * les clés du portail, qui sont en minuscules) ou une adresse {@code https://} sans espace ni caractère de contrôle ni
     * guillemet, de {@value #IMAGE_MAX} caractères au plus (la doc admet les deux dans {@code assets.large_image}).
     * Sinon null : on garde {@link #CLE_IMAGE}.
     */
    public static String imageValide(String v) {
        if (v == null) return null;
        String t = v.trim();
        if (t.isEmpty() || t.length() > IMAGE_MAX) return null;
        if (t.matches("[a-z0-9_-]{1,64}")) return t;
        if (t.startsWith("https://") && t.length() > "https://".length() && t.matches("https://[\\x21-\\x7E&&[^\"'<>\\\\`{}|^]]+")) return t;
        return null;
    }

    private Activite() { }

    /** L'activité à poser, ou null pour n'en montrer aucune (launcher seul dans la barre système). */
    public static JsonObject pour(Situation s) {
        if (s.phase() == Phase.LANCEUR && !s.fenetreVisible() && !s.enFond()) return null;

        JsonObject a = new JsonObject();
        a.addProperty("name", NOM);
        a.addProperty("type", 0); // « Joue à »
        if (s.phase() == Phase.EN_JEU && s.statutDetails()) {
            a.addProperty("details", s.pokenyle() ? "Pokényle" : "NyleRP");
            a.addProperty("state", "En jeu");
            a.addProperty("status_display_type", STATUT_DETAILS);
        } else {
            a.addProperty("details", switch (s.phase()) {
                case LANCEUR -> TEXTE_LANCEUR;
                case MISE_A_JOUR -> TEXTE_MISE_A_JOUR;
                case EN_JEU -> s.pokenyle() ? TEXTE_EN_JEU_POKENYLE : TEXTE_EN_JEU_NYLERP;
                case ECRAN_TITRE -> TEXTE_ECRAN_TITRE;
                case SOLO -> TEXTE_SOLO;
            });
        }
        if (s.phase() == Phase.EN_JEU && s.debutJeu() > 0) {
            JsonObject t = new JsonObject();
            t.addProperty("start", s.debutJeu());
            a.add("timestamps", t);
        }
        JsonObject images = new JsonObject();
        String image = imageValide(s.image());
        images.addProperty("large_image", image != null ? image : CLE_IMAGE);
        images.addProperty("large_text", NOM);
        a.add("assets", images);

        JsonObject bouton = new JsonObject();
        bouton.addProperty("label", BOUTON);
        bouton.addProperty("url", s.pokenyle() ? SITE_POKENYLE : SITE_NYLERP);
        JsonArray boutons = new JsonArray();
        boutons.add(bouton);
        a.add("buttons", boutons);
        a.addProperty("instance", false);
        return a;
    }

    /**
     * Une version plus simple de l'activité, si Discord refuse la complète : 1 = sans {@code name},
     * 2 = sans {@code name}, sans boutons et sans {@code status_display_type} (champs les plus récents).
     */
    static JsonObject repli(JsonObject a, int niveau) {
        if (a == null || niveau <= 0) return a;
        JsonObject r = a.deepCopy();
        r.remove("name");
        if (niveau >= 2) {
            r.remove("buttons");
            r.remove("status_display_type");
        }
        return r;
    }

    /** La première ligne, pour le journal. */
    static String resume(JsonObject a) {
        if (a == null) return "aucune";
        return a.has("details") ? a.get("details").getAsString() : NOM;
    }
}
