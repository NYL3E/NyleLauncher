package fr.nylerp.launcher.update.pretelechargement;

import fr.nylerp.launcher.config.AppPaths;
import fr.nylerp.launcher.util.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * La réserve du pré-téléchargement : les fichiers du pack déjà téléchargés, rangés par empreinte
 * ({@code <racine>/pretelechargement/<sha256>}), en attente du prochain « Jouer ».
 *
 * <h2>Pourquoi une réserve et pas le dossier de jeu directement</h2>
 * Écrire la nouvelle version d'un mod dans {@code mods/} à côté de l'ancienne, hors synchronisation,
 * laisse deux versions du même mod si le joueur lance le jeu sur le manifeste en cache (CDN
 * injoignable) : le jeu plante au démarrage. La réserve ne touche jamais au dossier de jeu ; c'est
 * la synchronisation normale ({@code ModpackUpdater}) qui, au moment de télécharger un fichier,
 * le prend ici s'il y est ({@link #adopter}) — avec tout son traitement habituel autour (anciennes
 * versions retirées, empreinte revérifiée, cache d'empreintes, mods managés).
 *
 * <p>Un fichier n'apparaît sous son nom final qu'une fois entièrement téléchargé et vérifié : le
 * téléchargement en cours vit sous {@code <sha256>.part}.
 */
public final class Reserve {

    private static final Logger LOG = LoggerFactory.getLogger(Reserve.class);
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");

    /** Fichiers repris de la réserve par la synchronisation depuis le démarrage (journal et tests). */
    static final AtomicLong ADOPTIONS = new AtomicLong();
    /** Empreinte → dernier endroit où un fichier de la réserve a été déplacé (contenus en double). */
    private static final Map<String, Path> DERNIERE_PLACE = new ConcurrentHashMap<>();
    private static final Map<String, Object> VERROUS = new ConcurrentHashMap<>();

    private Reserve() { }

    static Path dossier() throws IOException {
        Path d = AppPaths.rootDir().resolve("pretelechargement");
        Files.createDirectories(d);
        return d;
    }

    /** Une empreinte SHA-256 en hexadécimal — tout le reste est refusé (pas de chemin forgé). */
    static boolean valide(String sha) {
        return sha != null && SHA256.matcher(sha).matches();
    }

    static Path fichier(String sha) throws IOException {
        return dossier().resolve(sha.toLowerCase(Locale.ROOT));
    }

    static Path partiel(String sha) throws IOException {
        return dossier().resolve(sha.toLowerCase(Locale.ROOT) + ".part");
    }

    static boolean contient(String sha) {
        if (!valide(sha)) return false;
        try {
            return Files.isRegularFile(fichier(sha));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Appelé par la synchronisation juste avant de télécharger un fichier du pack : si ce fichier
     * attend dans la réserve, il est revérifié puis DÉPLACÉ à sa place dans le dossier de jeu, et
     * la synchronisation n'a rien à télécharger. Un fichier de réserve abîmé est supprimé et la
     * synchronisation télécharge normalement. Ne lève jamais d'exception.
     *
     * @return vrai si {@code destination} contient maintenant le fichier attendu
     */
    public static boolean adopter(String sha256, Path destination) {
        if (!valide(sha256) || destination == null) return false;
        String cle = sha256.toLowerCase(Locale.ROOT);
        // Un même contenu peut figurer plusieurs fois dans un manifeste (options.txt et
        // options.txt.bak, servers.dat et servers.dat.bak…) : la première adoption DÉPLACE le
        // fichier, les suivantes le recopient depuis la place qu'il vient de prendre. Le verrou par
        // empreinte évite que deux fils de la synchronisation se croisent sur le même contenu.
        synchronized (VERROUS.computeIfAbsent(cle, k -> new Object())) {
            return adopterSansVerrou(cle, destination);
        }
    }

    private static boolean adopterSansVerrou(String sha256, Path destination) {
        try {
            Path f = fichier(sha256);
            if (!Files.isRegularFile(f)) return recopierDepuisDernierePlace(sha256, destination);
            String reel = Hashing.sha256(f);
            if (!sha256.equalsIgnoreCase(reel)) {
                LOG.warn("Réserve : {} abîmé (empreinte {}), supprimé — téléchargement normal", f.getFileName(), reel);
                Files.deleteIfExists(f);
                return false;
            }
            Files.createDirectories(destination.toAbsolutePath().getParent());
            try {
                Files.move(f, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(f, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            DERNIERE_PLACE.put(sha256, destination.toAbsolutePath());
            ADOPTIONS.incrementAndGet();
            LOG.info("Réserve : {} pris du pré-téléchargement (pas de téléchargement)", destination.getFileName());
            return true;
        } catch (Exception e) {
            LOG.warn("Réserve : adoption impossible pour {} : {}", destination, e.toString());
            return false;
        }
    }

    private static boolean recopierDepuisDernierePlace(String sha256, Path destination) throws IOException {
        Path source = DERNIERE_PLACE.get(sha256);
        if (source == null || source.equals(destination.toAbsolutePath()) || !Files.isRegularFile(source)) return false;
        if (!sha256.equalsIgnoreCase(Hashing.sha256(source))) return false;  // modifié depuis : on télécharge
        Files.createDirectories(destination.toAbsolutePath().getParent());
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        ADOPTIONS.incrementAndGet();
        LOG.info("Réserve : {} recopié depuis {} (même contenu, pas de téléchargement)",
                destination.getFileName(), source.getFileName());
        return true;
    }

    /**
     * Supprime ce qui ne sert plus : fichiers terminés dont l'empreinte n'est dans aucun pack
     * actuel, et téléchargements partiels inutiles ou abandonnés depuis plus de trois jours.
     */
    static void purger(Set<String> utiles) {
        Instant limite = Instant.now().minus(Duration.ofDays(3));
        try (Stream<Path> liste = Files.list(dossier())) {
            liste.filter(Files::isRegularFile).forEach(p -> {
                String nom = p.getFileName().toString();
                boolean partielle = nom.endsWith(".part");
                String sha = partielle ? nom.substring(0, nom.length() - 5) : nom;
                try {
                    boolean inutile = !valide(sha) || !utiles.contains(sha.toLowerCase(Locale.ROOT));
                    boolean abandonne = partielle && Files.getLastModifiedTime(p).toInstant().isBefore(limite);
                    if (inutile || abandonne) {
                        Files.deleteIfExists(p);
                        LOG.info("Réserve : {} supprimé ({})", nom, inutile ? "plus dans aucun pack" : "abandonné");
                    }
                } catch (IOException e) {
                    LOG.warn("Réserve : nettoyage de {} impossible : {}", nom, e.toString());
                }
            });
        } catch (IOException e) {
            LOG.warn("Réserve : nettoyage impossible : {}", e.toString());
        }
    }
}
