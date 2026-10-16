package fr.nylerp.launcher.update;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import fr.nylerp.launcher.util.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Le moteur du paquet de médias distant : manifeste, cache adressé par contenu, téléchargements
 * en fond avec reprise. {@link Medias} en est la façade statique de production ; cette classe est
 * instanciable pour que les tests la pilotent contre un serveur local, sans toucher au vrai dossier
 * du joueur.
 *
 * <h2>Pourquoi les médias sortent du jar (refonte 2026-10)</h2>
 * Le payload pesait 76 Mo, dont 34 Mo de musique, d'images et de vidéos, retéléchargés en entier à
 * chaque version — alors qu'ils ne changent presque jamais. Ici, chaque média est téléchargé UNE
 * fois, vérifié par son SHA-256 et gardé tant que le manifeste le désigne.
 *
 * <h2>Règles de sûreté</h2>
 * <ul>
 *   <li><b>Jamais bloquant</b> : {@link #fichier} ne fait qu'une lecture d'index en mémoire ; tout
 *       le réseau est sur des fils de fond démons.</li>
 *   <li><b>Jamais fatal</b> : réseau coupé, manifeste illisible, SHA faux, disque plein — chaque cas
 *       est journalisé et le média reste simplement absent ({@code null}) ; l'interface s'affiche
 *       sans lui.</li>
 *   <li><b>Le dernier manifeste valide est gardé sur disque</b> : hors ligne, le cache sert ce qu'il
 *       a déjà.</li>
 *   <li><b>Nom de fichier = {@code <sha256>.<extension>}</b>, et non {@code <sha256>} nu comme le
 *       prévoyait le plan : JavaFX {@code Media} choisit son décodeur d'après l'extension de l'URL
 *       et refuse un fichier sans extension.</li>
 * </ul>
 */
public final class MediasCache {

    private static final Logger LOG = LoggerFactory.getLogger(MediasCache.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Nom du manifeste, sur la release comme dans le cache local. */
    public static final String NOM_MANIFESTE = "medias.json";
    /** Seul format de manifeste compris par ce code. Un format supérieur est ignoré (on garde le local). */
    public static final int FORMAT = 1;

    private static final Pattern CHEMIN = Pattern.compile("^(?!/)(?!.*\\.\\.)(?!.*//)[A-Za-z0-9._@/-]{1,200}$");
    private static final Pattern SHA    = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern ASSET  = Pattern.compile("^[A-Za-z0-9._-]{1,200}$");
    private static final Pattern EXT    = Pattern.compile("^[a-z0-9]{1,5}$");
    /** Fichiers que ce cache a écrits (et a donc le droit de ranger). */
    private static final Pattern FICHIER_CACHE = Pattern.compile("^[0-9a-f]{64}(\\.[a-z0-9]{1,5})?(\\.part)?$");

    private static final int REESSAIS = 2;
    private static final long[] ATTENTES_MS = {1_000, 3_000};

    /** Une ligne du manifeste. {@code prechargement=false} : téléchargé à la première demande seulement. */
    public record Entree(String chemin, String sha256, long taille, String asset, Boolean prechargement) {
        boolean prechargee() { return prechargement == null || prechargement; }
    }

    /** Le manifeste {@code medias.json}. {@code base} facultative : sinon, dossier du manifeste. */
    public record Manifeste(int format, String version, String base, List<Entree> fichiers) {}

    private final Path dossier;
    private final String urlManifeste;
    private final HttpClient http;
    private final ExecutorService fils;

    private volatile Map<String, Entree> index = Map.of();
    private volatile String base = "";
    private volatile String version = "";
    private volatile String dernierEtat = "jamais synchronisé";

    private final Map<String, CompletableFuture<Path>> enCours = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<Path>>> attentes = new ConcurrentHashMap<>();

    /**
     * @param dossier       dossier du cache ({@code <racine>/medias})
     * @param urlManifeste  URL du {@code medias.json} distant, SANS cache-buster
     * @param paralleles    téléchargements simultanés au plus
     */
    public MediasCache(Path dossier, String urlManifeste, int paralleles) {
        this.dossier = dossier;
        this.urlManifeste = urlManifeste;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        this.fils = Executors.newFixedThreadPool(Math.max(1, paralleles), r -> {
            Thread t = new Thread(r, "medias");
            t.setDaemon(true);                 // ne retient jamais la fermeture du launcher
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
    }

    // ── Lecture (fil appelant, jamais de réseau) ────────────────────────────────────────────

    /**
     * Le fichier en cache pour ce chemin logique, ou {@code null} s'il n'est pas (encore) là.
     * Un média « différé » absent est mis en file de téléchargement au passage.
     */
    public Path fichier(String chemin) {
        Entree e = index.get(chemin);
        if (e == null) return null;
        Path p = cible(e);
        try {
            if (Files.isRegularFile(p) && Files.size(p) == e.taille()) return p;
        } catch (IOException ignored) {
            // illisible : traité comme absent
        }
        if (!e.prechargee()) telecharger(e);
        return null;
    }

    /** {@code true} si le manifeste connu désigne ce chemin (présent en cache ou non). */
    public boolean connait(String chemin) { return index.containsKey(chemin); }

    /**
     * Appelle {@code rappel} avec le fichier dès qu'il est disponible : tout de suite (sur le fil
     * appelant) s'il est déjà en cache, sinon à la fin de son téléchargement (sur un fil de fond —
     * l'appelant repasse lui-même sur le fil JavaFX). Un chemin que le manifeste ne connaît pas
     * encore reste en attente : il sera servi si un manifeste plus récent l'apporte.
     */
    public void quandDisponible(String chemin, Consumer<Path> rappel) {
        Path p = fichier(chemin);
        if (p != null) { appeler(rappel, p); return; }
        attentes.computeIfAbsent(chemin, k -> new CopyOnWriteArrayList<>()).add(rappel);
        // Arrivé entre-temps (course entre la vérification et l'inscription) : servir maintenant.
        Path apres = fichier(chemin);
        if (apres != null) servirAttentes(chemin, apres);
    }

    public String version()     { return version; }
    public String dernierEtat() { return dernierEtat; }
    public Path dossier()       { return dossier; }

    // ── Manifeste local ─────────────────────────────────────────────────────────────────────

    /** Charge le dernier manifeste valide gardé sur disque (rapide, à faire au démarrage). */
    public void chargerLocal() {
        Path f = dossier.resolve(NOM_MANIFESTE);
        if (!Files.isRegularFile(f)) return;
        try {
            Manifeste m = lire(Files.readString(f, StandardCharsets.UTF_8));
            if (m != null) installer(m, urlManifeste);
        } catch (Exception e) {
            LOG.warn("manifeste de médias local illisible, ignoré : {}", e.toString());
        }
    }

    // ── Synchronisation (fond) ──────────────────────────────────────────────────────────────

    /**
     * Récupère le manifeste distant, l'installe s'il est valide, télécharge les médias préchargés
     * manquants, puis range le cache. Ne lève jamais : un échec réseau garde le manifeste local.
     * Le futur se termine quand tous les téléchargements lancés sont finis (réussis ou non) ; il vaut
     * {@code true} si le manifeste distant a été lu ET que tous les médias préchargés sont en cache
     * — {@code false} dit à l'appelant de réessayer plus tard (voir {@link Medias}).
     *
     * <p>Une synchronisation déjà en cours est partagée : deux passes simultanées ne doivent pas
     * installer puis ranger chacune de leur côté.
     */
    public CompletableFuture<Boolean> synchroniser() {
        synchronized (this) {
            CompletableFuture<Boolean> c = synchroEnCours;
            if (c != null && !c.isDone()) return c;
            CompletableFuture<Boolean> n = passe();
            synchroEnCours = n;
            return n;
        }
    }

    private volatile CompletableFuture<Boolean> synchroEnCours;

    private CompletableFuture<Boolean> passe() {
        return CompletableFuture.supplyAsync(this::recupererManifeste, fils)
                .<Boolean>thenCompose(m -> {
                    if (m == null) return CompletableFuture.completedFuture(false);
                    installer(m, urlManifeste);
                    enregistrer(m);
                    ranger();
                    List<CompletableFuture<Path>> lots = new ArrayList<>();
                    for (Entree e : index.values()) {
                        if (e.prechargee() && fichierPresent(e) == null) lots.add(telecharger(e));
                    }
                    // Les attentes sur des chemins déjà en cache (manifeste tout juste arrivé).
                    for (String chemin : attentes.keySet()) {
                        Path p = fichier(chemin);
                        if (p != null) servirAttentes(chemin, p);
                    }
                    return CompletableFuture.allOf(lots.toArray(new CompletableFuture[0]))
                            .handle((v, t) -> {
                                long manquants = index.values().stream()
                                        .filter(e -> e.prechargee() && fichierPresent(e) == null).count();
                                dernierEtat = manquants == 0 ? "à jour (" + version + ")"
                                        : manquants + " média(s) non téléchargé(s)";
                                LOG.info("médias : {}", dernierEtat);
                                return manquants == 0;
                            });
                })
                .exceptionally(t -> { LOG.warn("médias : synchronisation interrompue : {}", t.toString()); return false; });
    }

    private Manifeste recupererManifeste() {
        String url = urlManifeste + (urlManifeste.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "NyleLauncher/medias")
                    .GET().build();
            CompletableFuture<HttpResponse<byte[]>> f = http.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray());
            HttpResponse<byte[]> r = f.get(25, TimeUnit.SECONDS);   // budget sur l'échange ENTIER, corps compris
            if (r.statusCode() != 200) {
                dernierEtat = "manifeste distant HTTP " + r.statusCode();
                LOG.info("médias : {} ({})", dernierEtat, urlManifeste);
                return null;
            }
            Manifeste m = lire(new String(r.body(), StandardCharsets.UTF_8));
            if (m == null) dernierEtat = "manifeste distant refusé";
            return m;
        } catch (Exception e) {
            dernierEtat = "hors ligne (" + e.getClass().getSimpleName() + ")";
            LOG.info("médias : manifeste injoignable, cache local conservé : {}", e.toString());
            return null;
        }
    }

    /** Analyse et VALIDE un manifeste. Renvoie {@code null} s'il est inutilisable (journalisé). */
    static Manifeste lire(String json) {
        Manifeste m;
        try {
            m = GSON.fromJson(json, Manifeste.class);
        } catch (JsonParseException e) {
            LOG.warn("manifeste de médias : JSON invalide : {}", e.getMessage());
            return null;
        }
        if (m == null || m.fichiers() == null) return null;
        if (m.format() != FORMAT) {
            LOG.warn("manifeste de médias : format {} non pris en charge (attendu {})", m.format(), FORMAT);
            return null;
        }
        List<Entree> ok = new ArrayList<>();
        Set<String> vus = new HashSet<>();
        for (Entree e : m.fichiers()) {
            if (e == null || e.chemin() == null || !CHEMIN.matcher(e.chemin()).matches()
                    || e.sha256() == null || !SHA.matcher(e.sha256()).matches()
                    || e.asset() == null || !ASSET.matcher(e.asset()).matches()
                    || e.taille() < 1 || !vus.add(e.chemin())) {
                LOG.warn("manifeste de médias : entrée rejetée {}", e);
                continue;
            }
            ok.add(e);
        }
        return new Manifeste(m.format(), m.version() == null ? "" : m.version(), m.base(), List.copyOf(ok));
    }

    private void installer(Manifeste m, String depuis) {
        Map<String, Entree> nouvel = new LinkedHashMap<>();
        for (Entree e : m.fichiers()) nouvel.put(e.chemin(), e);
        String b = m.base();
        if (b == null || b.isBlank()) b = depuis.substring(0, depuis.lastIndexOf('/') + 1);
        if (!b.endsWith("/")) b += "/";
        base = b;
        version = m.version();
        index = Map.copyOf(nouvel);
    }

    private void enregistrer(Manifeste m) {
        try {
            Files.createDirectories(dossier);
            Path tmp = dossier.resolve(NOM_MANIFESTE + ".tmp");
            Files.writeString(tmp, GSON.toJson(m), StandardCharsets.UTF_8);
            Files.move(tmp, dossier.resolve(NOM_MANIFESTE),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warn("médias : manifeste local non enregistré : {}", e.toString());
        }
    }

    /**
     * Supprime du cache les fichiers que le manifeste courant ne désigne plus (anciennes versions).
     * Ne touche QUE les noms au format du cache ({@code <sha>.<ext>[.part]}) : rien d'autre dans le
     * dossier n'est à lui.
     */
    void ranger() {
        Set<String> gardes = new HashSet<>();
        for (Entree e : index.values()) {
            gardes.add(cible(e).getFileName().toString());
            gardes.add(cible(e).getFileName() + ".part");
        }
        try (var flux = Files.list(dossier)) {
            flux.filter(p -> FICHIER_CACHE.matcher(p.getFileName().toString()).matches())
                .filter(p -> !gardes.contains(p.getFileName().toString()))
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
        } catch (IOException ignored) {
            // dossier absent : rien à ranger
        }
    }

    // ── Téléchargement ──────────────────────────────────────────────────────────────────────

    /** Télécharge une entrée (dédoublonné : deux demandes simultanées partagent le même futur). */
    CompletableFuture<Path> telecharger(Entree e) {
        Path deja = fichierPresent(e);
        if (deja != null) return CompletableFuture.completedFuture(deja);
        CompletableFuture<Path> nouveau = new CompletableFuture<>();
        CompletableFuture<Path> existant = enCours.putIfAbsent(e.sha256(), nouveau);
        if (existant != null) return existant;
        Runnable tache = () -> {
            Path p = null;
            try {
                p = telechargerAvecReessais(e);
            } catch (Exception ex) {
                LOG.warn("médias : {} non téléchargé : {}", e.chemin(), ex.toString());
            } finally {
                // Retirer AVANT de compléter, et seulement NOTRE futur : une nouvelle demande après
                // un échec doit relancer un téléchargement, pas recevoir le vieux résultat nul.
                enCours.remove(e.sha256(), nouveau);
                nouveau.complete(p);
            }
            if (p != null) {
                // Tous les chemins qui partagent ce contenu sont servis.
                for (Entree autre : index.values()) {
                    if (autre.sha256().equals(e.sha256())) servirAttentes(autre.chemin(), p);
                }
            }
        };
        try {
            fils.execute(tache);
        } catch (java.util.concurrent.RejectedExecutionException rex) {
            enCours.remove(e.sha256(), nouveau);
            nouveau.complete(null);
        }
        return nouveau;
    }

    private Path telechargerAvecReessais(Entree e) throws IOException {
        IOException derniere = null;
        boolean shaDejaRate = false;
        for (int essai = 0; essai <= REESSAIS; essai++) {
            if (essai > 0) {
                try { Thread.sleep(ATTENTES_MS[Math.min(essai - 1, ATTENTES_MS.length - 1)]); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new IOException("interrompu"); }
            }
            try {
                return telechargerUneFois(e);
            } catch (ShaFaux sf) {
                // Contenu corrompu : on repart de zéro UNE fois, puis on abandonne (un asset vraiment
                // différent du manifeste ne doit pas être retéléchargé en boucle).
                if (shaDejaRate) throw sf;
                shaDejaRate = true;
                derniere = sf;
            } catch (IOException io) {
                derniere = io;                 // coupure : on réessaie en REPRENANT le .part
            }
        }
        throw derniere;
    }

    private Path telechargerUneFois(Entree e) throws IOException {
        Files.createDirectories(dossier);
        Path cible = cible(e);
        Path part = cible.resolveSibling(cible.getFileName() + ".part");
        long deja = Files.isRegularFile(part) ? Files.size(part) : 0;
        if (deja > e.taille()) { Files.deleteIfExists(part); deja = 0; }

        if (deja < e.taille()) {
            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(base + e.asset()))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "NyleLauncher/medias")
                    .GET();
            if (deja > 0) rb.header("Range", "bytes=" + deja + "-");
            HttpResponse<InputStream> r;
            try {
                r = http.send(rb.build(), HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("interrompu");
            }
            int code = r.statusCode();
            boolean ajout;
            if (code == 206 && deja > 0) {
                ajout = true;                                    // reprise acceptée
            } else if (code == 200) {
                ajout = false; deja = 0;                         // serveur sans Range : on repart de zéro
            } else if (code == 416) {
                r.body().close();
                Files.deleteIfExists(part);                      // .part incohérent : tout recommencer
                throw new IOException("HTTP 416 sur " + e.asset());
            } else {
                r.body().close();
                throw new IOException("HTTP " + code + " sur " + e.asset());
            }
            // Budget mural sur le CORPS (leçon du CDN qui « goutte », 2026-07-10) : au moins 60 s,
            // sinon la taille à 20 Ko/s — un budget se calcule sur ce qu'on transfère.
            long budgetMs = Math.max(60_000L, (e.taille() / 20_000L) * 1_000L);
            long limite = System.currentTimeMillis() + budgetMs;
            try (InputStream in = r.body();
                 OutputStream out = Files.newOutputStream(part, StandardOpenOption.CREATE,
                         ajout ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING,
                         StandardOpenOption.WRITE)) {
                byte[] buf = new byte[32 * 1024];
                long total = deja;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    total += n;
                    if (total > e.taille()) throw new IOException("plus gros que prévu : " + e.asset());
                    if (System.currentTimeMillis() > limite) throw new IOException("trop lent : " + e.asset());
                }
            }
        }
        long taille = Files.size(part);
        if (taille != e.taille()) throw new IOException("incomplet (" + taille + "/" + e.taille() + ") : " + e.asset());
        String sha = Hashing.sha256(part);
        if (!e.sha256().equals(sha)) {
            Files.deleteIfExists(part);
            throw new ShaFaux("SHA-256 différent du manifeste pour " + e.chemin());
        }
        Files.move(part, cible, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        LOG.info("médias : {} prêt ({} o)", e.chemin(), e.taille());
        return cible;
    }

    /** SHA faux : distinct d'une coupure réseau, qui, elle, se reprend. */
    static final class ShaFaux extends IOException {
        ShaFaux(String m) { super(m); }
    }

    // ── Utilitaires ─────────────────────────────────────────────────────────────────────────

    private Path fichierPresent(Entree e) {
        Path p = cible(e);
        try {
            return Files.isRegularFile(p) && Files.size(p) == e.taille() ? p : null;
        } catch (IOException ex) {
            return null;
        }
    }

    Path cible(Entree e) {
        String nom = e.chemin().substring(e.chemin().lastIndexOf('/') + 1);
        int point = nom.lastIndexOf('.');
        String ext = point < 0 ? "" : nom.substring(point + 1).toLowerCase(Locale.ROOT);
        return dossier.resolve(EXT.matcher(ext).matches() ? e.sha256() + "." + ext : e.sha256());
    }

    private void servirAttentes(String chemin, Path p) {
        List<Consumer<Path>> l = attentes.remove(chemin);
        if (l == null) return;
        for (Consumer<Path> c : l) appeler(c, p);
    }

    private static void appeler(Consumer<Path> c, Path p) {
        try { c.accept(p); } catch (Throwable t) { LOG.warn("médias : rappel en échec : {}", t.toString()); }
    }

    /** Pour les tests : arrête les fils. */
    void fermer() { fils.shutdownNow(); }
}
