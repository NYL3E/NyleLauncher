package fr.nylerp.launcher.annonces;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.OffsetDateTime;

/**
 * Un point d'accès lu par cycles : ETag, copie locale, recalage de l'heure du serveur.
 *
 * <p>Règle du contrat : seule une réponse 200 au corps JSON valide remplace la copie ; un 304 la
 * confirme ; tout le reste (502, 503, délai dépassé, corps illisible) laisse la dernière copie
 * valide en place et compte comme un échec.
 */
final class PointAcces {

    private static final Logger LOG = LoggerFactory.getLogger(PointAcces.class);

    enum Resultat { NOUVEAU, INCHANGE, ECHEC }

    private final String nom;
    private final URI uri;
    private final ClientLecture client;
    private final CopieLocale copie;
    private final HorlogeServeur horloge;
    private volatile String etag;
    private volatile JsonObject corps;

    PointAcces(String nom, URI uri, ClientLecture client, CopieLocale copie, HorlogeServeur horloge) {
        this.nom = nom;
        this.uri = uri;
        this.client = client;
        this.copie = copie;
        this.horloge = horloge;
        CopieLocale.Copie c = copie.lire();
        if (c != null) {
            JsonObject o = analyser(c.corps());
            if (o != null) {
                corps = o;
                etag = c.etag();
            }
        }
    }

    /** Dernier corps valide (réseau ou copie locale), ou null s'il n'y en a jamais eu. */
    JsonObject corps() { return corps; }

    Resultat rafraichir() {
        try {
            ClientLecture.Reponse r = client.lire(uri, corps != null ? etag : null);
            r.dateServeur().ifPresent(horloge::recaler);
            if (r.statut() == 304 && corps != null) return Resultat.INCHANGE;
            if (r.statut() != 200 || r.corps() == null) {
                LOG.info("Annonces : {} a répondu {} — dernière copie gardée", nom, r.statut());
                return Resultat.ECHEC;
            }
            JsonObject o = analyser(r.corps());
            if (o == null) {
                LOG.warn("Annonces : {} a renvoyé un corps illisible — dernière copie gardée", nom);
                return Resultat.ECHEC;
            }
            if (r.dateServeur().isEmpty()) horlogeDepuisCorps(o);
            corps = o;
            etag = r.etag();
            copie.ecrire(r.etag(), r.corps());
            return Resultat.NOUVEAU;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Resultat.ECHEC;
        } catch (Exception e) {
            LOG.info("Annonces : {} injoignable ({}) — dernière copie gardée", nom, e.toString());
            return Resultat.ECHEC;
        }
    }

    private void horlogeDepuisCorps(JsonObject o) {
        try {
            JsonElement m = o.get("maintenant");
            if (m != null && m.isJsonPrimitive()) horloge.recaler(OffsetDateTime.parse(m.getAsString()));
        } catch (RuntimeException ignored) { }
    }

    /** Objet JSON de version {@code v >= 1}, ou null. */
    private static JsonObject analyser(String texte) {
        try {
            JsonElement e = JsonParser.parseString(texte);
            if (!e.isJsonObject()) return null;
            JsonObject o = e.getAsJsonObject();
            JsonElement v = o.get("v");
            if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber() || v.getAsInt() < 1) return null;
            return o;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
