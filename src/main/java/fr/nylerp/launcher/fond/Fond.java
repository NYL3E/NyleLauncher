package fr.nylerp.launcher.fond;

import fr.nylerp.launcher.config.AppPaths;
import fr.nylerp.launcher.config.Settings;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Le launcher en arrière-plan : icône de barre système, démarrage automatique à l'ouverture de
 * session, instance unique, annonces.
 *
 * <h2>Cycle de vie</h2>
 * <ol>
 *   <li>{@link #prendreLaMain(String[])} dans {@code Main}, avant JavaFX : si un launcher tourne
 *       déjà, il reçoit le message (il se montre, sauf si ce lancement venait du démarrage
 *       automatique) et ce processus s'arrête.</li>
 *   <li>{@link #installer(Stage, Runnable)} dans {@code LauncherApp.start} : pose l'icône, aligne
 *       le démarrage automatique sur le réglage, et en mode {@code --fond} n'ouvre AUCUNE fenêtre
 *       ni interface : la construction est différée à la première ouverture (pas de vidéo ni de
 *       musique jouées en cachette à l'ouverture de session, mémoire minimale).</li>
 *   <li>Fermer la fenêtre la range dans la barre ({@link #masquer()}) quand {@code fondActif} ;
 *       « Quitter » dans le menu de l'icône arrête tout ({@link #quitter()}).</li>
 * </ol>
 *
 * <h2>Points d'accroche pour l'interface</h2>
 * {@link #surMasquage(Runnable)} et {@link #surAffichage(Runnable)} : couper puis reprendre vidéos
 * et musique ; {@link #surNavigation(Consumer)} : ouvrir la bonne page quand une annonce vise une
 * cible ; {@link #notifier(String, String, String)} : afficher une annonce ;
 * {@link #fermerApresLancement()} : ce que fait le launcher quand le jeu démarre ;
 * {@link #appliquerReglages()} : après un changement dans les réglages.
 *
 * <h2>macOS</h2>
 * Un seul processus JavaFX + AWT. En fond, la politique d'activation passe en « accessoire »
 * ({@link MacApp}) : pas d'icône dans le Dock, icône dans la barre des menus. Un double-clic sur
 * l'app dans le Finder alors qu'elle tourne déjà ne crée pas de second processus : macOS réactive
 * le premier. On détecte cette réactivation (passage de {@code isActive} de faux à vrai) et la
 * fenêtre s'ouvre.
 */
public final class Fond {

    private static final Logger LOG = LoggerFactory.getLogger(Fond.class);

    /** Argument passé par l'entrée de démarrage automatique. */
    public static final String ARG_FOND = "--fond";

    private static volatile boolean lanceEnFond;
    private static InstanceUnique instance;
    private static volatile Stage stage;
    private static Runnable construction;
    private static boolean construite;
    private static volatile Barre barre;
    /** Message reçu avant que JavaFX soit prêt : appliqué dans {@link #installer}. */
    private static volatile String ouvertureEnAttente;
    private static ScheduledExecutorService veilleMac;
    private static boolean etaitActive;

    private static final List<Runnable> masquage = new CopyOnWriteArrayList<>();
    private static final List<Runnable> affichage = new CopyOnWriteArrayList<>();
    private static volatile Consumer<String> navigateur;

    private Fond() {}

    // ── Démarrage ────────────────────────────────────────────────────────────

    /**
     * À appeler dans {@code main}, avant {@code Application.launch}.
     *
     * @return {@code false} si un launcher tournait déjà et a pris le relais : quitter sans interface.
     */
    public static boolean prendreLaMain(String[] args) {
        lanceEnFond = args != null && Arrays.asList(args).contains(ARG_FOND);
        instance = new InstanceUnique(AppPaths.rootDir());
        InstanceUnique.Resultat r = instance.acquerir(lanceEnFond ? "FOND" : "OUVRIR", Fond::recevoir);
        LOG.info("Fond : lancement {} — instance {}", lanceEnFond ? "en fond" : "normal", r);
        return r != InstanceUnique.Resultat.RELAYEE;
    }

    /** Vrai si ce processus a été lancé par le démarrage automatique ({@code --fond}). */
    public static boolean lanceEnFond() {
        return lanceEnFond;
    }

    /**
     * À appeler dans {@code LauncherApp.start}, dès que la {@link Stage} existe.
     *
     * @param construireEtAfficher construit l'interface et montre la fenêtre (le démarrage normal)
     * @return {@code true} en mode fond : l'appelant ne construit RIEN, la construction aura lieu à
     *         la première ouverture ; {@code false} : l'appelant poursuit comme avant.
     */
    public static boolean installer(Stage s, Runnable construireEtAfficher) {
        stage = s;
        construction = construireEtAfficher;
        Settings reglages = Settings.get();
        boolean barreVoulue = reglages.fondActif || lanceEnFond;
        if (barreVoulue) barre = Barre.installer(() -> afficher(null), Fond::quitter, Fond::sansBarre);
        appliquerFermeture();
        // Le démarrage automatique n'est posé qu'une fois l'écran « Nouveau launcher » validé (il l'explique
        // et permet de le couper) : jamais à l'insu d'un joueur qui vient de recevoir la mise à jour.
        DemarrageAuto.synchroniser(reglages.demarrageAuto && reglages.accueilVu);
        if (Systeme.mac()) demarrerVeilleMac();

        String enAttente = ouvertureEnAttente;
        ouvertureEnAttente = null;
        if (lanceEnFond && enAttente == null) {
            if (Systeme.mac()) MacApp.politique(MacApp.ACCESSOIRE);
            LOG.info("Fond : démarré dans la barre système, interface différée");
            return true;
        }
        construite = true;
        if (enAttente != null && !enAttente.isEmpty() && navigateur != null) {
            String cible = enAttente;
            Platform.runLater(() -> naviguer(cible));
        }
        return false;
    }

    // ── Fenêtre ──────────────────────────────────────────────────────────────

    /** Montre (et construit au besoin) la fenêtre, puis ouvre {@code cible} si elle est donnée. */
    public static void afficher(String cible) {
        if (stage == null) { // message d'un second lancement arrivé avant l'interface
            ouvertureEnAttente = cible == null ? "" : cible;
            return;
        }
        surFx(() -> {
            if (Systeme.mac()) MacApp.politique(MacApp.NORMALE);
            if (!construite) {
                construite = true;
                construction.run();
            } else if (!stage.isShowing()) {
                stage.show();
            }
            if (stage.isIconified()) stage.setIconified(false);
            stage.toFront();
            stage.requestFocus();
            if (Systeme.mac()) MacApp.activer();
            etaitActive = true;
            for (Runnable r : affichage) executer(r);
            naviguer(cible);
        });
    }

    /** Range la fenêtre dans la barre système (le launcher continue de tourner). */
    public static void masquer() {
        surFx(() -> {
            if (stage == null) return;
            boolean premiereFois = !astuceVue();
            stage.hide();
            for (Runnable r : masquage) executer(r);
            if (Systeme.mac()) {
                MacApp.politique(MacApp.ACCESSOIRE);
                MacApp.desactiver();
                etaitActive = false;
            }
            if (premiereFois && barre != null) {
                marquerAstuceVue();
                Toast.afficher("Le launcher reste à portée de main",
                        Systeme.mac() ? "Retrouve-le dans la barre des menus, en haut de l'écran. Pour l'arrêter : Quitter dans son menu."
                                      : "Retrouve-le près de l'horloge, dans les icônes cachées. Pour l'arrêter : clic droit, Quitter.",
                        () -> afficher(null));
            }
        });
    }

    /** Arrête complètement le launcher (menu « Quitter » de l'icône). */
    public static void quitter() {
        LOG.info("Fond : arrêt demandé");
        if (barre != null) barre.retirer();
        if (veilleMac != null) veilleMac.shutdownNow();
        if (instance != null) instance.fermer();
        Platform.exit();
        System.exit(0);
    }

    /**
     * À appeler quand le jeu vient de démarrer et que le réglage « fermer au lancement » est actif :
     * avec l'icône de barre, on range la fenêtre (le launcher reste là pour les annonces) ; sans
     * elle, on quitte comme avant.
     */
    public static void fermerApresLancement() {
        if (barre != null && Settings.get().fondActif) masquer();
        else quitter();
    }

    // ── Annonces ─────────────────────────────────────────────────────────────

    /**
     * Affiche une annonce dans le coin de l'écran, fenêtre ouverte ou non. Sans effet si le joueur a
     * coupé les notifications. Un clic ouvre le launcher sur {@code cible} (peut être {@code null}).
     * Appelable depuis n'importe quel fil.
     */
    public static void notifier(String titre, String texte, String cible) {
        if (!Settings.get().notifications) return;
        // Une annonce qui pointe vers un lien ouvre le navigateur sans sortir la fenêtre du launcher.
        Runnable clic = fr.nylerp.launcher.annonces.Annonces.estUrl(cible)
                ? () -> fr.nylerp.launcher.annonces.Annonces.ouvrirUrl(cible)
                : () -> afficher(cible);
        surFx(() -> Toast.afficher(titre, texte, clic));
    }

    // ── Réglages ─────────────────────────────────────────────────────────────

    /** Applique {@code demarrageAuto}, {@code fondActif} et l'icône après un changement de réglage. */
    public static void appliquerReglages() {
        Settings r = Settings.get();
        DemarrageAuto.synchroniser(r.demarrageAuto && r.accueilVu);
        surFx(() -> {
            if (r.fondActif && barre == null && stage != null) {
                barre = Barre.installer(() -> afficher(null), Fond::quitter, Fond::sansBarre);
            } else if (!r.fondActif && barre != null && !lanceEnFond) {
                barre.retirer();
                barre = null;
            }
            appliquerFermeture();
        });
    }

    /** « Tout supprimer » des réglages : retire aussi l'entrée de démarrage automatique. */
    public static void retirerDemarrageAuto() {
        DemarrageAuto.synchroniser(false);
    }

    // ── Points d'accroche ────────────────────────────────────────────────────

    /** Appelé (fil JavaFX) chaque fois que la fenêtre est rangée : couper vidéos et musique. */
    public static void surMasquage(Runnable r) { masquage.add(r); }

    /** Appelé (fil JavaFX) chaque fois que la fenêtre réapparaît : reprendre vidéos et musique. */
    public static void surAffichage(Runnable r) { affichage.add(r); }

    /** Reçoit la cible d'une annonce cliquée (« pokenyle », « nylerp », « reglages »…). */
    public static void surNavigation(Consumer<String> n) { navigateur = n; }

    // ── Interne ──────────────────────────────────────────────────────────────

    private static void appliquerFermeture() {
        boolean garder = barre != null && Settings.get().fondActif;
        Platform.setImplicitExit(!garder);
        if (stage != null) {
            stage.setOnCloseRequest(e -> {
                if (barre != null && Settings.get().fondActif) {
                    e.consume();
                    masquer();
                } else {
                    quitter();
                }
            });
        }
    }

    /** L'icône n'a pas pu être posée : fermer la fenêtre quittera. */
    private static void sansBarre() {
        surFx(() -> {
            barre = null;
            appliquerFermeture();
        });
    }

    private static void recevoir(String message) {
        LOG.info("Fond : message d'un second lancement : {}", message);
        if (message.startsWith("FOND")) return; // démarrage automatique en double : rien à montrer
        String cible = message.startsWith("OUVRIR ") ? message.substring(7).trim() : null;
        afficher(cible);
    }

    private static void naviguer(String cible) {
        Consumer<String> n = navigateur;
        if (cible != null && !cible.isBlank() && n != null) executer(() -> n.accept(cible));
    }

    /**
     * macOS : le Finder réactive l'app déjà lancée au lieu d'en démarrer une seconde. Toutes les
     * 600 ms (sur le fil JavaFX, qui est le fil principal d'AppKit), on regarde si le launcher vient
     * de passer au premier plan alors que sa fenêtre est cachée — hors clic sur son icône de barre,
     * dont le menu peut lui aussi l'activer.
     */
    private static void demarrerVeilleMac() {
        veilleMac = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nyle-fond-veille");
            t.setDaemon(true);
            return t;
        });
        veilleMac.scheduleWithFixedDelay(() -> surFx(() -> {
            if (stage == null) return;
            boolean active = MacApp.estActive();
            boolean front = active && !etaitActive;
            etaitActive = active;
            if (!front || stage.isShowing()) return;
            if (barre != null && System.currentTimeMillis() - barre.derniereInteraction() < 1500) return;
            LOG.info("Fond : réactivation par macOS (Finder, Spotlight…) — ouverture de la fenêtre");
            afficher(null);
        }), 1200, 600, TimeUnit.MILLISECONDS);
    }

    private static Path fichierAstuce() {
        return AppPaths.launcherState().resolve("fond-astuce-vue");
    }

    private static boolean astuceVue() {
        try { return Files.exists(fichierAstuce()); } catch (Throwable t) { return true; }
    }

    private static void marquerAstuceVue() {
        try { Files.writeString(fichierAstuce(), "1"); } catch (Throwable ignored) { }
    }

    private static void surFx(Runnable r) {
        try {
            if (Platform.isFxApplicationThread()) r.run();
            else Platform.runLater(r);
        } catch (IllegalStateException pasEncorePret) {
            // JavaFX pas encore démarré : seul afficher() peut arriver ici (message très précoce).
            LOG.debug("Fond : JavaFX pas prêt, action ignorée");
        }
    }

    private static void executer(Runnable r) {
        try { r.run(); } catch (Throwable t) { LOG.warn("Fond : point d'accroche en échec : {}", t.toString()); }
    }
}
