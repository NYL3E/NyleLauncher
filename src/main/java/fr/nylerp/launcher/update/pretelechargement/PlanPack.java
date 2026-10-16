package fr.nylerp.launcher.update.pretelechargement;

import com.google.gson.Gson;
import fr.nylerp.launcher.config.AppPaths;
import fr.nylerp.launcher.config.ModeDeJeu;
import fr.nylerp.launcher.update.Manifest;
import fr.nylerp.launcher.update.ManifestEntry;
import fr.nylerp.launcher.util.Downloader;
import fr.nylerp.launcher.util.Hashing;
import fr.nylerp.launcher.util.LectureTexte;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Ce que le prochain « Jouer » d'un univers devra télécharger, et donc ce qu'on peut aller chercher
 * à l'avance.
 *
 * <p>Un univers = un {@link ModeDeJeu} : son pack (tag de release, qui suit déjà le canal PROD/DEV),
 * son dossier de jeu, son manifeste en cache. On les lit sur le mode DEMANDÉ, jamais sur le mode
 * courant : pré-télécharger Pokényle ne doit pas faire basculer le launcher sur Pokényle.
 *
 * <p>Est considéré comme déjà installé un fichier que le manifeste appliqué la dernière fois liste
 * avec la même empreinte ET qui est sur le disque à la bonne taille (pas de re-hachage d'un
 * gigaoctet en fond). Le reste est à télécharger, sauf s'il attend déjà dans la {@link Reserve}.
 */
final class PlanPack {

    private static final Gson GSON = new Gson();

    /** Tests uniquement : base des manifestes ({@code <base><packTag>/manifest.json}). */
    static volatile String baseManifestesTest = null;

    record Element(String sha, String url, long taille, String chemin) { }

    /**
     * @param aTelecharger fichiers absents de la réserve et non installés
     * @param octetsTotal  tout ce que le prochain « Jouer » doit apporter (réserve comprise)
     * @param octetsDeja   la part qui attend déjà dans la réserve
     * @param utiles       empreintes que ce pack pourra encore prendre dans la réserve
     */
    record Resultat(String version, List<Element> aTelecharger, long octetsTotal, long octetsDeja, Set<String> utiles) {
        boolean rienATelecharger() { return aTelecharger.isEmpty(); }
    }

    final String univers;
    final ModeDeJeu mode;

    PlanPack(String univers) {
        this.univers = univers;
        this.mode = mode(univers);
    }

    static ModeDeJeu mode(String univers) {
        if (univers != null) {
            for (ModeDeJeu m : ModeDeJeu.values()) {
                if (m.id.equalsIgnoreCase(univers.trim())) return m;
            }
        }
        throw new IllegalArgumentException("univers inconnu : " + univers);
    }

    String urlManifeste() {
        String base = baseManifestesTest != null ? baseManifestesTest
                : "https://github.com/NYL3E/NyleLauncher/releases/download/";
        return base + mode.packTag + "/manifest.json?t=" + System.currentTimeMillis();
    }

    /** Même règle que {@code AppPaths.manifestCache()}, mais pour le mode demandé (test : identité). */
    Path cacheManifeste() {
        return AppPaths.launcherState().resolve(mode == ModeDeJeu.NYLERP ? "manifest.json"
                                                                         : "manifest-" + mode.id + ".json");
    }

    /** Même règle que {@code AppPaths.gameDir()}, pour le mode demandé, sans créer le dossier. */
    Path dossierJeu() {
        return AppPaths.rootDir().resolve(mode.dossier);
    }

    /** Vrai si cet univers a déjà été synchronisé au moins une fois (joué ou préparé). */
    boolean dejaJoue() {
        return Files.exists(cacheManifeste());
    }

    Resultat evaluer() throws IOException {
        Manifest distant = GSON.fromJson(Downloader.toStringPatient(urlManifeste(), 45, 3), Manifest.class);
        if (distant == null || distant.version == null || distant.files == null) {
            throw new IOException("manifeste " + univers + " invalide");
        }
        Manifest local = lireCache();
        Map<String, String> installe = new HashMap<>();
        if (local != null && local.files != null) {
            for (ManifestEntry e : local.files) {
                if (e.path != null && e.sha256 != null) installe.put(e.path, e.sha256.toLowerCase(Locale.ROOT));
            }
        }
        Path jeu = dossierJeu();
        List<Element> aTelecharger = new ArrayList<>();
        Set<String> vus = new HashSet<>();
        long total = 0;
        long deja = 0;
        for (ManifestEntry e : distant.files) {
            if (e.path == null || e.url == null || !Reserve.valide(e.sha256)) continue;
            String sha = e.sha256.toLowerCase(Locale.ROOT);
            Path fichier = jeu.resolve(e.path).normalize();
            if (!fichier.startsWith(jeu)) continue;  // chemin hors du dossier de jeu : jamais
            if (e.firstInstallOnly && Files.exists(fichier)) continue;
            if (estInstalle(fichier, e, sha, installe.get(e.path))) continue;
            if (!vus.add(sha)) continue;
            long taille = Math.max(0, e.size);
            total += taille;
            if (Reserve.contient(sha)) {
                deja += taille;
            } else {
                aTelecharger.add(new Element(sha, e.url, taille, e.path));
            }
        }
        return new Resultat(distant.version, Collections.unmodifiableList(aTelecharger), total, deja,
                Collections.unmodifiableSet(vus));
    }

    private static boolean estInstalle(Path fichier, ManifestEntry e, String sha, String shaCache) {
        try {
            if (!Files.isRegularFile(fichier)) return false;
            if (e.size > 0 && Files.size(fichier) != e.size) return false;
            if (shaCache != null) return shaCache.equals(sha);
            // Pas d'empreinte connue (univers jamais synchronisé) : on hache, ça n'arrive qu'aux
            // fichiers déjà sur le disque à la bonne taille, donc presque jamais.
            return sha.equalsIgnoreCase(Hashing.sha256(fichier));
        } catch (IOException ex) {
            return false;
        }
    }

    private Manifest lireCache() {
        try {
            Path cache = cacheManifeste();
            if (!Files.exists(cache)) return null;
            return GSON.fromJson(LectureTexte.lire(cache), Manifest.class);
        } catch (Exception e) {
            return null;  // cache illisible : tout sera vérifié comme pour une première installation
        }
    }

    @Override
    public String toString() {
        return "PlanPack[" + univers + ", " + Objects.toString(mode.packTag) + "]";
    }
}
