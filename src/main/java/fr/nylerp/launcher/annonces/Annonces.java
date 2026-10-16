package fr.nylerp.launcher.annonces;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.auth.AccountStore;
import fr.nylerp.launcher.config.AppPaths;
import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.fond.Fond;
import fr.nylerp.launcher.univers.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Point d'entrée unique des annonces et de la config distante (lot L7, contrat dans le vault
 * « NyleLauncher — contrat d'API annonces et config (L7) »).
 *
 * <p>L'intégration appelle {@link #demarrer()} une fois au démarrage, fenêtre ou {@code --fond} :
 * la copie locale de la config est lue tout de suite ({@link Services#etatPokenyle} est utilisable
 * dès le retour), puis les deux points d'accès sont lus en tâche de fond au démarrage et toutes les
 * 15 min (30 min après 3 échecs de suite). Rien ne bloque ni ne plante si le réseau manque.
 */
public final class Annonces {

    private static final Logger LOG = LoggerFactory.getLogger(Annonces.class);

    static final String BASE_DEFAUT = "https://pokerayou.academy/api/launcher/";
    static final long CYCLE_MIN = 15;
    static final long CYCLE_APRES_ECHECS_MIN = 30;
    static final int ECHECS_AVANT_RALENTI = 3;

    private static ScheduledExecutorService planif;
    private static ConfigDistante config;
    private static ServiceAnnonces service;
    private static UniversJoues univers;
    private static HorlogeServeur horloge;
    private static int echecsDeSuite;

    private Annonces() { }

    /** Démarre la config distante et les annonces. Sans effet si déjà démarré. */
    public static synchronized void demarrer() {
        if (planif != null) return;
        String base = System.getProperty("nyle.annonces.base", BASE_DEFAUT);
        if (!base.endsWith("/")) base += "/";
        Path etat = AppPaths.launcherState();
        planif = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nyle-annonces");
            t.setDaemon(true);
            return t;
        });
        horloge = new HorlogeServeur();
        ClientLecture client = new ClientLecture();
        config = new ConfigDistante(new PointAcces("config", URI.create(base + "config"), client,
                new CopieLocale(etat.resolve("config-cache.json")), horloge));
        univers = new UniversJoues(AppPaths.rootDir(), etat.resolve("univers-joues.json"));
        service = new ServiceAnnonces(
                new PointAcces("annonces", URI.create(base + "annonces"), client,
                        new CopieLocale(etat.resolve("annonces-cache.json")), horloge),
                horloge, new VuesAnnonces(etat.resolve("annonces-vues.json")),
                univers::lire, Annonces::typeCompte,
                () -> Settings.get().notifications, Fond::notifier,
                planif, ServiceAnnonces.ESPACEMENT);
        Services.etatPokenyle = config;
        echecsDeSuite = 0;
        planif.execute(Annonces::cycle);
        LOG.info("Annonces : démarrées ({})", base);
    }

    /**
     * À appeler quand le compte actif change (connexion, changement de compte) : le ciblage premium /
     * crack dépend du compte, et le premier cycle a pu tourner avant toute connexion.
     */
    public static synchronized void compteChange() {
        if (planif == null || service == null) return;
        ServiceAnnonces s = service;
        planif.schedule(() -> {
            try { s.reprogrammer(); } catch (Throwable t) { LOG.warn("Annonces : reprogrammation en échec : {}", t.toString()); }
        }, 2, TimeUnit.SECONDS);
    }

    /** Arrête les lectures (quitter, tests). La config déjà lue reste en place. */
    public static synchronized void arreter() {
        if (planif != null) planif.shutdownNow();
        planif = null;
    }

    /** À appeler quand le joueur ouvre ou lance un univers (« pokenyle », « nylerp ») : ciblage des annonces. */
    public static void universOuvert(String id) {
        UniversJoues u = univers;
        if (u != null) u.marquer(id);
    }

    /** Heure du serveur (pour un compte à rebours fiable), ou vide sans réponse du serveur depuis le démarrage. */
    public static Optional<OffsetDateTime> maintenantServeur() {
        HorlogeServeur h = horloge;
        return h == null ? Optional.empty() : h.maintenant();
    }

    /** Vrai si la cible d'une carte est un lien (à ouvrir dans le navigateur) plutôt qu'un univers. */
    public static boolean estUrl(String cible) {
        return Annonce.estHttps(cible);
    }

    /**
     * Ouvre un lien https dans le navigateur par défaut, hors du fil appelant. Tout autre schéma est
     * refusé (contrat L7, règle 9).
     */
    public static void ouvrirUrl(String url) {
        if (!Annonce.estHttps(url)) {
            LOG.warn("Annonces : lien refusé (pas en https) : {}", url);
            return;
        }
        Thread t = new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI.create(url.trim()));
                    return;
                }
            } catch (Throwable ignored) { }
            try {
                String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
                ProcessBuilder pb = os.contains("win") ? new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url.trim())
                        : os.contains("mac") ? new ProcessBuilder("open", url.trim())
                        : new ProcessBuilder("xdg-open", url.trim());
                pb.start();
            } catch (Exception e) {
                LOG.warn("Annonces : impossible d'ouvrir le lien : {}", e.toString());
            }
        }, "nyle-annonces-lien");
        t.setDaemon(true);
        t.start();
    }

    // ── Interne ──────────────────────────────────────────────────────────────

    private static void cycle() {
        boolean ok;
        try {
            boolean c = config.rafraichir();
            boolean a = service.rafraichir();
            ok = c && a;
        } catch (Throwable t) {
            LOG.warn("Annonces : cycle en échec : {}", t.toString());
            ok = false;
        }
        long attente;
        synchronized (Annonces.class) {
            echecsDeSuite = ok ? 0 : echecsDeSuite + 1;
            attente = echecsDeSuite >= ECHECS_AVANT_RALENTI ? CYCLE_APRES_ECHECS_MIN : CYCLE_MIN;
            if (planif == null) return;
            planif.schedule(Annonces::cycle, attente, TimeUnit.MINUTES);
        }
    }

    private static ServiceAnnonces.TypeCompte typeCompte() {
        try {
            Account a = AccountStore.active();
            if (a == null) return ServiceAnnonces.TypeCompte.AUCUN;
            return a.isOffline() ? ServiceAnnonces.TypeCompte.CRACK : ServiceAnnonces.TypeCompte.PREMIUM;
        } catch (Throwable t) {
            return ServiceAnnonces.TypeCompte.AUCUN;
        }
    }
}
