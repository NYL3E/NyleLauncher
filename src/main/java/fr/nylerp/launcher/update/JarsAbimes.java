package fr.nylerp.launcher.update;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Retire de mods/ les jars qui ne s'ouvrent pas comme une archive (téléchargement coupé, disque plein, antivirus) : Fabric refuse de
 * démarrer sur un seul d'entre eux (« zip END header not found »). Un jar retiré est absent du disque, donc la passe de
 * synchronisation qui suit le retélécharge toute seule. Ne touche que les .jar ; une archive saine n'est jamais modifiée.
 *
 * <p>Depuis le 14/11/2026 : un jar déjà ouvert avec succès n'est plus rouvert tant que sa TAILLE et sa DATE n'ont pas bougé
 * ({@code state/jars-sains.json}). Ouvrir les ~280 jars du pack à chaque « Jouer » ne coûtait presque rien sur un SSD seul, mais
 * chaque ouverture réveille l'antivirus sous Windows (lancement à 181 s chez l'owner le 06/11, analyse des jars en cause). La
 * détection n'est pas affaiblie : un jar tronqué après coup change de taille, un jar réécrit change de date, et tous deux sont
 * rouverts ; un cache absent ou illisible fait tout rouvrir, comme avant.
 */
final class JarsAbimes {

    private static final Logger LOG = LoggerFactory.getLogger(JarsAbimes.class);
    private static final Gson GSON = new Gson();

    private JarsAbimes() {}

    /** @return le nombre de jars abîmés supprimés. Sans mémoire : chaque jar est rouvert (tests, appelants historiques). */
    static int purger(Path modsDir) {
        return purger(modsDir, null);
    }

    /** @param memoire fichier « jars sains » (chemin → taille, date), ou {@code null} pour tout rouvrir. */
    static int purger(Path modsDir, Path memoire) {
        if (!Files.isDirectory(modsDir)) return 0;
        Map<String, long[]> sains = lire(memoire);
        Map<String, long[]> vus = new HashMap<>();
        int n = 0;
        try (Stream<Path> list = Files.list(modsDir)) {
            for (Path p : (Iterable<Path>) list.filter(Files::isRegularFile)
                    .filter(x -> x.getFileName().toString().endsWith(".jar"))::iterator) {
                String cle = p.toAbsolutePath().toString();
                long[] etat = etat(p);
                long[] connu = sains.get(cle);
                if (etat != null && connu != null && connu.length == 2 && connu[0] == etat[0] && connu[1] == etat[1]) {
                    vus.put(cle, etat);   // inchangé depuis sa dernière ouverture réussie
                    continue;
                }
                if (ouvrable(p)) {
                    if (etat != null) vus.put(cle, etat);
                    continue;
                }
                try {
                    Files.deleteIfExists(p);
                    n++;
                    LOG.warn("Jar abîmé retiré (sera retéléchargé) : {}", p.getFileName());
                } catch (IOException e) {
                    LOG.warn("Jar abîmé non supprimable : {} ({})", p.getFileName(), e.toString());
                }
            }
        } catch (IOException e) {
            LOG.warn("Contrôle des jars impossible : {}", e.toString());
            return n;
        }
        if (memoire != null && !vus.equals(sains)) ecrire(memoire, vus);
        return n;
    }

    static boolean ouvrable(Path jar) {
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(jar.toFile())) {
            return z.size() >= 0;
        } catch (IOException e) {
            return false;
        }
    }

    /** {taille, date de modification} ou {@code null} si le fichier est illisible. */
    private static long[] etat(Path p) {
        try {
            BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class);
            return new long[]{a.size(), a.lastModifiedTime().toMillis()};
        } catch (IOException e) {
            return null;
        }
    }

    private static Map<String, long[]> lire(Path memoire) {
        if (memoire == null || !Files.isRegularFile(memoire)) return new HashMap<>();
        try {
            Map<String, long[]> m = GSON.fromJson(Files.readString(memoire), new TypeToken<HashMap<String, long[]>>() {}.getType());
            return m != null ? m : new HashMap<>();
        } catch (Exception illisible) {
            return new HashMap<>();   // mémoire abîmée : on rouvre tout, comme avant
        }
    }

    private static void ecrire(Path memoire, Map<String, long[]> vus) {
        try {
            Files.createDirectories(memoire.getParent());
            Path tmp = memoire.resolveSibling(memoire.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(vus));
            Files.move(tmp, memoire, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignore) {
            // au pire : tout est rouvert au prochain lancement
        }
    }
}
