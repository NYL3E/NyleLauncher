package fr.nylerp.launcher.update;

import fr.nylerp.launcher.config.AppPaths;
import javafx.scene.image.Image;

import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Les médias du launcher hors du jar : façade de production de {@link MediasCache}.
 *
 * <p>Le paquet est une release GitHub {@code medias-<VERSION>} du dépôt NyleLauncher : un
 * {@code medias.json} (chemin logique → SHA-256, taille, nom d'asset) et les fichiers. Le payload
 * épingle la {@link #VERSION} qu'il attend : un payload et ses médias restent cohérents, et changer
 * un média = publier {@code medias-<N+1>} ({@code scripts/publier-medias.py}) puis monter cette
 * constante dans le payload suivant.
 *
 * <h2>Usage</h2>
 * <pre>
 *   Medias.demarrer();                                  // au démarrage, normal ou --fond (idempotent)
 *   Image fond = Medias.image(CheminsMedias.POKENYLE_FOND_2X);   // null tant qu'absent
 *   String url = Medias.urlOuClasspath(CheminsMedias.NYLERP_VIDEO_1, "/media/videolauncher1.mp4");
 *   Medias.quandDisponible(CheminsMedias.NYLERP_MUSIQUE, p -> Platform.runLater(...));
 * </pre>
 * Aucune de ces méthodes ne fait de réseau sur le fil appelant ni ne lève d'exception : un média
 * absent vaut {@code null}, et l'interface se dessine sans lui.
 */
public final class Medias {

    /** Version du paquet de médias attendue par CE payload (release {@code medias-<VERSION>}). */
    public static final String VERSION = "1";

    /** Dépôt qui héberge les releases de médias. */
    static final String DEPOT = "NYL3E/NyleLauncher";

    private static volatile MediasCache cache;
    private static final AtomicBoolean demarre = new AtomicBoolean();

    /** URL du manifeste distant. Surchargeable par {@code -Dnyle.medias.manifest=…} (tests, hors ligne simulé). */
    static String urlManifeste() {
        String force = System.getProperty("nyle.medias.manifest");
        if (force != null && !force.isBlank()) return force;
        return "https://github.com/" + DEPOT + "/releases/download/medias-" + VERSION + "/" + MediasCache.NOM_MANIFESTE;
    }

    /** Le cache du processus, créé au premier usage avec le manifeste local déjà chargé. */
    public static MediasCache cache() {
        MediasCache c = cache;
        if (c != null) return c;
        synchronized (Medias.class) {
            if (cache == null) {
                MediasCache n = new MediasCache(AppPaths.rootDir().resolve("medias"), urlManifeste(), 3);
                n.chargerLocal();
                cache = n;
            }
            return cache;
        }
    }

    /**
     * Lance la synchronisation en fond, une fois par processus, puis la REPLANIFIE : après un échec
     * (hors ligne au démarrage, CDN malade) à 1, 5, 15 puis 30 minutes ; une fois à jour, toutes les
     * 6 heures. Indispensable depuis le mode {@code --fond} : le launcher peut rester des jours dans
     * la barre système sans redémarrer, et un démarrage de session hors ligne ne doit pas le priver
     * de ses médias jusqu'au prochain redémarrage. À appeler au démarrage normal ET en {@code --fond}
     * ; les accesseurs l'appellent aussi au besoin.
     */
    public static void demarrer() {
        if (demarre.compareAndSet(false, true)) passe(0);
    }

    /** Relance une synchronisation tout de suite (ex. retour du réseau, config distante). */
    public static void resynchroniser() {
        demarrer();
        cache().synchroniser();
    }

    /** Délais de reprise après échec, en millisecondes (modifiable par les tests). */
    static long[] reprisesMs = {60_000L, 5 * 60_000L, 15 * 60_000L, 30 * 60_000L};
    /** Délai entre deux vérifications une fois à jour. */
    static long rafraichissementMs = 6 * 3_600_000L;

    private static final java.util.concurrent.ScheduledExecutorService PLANIF =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "medias-planif");
                t.setDaemon(true);
                return t;
            });

    private static void passe(int echecs) {
        cache().synchroniser().whenComplete((ok, t) -> {
            boolean reussi = Boolean.TRUE.equals(ok);
            long delai = reussi ? rafraichissementMs : reprisesMs[Math.min(echecs, reprisesMs.length - 1)];
            try {
                PLANIF.schedule(() -> passe(reussi ? 0 : echecs + 1), delai, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
                // arrêt en cours
            }
        });
    }

    /** Pour les tests : oublie le cache et l'état du processus. */
    static synchronized void reinitialiserPourTests() {
        MediasCache c = cache;
        if (c != null) c.fermer();
        cache = null;
        demarre.set(false);
    }

    /** Fichier en cache du média, ou {@code null}. */
    public static Path fichier(String chemin) {
        demarrer();
        return cache().fichier(chemin);
    }

    /** URL {@code file:} du média en cache, ou {@code null}. */
    public static String url(String chemin) {
        Path p = fichier(chemin);
        return p == null ? null : p.toUri().toString();
    }

    /**
     * Image JavaFX du média, ou {@code null} s'il est absent ou indécodable. Chargement synchrone :
     * à appeler une fois et garder l'image (pas à chaque image d'animation).
     */
    public static Image image(String chemin) {
        String u = url(chemin);
        if (u == null) return null;
        try {
            Image img = new Image(u);
            return img.isError() ? null : img;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Adaptateur pour les écrans qui chargeaient un média depuis le classpath : le média du paquet
     * s'il est en cache (il REMPLACE alors celui du jar), sinon la ressource du jar, sinon
     * {@code null} (ressource retirée du jar et pas encore téléchargée).
     */
    public static String urlOuClasspath(String chemin, String ressourceClasspath) {
        String u = url(chemin);
        if (u != null) return u;
        if (ressourceClasspath == null) return null;
        URL r = Medias.class.getResource(ressourceClasspath);
        return r == null ? null : r.toExternalForm();
    }

    /**
     * Appelle {@code rappel} dès que le média est en cache : tout de suite s'il y est déjà, sinon
     * depuis un fil de fond à la fin du téléchargement (l'appelant fait son {@code Platform.runLater}).
     */
    public static void quandDisponible(String chemin, Consumer<Path> rappel) {
        demarrer();
        cache().quandDisponible(chemin, rappel);
    }

    /** Une ligne d'état pour le diagnostic (réglages, journaux). */
    public static String etat() {
        MediasCache c = cache;
        return c == null ? "médias : non initialisés" : "médias " + c.version() + " : " + c.dernierEtat();
    }

    private Medias() {}
}
