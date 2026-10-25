package fr.nylerp.launcher.annonces;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.nylerp.launcher.univers.SourceEtatPokenyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * L'état de Pokényle piloté depuis le Studio ({@code /api/launcher/config}) : date d'ouverture,
 * ouverture générale, comptes crack, Discord, testeurs.
 *
 * <p>Toute clé absente ou au mauvais type prend la valeur par défaut du contrat, une à une : une
 * faute de frappe au Studio sur une clé ne fait jamais tomber les autres. Hors ligne, on lit la
 * copie locale ; sans copie, les défauts décrivent un Pokényle pas encore ouvert.
 */
public final class ConfigDistante implements SourceEtatPokenyle {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigDistante.class);
    static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    static final String LIBELLE_DEFAUT = "mi-décembre";
    static final String MESSAGE_CRACK_DEFAUT =
            "Pokényle demande pour l'instant un compte Minecraft acheté. Les comptes crack arrivent à l'ouverture.";
    static final String DISCORD_DEFAUT = "https://discord.gg/nyle";

    /**
     * Repli quand la config distante ne donne pas de liste : la même que {@code SelecteurMode.AUTORISES}
     * (owner + testeurs désignés le 2026-08-30, « khuunaa » le 2026-10-25). Recopiée ici plutôt que lue
     * dans l'interface, qui sera refaite par la refonte.
     */
    static final Set<String> TESTEURS_REPLI = Set.of("nyl3e", "mrcedriic", "fell4eli", "disryy", "milanzio", "khuunaa");

    /** Valeurs lues, immuables : une lecture concurrente voit toujours un état complet. */
    record Etat(ZonedDateTime ouverture, String libelle, boolean ouvert, boolean crack, String messageCrack,
                boolean discordActif, String discordUrl, Set<String> testeurs) {
        static final Etat DEFAUT = new Etat(null, LIBELLE_DEFAUT, false, false, MESSAGE_CRACK_DEFAUT,
                false, DISCORD_DEFAUT, null);
    }

    private final PointAcces point;
    private final List<Runnable> rappels = new CopyOnWriteArrayList<>();
    private volatile Etat etat;

    ConfigDistante(PointAcces point) {
        this.point = point;
        this.etat = point.corps() != null ? lire(point.corps()) : Etat.DEFAUT;
    }

    /** Un cycle de lecture ; faux si le point d'accès a échoué (la dernière copie reste en place). */
    boolean rafraichir() {
        PointAcces.Resultat r = point.rafraichir();
        if (r == PointAcces.Resultat.NOUVEAU) {
            Etat nouveau = lire(point.corps());
            Etat ancien = etat;
            etat = nouveau;
            if (!nouveau.equals(ancien)) {
                LOG.info("Annonces : config distante changée (ouvert={}, crack={}, discord={})",
                        nouveau.ouvert(), nouveau.crack(), nouveau.discordActif());
                for (Runnable rp : rappels) {
                    try { rp.run(); } catch (Throwable t) { LOG.warn("Annonces : rappel de config en échec : {}", t.toString()); }
                }
            }
        }
        return r != PointAcces.Resultat.ECHEC;
    }

    Etat etat() { return etat; }

    // ── SourceEtatPokenyle ───────────────────────────────────────────────────

    @Override public boolean ouvertPour(String pseudo) {
        Etat e = etat;
        if (e.ouvert()) return true;
        if (pseudo == null || pseudo.isBlank()) return false;
        Set<String> testeurs = e.testeurs() != null ? e.testeurs() : TESTEURS_REPLI;
        return testeurs.contains(pseudo.trim().toLowerCase(Locale.ROOT));
    }

    @Override public Optional<ZonedDateTime> ouverture() { return Optional.ofNullable(etat.ouverture()); }
    @Override public String libelleOuverture() { return etat.libelle(); }
    @Override public boolean crackAutorise() { return etat.crack(); }
    @Override public String messageCrack() { return etat.messageCrack(); }
    @Override public boolean discordActif() { return etat.discordActif(); }
    @Override public String discordUrl() { return etat.discordUrl(); }
    @Override public void surChangement(Runnable rappel) { if (rappel != null) rappels.add(rappel); }

    // ── Lecture tolérante ────────────────────────────────────────────────────

    static Etat lire(JsonObject racine) {
        JsonObject c = racine.has("config") && racine.get("config").isJsonObject()
                ? racine.getAsJsonObject("config") : new JsonObject();
        return new Etat(
                date(c.get("pokenyleOuverture")),
                texte(c.get("pokenyleOuvertureLibelle"), 40, LIBELLE_DEFAUT),
                bool(c.get("pokenyleOuvert"), false),
                bool(c.get("pokenyleCrack"), false),
                texte(c.get("messageCrackPokenyle"), 200, MESSAGE_CRACK_DEFAUT),
                bool(c.get("discordActif"), false),
                discord(c.get("discordUrl")),
                testeurs(c.get("testeursPokenyle")));
    }

    private static ZonedDateTime date(JsonElement e) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) return null;
        try { return OffsetDateTime.parse(e.getAsString()).atZoneSameInstant(PARIS); }
        catch (RuntimeException ex) { return null; }
    }

    private static boolean bool(JsonElement e, boolean defaut) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : defaut;
    }

    private static String texte(JsonElement e, int max, String defaut) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) return defaut;
        String t = Annonce.texteBrut(e.getAsString(), max);
        return t.isEmpty() ? defaut : t;
    }

    private static String discord(JsonElement e) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) return DISCORD_DEFAUT;
        String u = e.getAsString().trim();
        return u.startsWith("https://discord.gg/") || u.startsWith("https://discord.com/invite/") ? u : DISCORD_DEFAUT;
    }

    /** Liste de pseudos en minuscules, ou null si la clé est absente ou invalide (repli intégré). */
    private static Set<String> testeurs(JsonElement e) {
        if (e == null || !e.isJsonArray()) return null;
        JsonArray a = e.getAsJsonArray();
        Set<String> s = new HashSet<>();
        for (JsonElement x : a) {
            if (x.isJsonPrimitive() && x.getAsJsonPrimitive().isString()) {
                String p = x.getAsString().trim().toLowerCase(Locale.ROOT);
                if (!p.isEmpty()) s.add(p);
            }
        }
        return Set.copyOf(s);
    }
}
