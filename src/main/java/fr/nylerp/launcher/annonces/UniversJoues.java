package fr.nylerp.launcher.annonces;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import fr.nylerp.launcher.config.ModeDeJeu;
import fr.nylerp.launcher.config.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Les univers que ce joueur a déjà ouverts ou lancés, pour le ciblage des annonces (contrat L7,
 * règle 4). Quatre signaux, réunis :
 * <ul>
 *   <li>la liste tenue par le launcher ({@code state/univers-joues.json}, alimentée par
 *       {@link Annonces#universOuvert(String)}) ;</li>
 *   <li>{@code Settings.universVu} (dernier univers ouvert dans la nouvelle interface) ;</li>
 *   <li>{@code Settings.modeDeJeu} quand il vaut autre chose que le défaut historique « nylerp » —
 *       le défaut seul ne prouve rien, tout launcher neuf l'a ;</li>
 *   <li>une instance de jeu déjà installée ({@code game/}, {@code game-pokenyle/}) : c'est ainsi
 *       que les joueurs NyleRP d'avant la refonte sont reconnus.</li>
 * </ul>
 */
final class UniversJoues {

    private static final Logger LOG = LoggerFactory.getLogger(UniversJoues.class);

    private final Path racine;
    private final Path fichier;

    UniversJoues(Path racine, Path fichier) {
        this.racine = racine;
        this.fichier = fichier;
    }

    synchronized Set<String> lire() {
        Set<String> s = new TreeSet<>(liste());
        try {
            Settings r = Settings.get();
            if (r.universVu != null && !r.universVu.isBlank()) s.add(r.universVu.trim().toLowerCase(Locale.ROOT));
            if (r.modeDeJeu != null && !r.modeDeJeu.isBlank() && !"nylerp".equalsIgnoreCase(r.modeDeJeu)) {
                s.add(r.modeDeJeu.trim().toLowerCase(Locale.ROOT));
            }
        } catch (Throwable ignored) { }
        for (ModeDeJeu m : ModeDeJeu.values()) {
            Path dossier = racine.resolve(m.dossier);
            if (Files.isDirectory(dossier.resolve("mods")) || Files.isRegularFile(dossier.resolve("options.txt"))) {
                s.add(m.id);
            }
        }
        return s;
    }

    synchronized void marquer(String univers) {
        if (univers == null || univers.isBlank()) return;
        Set<String> s = new TreeSet<>(liste());
        if (!s.add(univers.trim().toLowerCase(Locale.ROOT))) return;
        try {
            JsonArray a = new JsonArray();
            s.forEach(a::add);
            Files.createDirectories(fichier.getParent());
            Path tmp = fichier.resolveSibling(fichier.getFileName() + ".tmp");
            Files.writeString(tmp, a.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            LOG.warn("Annonces : univers-joues.json non écrit : {}", e.toString());
        }
    }

    private Set<String> liste() {
        Set<String> s = new TreeSet<>();
        try {
            if (Files.isRegularFile(fichier)) {
                for (JsonElement e : JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonArray()) {
                    if (e.isJsonPrimitive()) s.add(e.getAsString().trim().toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception e) {
            LOG.warn("Annonces : univers-joues.json illisible : {}", e.toString());
        }
        return s;
    }
}
