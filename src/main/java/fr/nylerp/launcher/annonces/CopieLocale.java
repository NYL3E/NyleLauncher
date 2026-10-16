package fr.nylerp.launcher.annonces;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/**
 * Dernière réponse valide d'un point d'accès, gardée sur disque avec son ETag pour travailler hors
 * ligne ({@code state/config-cache.json}, {@code state/annonces-cache.json}). Écriture atomique :
 * un launcher tué en pleine écriture ne laisse jamais un fichier tronqué.
 */
final class CopieLocale {

    private static final Logger LOG = LoggerFactory.getLogger(CopieLocale.class);

    record Copie(String etag, String corps) { }

    private final Path fichier;

    CopieLocale(Path fichier) { this.fichier = fichier; }

    Copie lire() {
        try {
            if (!Files.isRegularFile(fichier)) return null;
            JsonObject o = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!o.has("corps") || !o.get("corps").isJsonPrimitive()) return null;
            String etag = o.has("etag") && o.get("etag").isJsonPrimitive() ? o.get("etag").getAsString() : null;
            return new Copie(etag, o.get("corps").getAsString());
        } catch (Exception e) {
            LOG.warn("Annonces : copie locale illisible ({}) : {}", fichier.getFileName(), e.toString());
            return null;
        }
    }

    void ecrire(String etag, String corps) {
        try {
            JsonObject o = new JsonObject();
            if (etag != null) o.addProperty("etag", etag);
            o.addProperty("recu", Instant.now().toString());
            o.addProperty("corps", corps);
            Files.createDirectories(fichier.getParent());
            Path tmp = fichier.resolveSibling(fichier.getFileName() + ".tmp");
            Files.writeString(tmp, o.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            LOG.warn("Annonces : copie locale non écrite ({}) : {}", fichier.getFileName(), e.toString());
        }
    }
}
