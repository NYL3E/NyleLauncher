package fr.nylerp.launcher.annonces;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Les annonces déjà montrées, par id ({@code state/annonces-vues.json} : {@code { id: date }}).
 * Un id montré ne revient jamais, même modifié au Studio. Les entrées de plus de 180 jours sont
 * oubliées au chargement : le fichier ne grossit pas sans fin.
 */
final class VuesAnnonces {

    private static final Logger LOG = LoggerFactory.getLogger(VuesAnnonces.class);
    static final long OUBLI_JOURS = 180;

    private final Path fichier;
    private Map<String, String> vues;

    VuesAnnonces(Path fichier) { this.fichier = fichier; }

    synchronized boolean vue(String id) {
        return charger().containsKey(id);
    }

    synchronized void marquer(String id) {
        charger().put(id, Instant.now().toString());
        sauver();
    }

    private Map<String, String> charger() {
        if (vues != null) return vues;
        vues = new LinkedHashMap<>();
        try {
            if (Files.isRegularFile(fichier)) {
                JsonObject o = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
                Instant limite = Instant.now().minus(OUBLI_JOURS, ChronoUnit.DAYS);
                boolean purge = false;
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    String quand = e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : null;
                    Instant t = lireInstant(quand);
                    if (t != null && t.isBefore(limite)) { purge = true; continue; }
                    vues.put(e.getKey(), quand != null ? quand : Instant.now().toString());
                }
                if (purge) sauver();
            }
        } catch (Exception e) {
            // Fichier abîmé : on repart vide plutôt que de bloquer les annonces. Au pire, une annonce
            // encore en cours se remontre une fois.
            LOG.warn("Annonces : annonces-vues.json illisible, remis à zéro : {}", e.toString());
        }
        return vues;
    }

    private void sauver() {
        try {
            JsonObject o = new JsonObject();
            vues.forEach(o::addProperty);
            Files.createDirectories(fichier.getParent());
            Path tmp = fichier.resolveSibling(fichier.getFileName() + ".tmp");
            Files.writeString(tmp, o.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            LOG.warn("Annonces : annonces-vues.json non écrit : {}", e.toString());
        }
    }

    private static Instant lireInstant(String s) {
        try { return s == null ? null : Instant.parse(s); } catch (RuntimeException e) { return null; }
    }
}
