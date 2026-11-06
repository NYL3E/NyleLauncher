package fr.nylerp.launcher.launch;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache de SHA-1 par (taille, date de modification) pour les fichiers de Minecraft vanilla (jar du client, bibliothèques,
 * index et objets d'assets). MojangInstaller re-hachait TOUT à chaque « Jouer » : environ 3 900 fichiers, 1,1 à 1,8 s
 * mesurés dans launcher.log (06/11/2026), et bien plus sur un disque lent. Même principe que update/HashCache pour le
 * pack : un fichier dont la taille ET la date n'ont pas bougé depuis le dernier hachage garde son SHA-1 ; toute
 * modification (troncature = autre taille, réécriture = autre date) force un nouveau hachage. Le cache est jetable :
 * illisible ou absent, on re-hache tout, comme avant.
 */
final class Sha1Cache {

    private static final Gson GSON = new Gson();

    private record Entree(long taille, long date, String sha1) {}

    private final Map<String, Entree> map;
    private volatile boolean modifie;

    private Sha1Cache(Map<String, Entree> map) { this.map = map; }

    static Sha1Cache charger(Path fichier) {
        try {
            if (Files.exists(fichier)) {
                Map<String, Entree> m = GSON.fromJson(Files.readString(fichier),
                        new TypeToken<ConcurrentHashMap<String, Entree>>() {}.getType());
                if (m != null) return new Sha1Cache(m);
            }
        } catch (Exception ignored) {
            // illisible : on repart de zéro (re-hachage complet, aucun risque)
        }
        return new Sha1Cache(new ConcurrentHashMap<>());
    }

    /** true si le fichier existe et que son SHA-1 vaut {@code attendu} (depuis le cache quand taille et date n'ont pas bougé). */
    boolean conforme(Path f, String attendu, java.util.function.Function<Path, String> hacher) {
        if (attendu == null) return Files.exists(f);
        try {
            BasicFileAttributes a = Files.readAttributes(f, BasicFileAttributes.class);
            String cle = f.toAbsolutePath().toString();
            Entree e = map.get(cle);
            long date = a.lastModifiedTime().toMillis();
            if (e != null && e.taille() == a.size() && e.date() == date) return e.sha1().equalsIgnoreCase(attendu);
            String sha1 = hacher.apply(f);
            if (sha1 == null) return false;
            map.put(cle, new Entree(a.size(), date, sha1));
            modifie = true;
            return sha1.equalsIgnoreCase(attendu);
        } catch (Exception absentOuIllisible) {
            return false;
        }
    }

    void enregistrer(Path fichier) {
        if (!modifie) return;
        try {
            Files.createDirectories(fichier.getParent());
            Path tmp = fichier.resolveSibling(fichier.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(map));
            Files.move(tmp, fichier, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            modifie = false;
        } catch (Exception ignored) {
            // au pire : re-hachage au prochain lancement
        }
    }
}
