package fr.nylerp.launcher.update;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.nylerp.launcher.util.Hashing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache de SHA-256 par (taille, mtime) — le « Jouer » vérifiait l'intégrité des 1171 fichiers du
 * pack (~1,2 Go) en les RE-HACHANT tous à chaque clic : ~30 s de disque/CPU avant même de lancer
 * le jeu (mesuré le 2026-07-11). Un fichier dont la taille ET la date de modification n'ont pas
 * bougé depuis le dernier hachage garde son SHA — la passe complète tombe à ~1 s. Toute
 * modification (taille ou mtime différents) force un re-hachage : un fichier corrompu par
 * troncature change de taille, un fichier réécrit change de mtime. Le cache lui-même est
 * jetable/reconstructible : corrompu ou absent → on re-hache tout, comme avant.
 */
final class HashCache {

    private static final Gson GSON = new Gson();

    /** Clé = chemin absolu ; valeur = [taille, mtimeMillis, sha256]. */
    private record Entry(long size, long mtime, String sha) {}

    private final Map<String, Entry> map;
    private boolean dirty = false;

    private HashCache(Map<String, Entry> map) { this.map = map; }

    static HashCache load(Path file) {
        try {
            if (Files.exists(file)) {
                Map<String, Entry> m = GSON.fromJson(fr.nylerp.launcher.util.LectureTexte.lire(file),
                        new TypeToken<ConcurrentHashMap<String, Entry>>(){}.getType());
                if (m != null) return new HashCache(m);
            }
        } catch (Exception ignored) {
            // cache illisible → on repart de zéro (simple re-hachage complet, aucun risque)
        }
        return new HashCache(new ConcurrentHashMap<>());
    }

    /** SHA-256 du fichier : depuis le cache si (taille, mtime) inchangés, sinon re-haché + mémorisé.
     *  Retourne null si le fichier n'existe pas (même contrat que {@link Hashing#sha256}). */
    String sha(Path file) throws IOException {
        if (!Files.exists(file)) return null;
        BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
        String key = file.toAbsolutePath().toString();
        Entry e = map.get(key);
        if (e != null && e.size() == attr.size() && e.mtime() == attr.lastModifiedTime().toMillis()) {
            return e.sha();
        }
        String sha = Hashing.sha256(file);
        if (sha != null) {
            map.put(key, new Entry(attr.size(), attr.lastModifiedTime().toMillis(), sha));
            dirty = true;
        }
        return sha;
    }

    /** Enregistre le SHA d'un fichier qu'on vient d'écrire (post-téléchargement vérifié). */
    void put(Path file, String sha) {
        try {
            BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
            map.put(file.toAbsolutePath().toString(),
                    new Entry(attr.size(), attr.lastModifiedTime().toMillis(), sha));
            dirty = true;
        } catch (Exception ignored) { /* au pire : re-hachage au prochain sync */ }
    }

    void save(Path file) {
        if (!dirty) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(map));
            dirty = false;
        } catch (Exception ignored) { /* au pire : re-hachage au prochain sync */ }
    }
}
