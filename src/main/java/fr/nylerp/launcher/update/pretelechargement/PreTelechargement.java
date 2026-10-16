package fr.nylerp.launcher.update.pretelechargement;

import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.univers.PreTelechargeur;
import fr.nylerp.launcher.univers.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Point d'entrée du pré-téléchargement, appelé UNE fois par l'intégration (lot L9) au démarrage du
 * launcher, en mode normal comme en mode fond :
 *
 * <pre>PreTelechargement.demarrer();</pre>
 *
 * <ul>
 *   <li>pose {@link Services#preTelechargeur} (l'interface lit l'état et la taille de chaque univers,
 *       et lance « Pré-télécharger » par {@link PreTelechargeur#demarrer}) ;</li>
 *   <li>évalue les deux univers quelques secondes après le démarrage (taille à afficher) ;</li>
 *   <li>puis, toutes les {@link #intervalle}, si {@code Settings.preTelechargement} est vrai,
 *       télécharge en fond les mises à jour des univers déjà joués, et nettoie la réserve.</li>
 * </ul>
 *
 * Plafond du fond : 50 % de la bande mesurée, ou {@code -Dnyle.pretelechargement.plafond=<octets/s>}.
 */
public final class PreTelechargement {

    private static final Logger LOG = LoggerFactory.getLogger(PreTelechargement.class);

    static Duration premiereEvaluation = Duration.ofSeconds(5);
    static Duration premierPassage = Duration.ofSeconds(90);
    static Duration intervalle = Duration.ofHours(3);

    private static PreTelechargeurPack instance;
    private static ScheduledExecutorService minuterie;

    private PreTelechargement() { }

    public static synchronized PreTelechargeur demarrer() {
        if (instance != null) return instance;
        instance = new PreTelechargeurPack(Conditions.systeme(), Long.getLong("nyle.pretelechargement.plafond", 0L));
        Services.preTelechargeur = instance;
        minuterie = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nyle-pretelechargement-minuterie");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        PreTelechargeurPack p = instance;
        minuterie.schedule(() -> evaluerTout(p), premiereEvaluation.toMillis(), TimeUnit.MILLISECONDS);
        minuterie.scheduleWithFixedDelay(() -> cycle(p), premierPassage.toMillis(), intervalle.toMillis(),
                TimeUnit.MILLISECONDS);
        LOG.info("Pré-téléchargement démarré (passage toutes les {} h)", intervalle.toHours());
        return instance;
    }

    /** Arrête tout (tests, et « Quitter »). Les partiels restent pour la reprise. */
    public static synchronized void arreter() {
        if (minuterie != null) minuterie.shutdownNow();
        if (instance != null) instance.fermer();
        if (Services.preTelechargeur == instance) Services.preTelechargeur = PreTelechargeur.AUCUN;
        minuterie = null;
        instance = null;
    }

    private static void evaluerTout(PreTelechargeurPack p) {
        for (String u : PreTelechargeurPack.UNIVERS) p.evaluer(u);
    }

    /** Un passage de la minuterie : fond des univers déjà joués, puis ménage de la réserve. */
    static void cycle(PreTelechargeurPack p) {
        try {
            boolean actif = Settings.get().preTelechargement;
            List<Future<?>> taches = new ArrayList<>();
            boolean complet = true;
            for (String u : PreTelechargeurPack.UNIVERS) {
                Future<?> f = actif && new PlanPack(u).dejaJoue() ? p.demarrerAuto(u) : p.evaluer(u);
                if (f == null) complet = false;  // une tâche (premier plan) tourne déjà : ménage plus tard
                else taches.add(f);
            }
            for (Future<?> f : taches) f.get();
            Set<String> utiles = complet ? p.utilesDeTous() : null;
            if (utiles != null) Reserve.purger(utiles);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOG.warn("Pré-téléchargement : passage échoué : {}", e.toString());
        }
    }
}
