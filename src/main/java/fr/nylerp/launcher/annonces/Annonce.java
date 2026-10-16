package fr.nylerp.launcher.annonces;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Une annonce telle que le contrat L7 la décrit, déjà nettoyée : texte brut, bornes de longueur,
 * bouton lien refusé s'il n'est pas en https. Une annonce mal formée n'est jamais construite.
 */
record Annonce(String id, String titre, String texte, String univers, String compte,
               OffsetDateTime debut, OffsetDateTime fin,
               String actionType, String actionValeur, int priorite) {

    private static final Set<String> UNIVERS = Set.of("tous", "pokenyle", "nylerp");
    private static final Set<String> COMPTES = Set.of("tous", "premium", "crack");

    /** En cours à cet instant : {@code debut <= maintenant < fin} ({@code fin} nulle = sans fin). */
    boolean enCours(OffsetDateTime maintenant) {
        return !maintenant.isBefore(debut) && (fin == null || maintenant.isBefore(fin));
    }

    /**
     * Cible passée à {@code Fond.notifier} : l'univers à ouvrir, l'URL https à ouvrir dans le
     * navigateur, ou null (le clic ouvre simplement le launcher).
     */
    String cible() {
        return switch (actionType) {
            case "univers", "url" -> actionValeur;
            default -> null;
        };
    }

    /** Lit une entrée de {@code annonces[]} ; null si elle ne respecte pas le contrat. */
    static Annonce depuis(JsonElement e) {
        try {
            if (e == null || !e.isJsonObject()) return null;
            JsonObject o = e.getAsJsonObject();
            String id = chaine(o, "id");
            String titre = texteBrut(chaine(o, "titre"), 60);
            String texte = texteBrut(chaine(o, "texte"), 160);
            String univers = chaine(o, "univers");
            String compte = chaine(o, "compte");
            String debut = chaine(o, "debut");
            if (id == null || id.isBlank() || titre.isEmpty() || texte.isEmpty() || debut == null) return null;
            if (!UNIVERS.contains(univers) || !COMPTES.contains(compte)) return null;
            String fin = chaine(o, "fin");
            String type = "aucune";
            String valeur = null;
            if (o.has("action") && o.get("action").isJsonObject()) {
                JsonObject a = o.getAsJsonObject("action");
                String t = chaine(a, "type");
                String v = chaine(a, "valeur");
                if ("univers".equals(t) && ("pokenyle".equals(v) || "nylerp".equals(v))) {
                    type = t;
                    valeur = v;
                } else if ("url".equals(t) && estHttps(v)) {
                    type = t;
                    valeur = v.trim();
                }
            }
            int priorite = o.has("priorite") && o.get("priorite").isJsonPrimitive()
                    && o.get("priorite").getAsJsonPrimitive().isNumber()
                    ? Math.max(0, Math.min(9, o.get("priorite").getAsInt())) : 0;
            return new Annonce(id.trim(), titre, texte, univers, compte,
                    OffsetDateTime.parse(debut), fin == null ? null : OffsetDateTime.parse(fin),
                    type, valeur, priorite);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Lien accepté pour un bouton : https, hôte présent, 500 caractères au plus. */
    static boolean estHttps(String v) {
        if (v == null) return false;
        String u = v.trim();
        if (u.length() > 500 || !u.regionMatches(true, 0, "https://", 0, 8)) return false;
        try {
            URI uri = URI.create(u);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Texte brut d'une ligne : caractères de contrôle retirés, blancs resserrés, borné à {@code max}. */
    static String texteBrut(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max).trim();
    }

    private static String chaine(JsonObject o, String cle) {
        JsonElement e = o.get(cle);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }
}
