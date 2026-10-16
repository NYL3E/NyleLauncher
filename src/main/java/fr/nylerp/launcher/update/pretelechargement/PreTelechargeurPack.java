package fr.nylerp.launcher.update.pretelechargement;

import fr.nylerp.launcher.univers.PreTelechargeur;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Le pré-téléchargement des packs « pokenyle » et « nylerp » dans la {@link Reserve}, pour que le
 * prochain « Jouer » n'ait plus rien à télécharger : la synchronisation normale y prend les
 * fichiers au lieu de les retélécharger.
 *
 * <h2>Deux priorités</h2>
 * <ul>
 *   <li><b>premier plan</b> ({@link #demarrer}) : le joueur l'a demandé (« Pré-télécharger » de
 *       Pokényle avant l'ouverture, environ 1 Go) — pleine vitesse, sauf pendant une partie où le
 *       débit est bridé ; jamais suspendu tout seul ;</li>
 *   <li><b>fond</b> ({@link #demarrerAuto}) : mises à jour des univers déjà joués, lancées par la
 *       minuterie de {@link PreTelechargement} — débit plafonné (50 % de la bande mesurée, ou un
 *       plafond fixé), et arrêt dès que le jeu tourne, qu'une synchronisation démarre ou que la
 *       connexion est facturée ; ce qui est arrivé est gardé et reprend au passage suivant.</li>
 * </ul>
 * Une demande au premier plan suspend un téléchargement de fond en cours sur l'autre univers.
 *
 * <h2>Pokényle avant l'ouverture : réservé à ceux qui peuvent déjà jouer</h2>
 * Le pack de Pokényle n'est téléchargé, au premier plan comme en fond, que si le compte actif y a accès
 * ({@link fr.nylerp.launcher.univers.AccesPokenyle} : ouverture générale, testeur distant ou historique). Les autres
 * joueurs sont seulement prévenus de l'ouverture (décision de l'owner, 16/10/2026). Le refus est silencieux (journal,
 * état inchangé), et {@link #reverifierAcces()} annule ce qui tourne quand le compte ou la config distante change.
 */
public final class PreTelechargeurPack implements PreTelechargeur {

    private static final Logger LOG = LoggerFactory.getLogger(PreTelechargeurPack.class);

    static final List<String> UNIVERS = List.of("pokenyle", "nylerp");
    static final int FLUX = 4;
    /** Plafond du premier plan pendant qu'une partie tourne (octets/s). */
    static long plafondJeuEnCours = 1024 * 1024;
    /** Le fond ne descend jamais sous ce débit (octets/s), même sur une ligne lente. */
    static long plancherFond = 256 * 1024;
    /** Mesure de la bande passante au début d'un téléchargement de fond. */
    static long mesureOctets = 8L * 1024 * 1024;
    static long mesureNanos = TimeUnit.SECONDS.toNanos(5);
    /** Marge de disque gardée libre en plus de ce qui sera téléchargé. */
    static long margeDisque = 256L * 1024 * 1024;

    enum Priorite { PREMIER_PLAN, FOND }

    private static final class Tache {
        final String univers;
        volatile Priorite priorite;
        volatile boolean annulee;
        volatile boolean suspendue;

        Tache(String univers, Priorite priorite) {
            this.univers = univers;
            this.priorite = priorite;
        }

        boolean arretee() { return annulee || suspendue; }
    }

    private static final class EtatUnivers {
        volatile Etat etat = Etat.AUCUN;
        volatile long total = -1;
        final AtomicLong faits = new AtomicLong();
        volatile String erreur;
        volatile Tache tache;
        /** Empreintes que ce pack peut encore prendre dans la réserve (null = pas encore évalué). */
        volatile Set<String> utiles;
    }

    private final Map<String, EtatUnivers> etats = new ConcurrentHashMap<>();
    private final List<Runnable> rappels = new CopyOnWriteArrayList<>();
    private final ExecutorService file = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nyle-pretelechargement");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private final Conditions conditions;
    private final long plafondFond;
    private final BooleanSupplier pokenylePermis;
    private volatile long dernierAvis;

    /**
     * @param plafondFond débit maximal du fond en octets/s ; 0 = 50 % de la bande mesurée
     */
    PreTelechargeurPack(Conditions conditions, long plafondFond) {
        this(conditions, plafondFond, fr.nylerp.launcher.univers.AccesPokenyle::compteActifAutorise);
    }

    /**
     * @param pokenylePermis le compte actif peut-il télécharger le pack Pokényle ? (refus par défaut : voir
     *                       {@link fr.nylerp.launcher.univers.AccesPokenyle#compteActifAutorise()})
     */
    PreTelechargeurPack(Conditions conditions, long plafondFond, BooleanSupplier pokenylePermis) {
        this.conditions = conditions;
        this.plafondFond = Math.max(0, plafondFond);
        this.pokenylePermis = pokenylePermis;
    }

    /** Vrai si ce téléchargement est permis pour le compte actif (seul Pokényle est conditionné). */
    boolean permis(String univers) {
        return !"pokenyle".equals(univers) || pokenylePermis.getAsBoolean();
    }

    /**
     * À appeler quand le compte actif change ou que la config distante change : un téléchargement Pokényle en cours
     * pour un compte qui n'y a pas (ou plus) accès est annulé proprement. Le partiel en réserve est gardé, il n'est
     * simplement plus actif.
     */
    public void reverifierAcces() {
        EtatUnivers e = etatOuNull("pokenyle");
        if (e == null) return;
        Tache t = e.tache;
        if (t != null && !t.arretee() && !permis("pokenyle")) {
            LOG.info("Pré-téléchargement pokenyle annulé : le compte actif n'y a pas accès");
            t.annulee = true;
        }
    }

    // ---- Contrat PreTelechargeur (lu par l'interface) --------------------------------------

    @Override
    public Etat etat(String univers) {
        EtatUnivers e = etatOuNull(univers);
        return e == null ? Etat.AUCUN : e.etat;
    }

    @Override
    public double progression(String univers) {
        EtatUnivers e = etatOuNull(univers);
        if (e == null) return 0;
        if (e.total > 0) return Math.min(1.0, (double) e.faits.get() / e.total);
        return e.etat == Etat.PRET ? 1.0 : 0.0;
    }

    @Override
    public long octetsTotal(String univers) {
        EtatUnivers e = etatOuNull(univers);
        return e == null ? -1 : e.total;
    }

    @Override
    public void demarrer(String univers) {
        lancer(univers, Priorite.PREMIER_PLAN);
    }

    @Override
    public void annuler(String univers) {
        EtatUnivers e = etatOuNull(univers);
        if (e == null) return;
        Tache t = e.tache;
        if (t != null) t.annulee = true;
    }

    @Override
    public void surChangement(Runnable rappel) {
        if (rappel != null) rappels.add(rappel);
    }

    /** Dernière erreur lisible (« Espace disque insuffisant… »), ou null. */
    public String erreur(String univers) {
        EtatUnivers e = etatOuNull(univers);
        return e == null ? null : e.erreur;
    }

    // ---- Pilotage --------------------------------------------------------------------------

    /** Téléchargement de fond (minuterie). Sans effet si une tâche est déjà là pour cet univers. */
    Future<?> demarrerAuto(String univers) {
        return lancer(univers, Priorite.FOND);
    }

    /** Calcule seulement ce qu'il y aurait à télécharger (pour afficher la taille), sans rien télécharger. */
    Future<?> evaluer(String univers) {
        EtatUnivers e = etatDe(univers);
        return file.submit(() -> {
            if (e.tache != null) return;  // une tâche s'en occupe déjà
            try {
                PlanPack.Resultat r = new PlanPack(univers).evaluer();
                appliquerPlan(e, r);
                if (e.tache == null) {
                    e.etat = r.rienATelecharger() ? Etat.PRET : Etat.AUCUN;
                    e.erreur = null;
                }
            } catch (Exception ex) {
                LOG.info("Pré-téléchargement {} : évaluation impossible ({})", univers, ex.toString());
            }
            avertir(true);
        });
    }

    /** Empreintes utiles à tous les univers, ou null si l'un d'eux n'a pas encore été évalué. */
    Set<String> utilesDeTous() {
        java.util.HashSet<String> tout = new java.util.HashSet<>();
        for (String u : UNIVERS) {
            Set<String> s = etatDe(u).utiles;
            if (s == null) return null;
            tout.addAll(s);
        }
        return tout;
    }

    void fermer() {
        file.shutdownNow();
    }

    private Future<?> lancer(String univers, Priorite priorite) {
        if (!permis(univers)) {
            LOG.info("Pré-téléchargement {} refusé : le compte actif n'y a pas accès (il est seulement prévenu)", univers);
            return null;
        }
        EtatUnivers e = etatDe(univers);
        Tache t;
        synchronized (e) {
            Tache enCours = e.tache;
            if (enCours != null && !enCours.arretee()) {
                if (priorite == Priorite.PREMIER_PLAN) enCours.priorite = Priorite.PREMIER_PLAN;
                return null;
            }
            t = new Tache(univers, priorite);
            e.tache = t;
            if (priorite == Priorite.PREMIER_PLAN) {
                e.etat = Etat.EN_COURS;
                e.erreur = null;
            }
        }
        if (priorite == Priorite.PREMIER_PLAN) {
            // Le joueur attend CE téléchargement : un fond en cours ailleurs lui cède la place.
            for (Map.Entry<String, EtatUnivers> autre : etats.entrySet()) {
                Tache ta = autre.getValue().tache;
                if (ta != null && ta != t && ta.priorite == Priorite.FOND) ta.suspendue = true;
            }
        }
        avertir(true);
        return file.submit(() -> executer(e, t));
    }

    private void executer(EtatUnivers e, Tache t) {
        if (t.arretee() || !permis(t.univers)) {
            terminer(e, t, Etat.AUCUN, null);
            return;
        }
        try {
            PlanPack.Resultat r = new PlanPack(t.univers).evaluer();
            appliquerPlan(e, r);
            if (r.rienATelecharger()) {
                terminer(e, t, Etat.PRET, null);
                return;
            }
            if (t.priorite == Priorite.FOND && conditions.doitAttendre()) {
                terminer(e, t, Etat.AUCUN, null);
                return;
            }
            long reste = r.aTelecharger().stream().mapToLong(PlanPack.Element::taille).sum();
            long libre = Files.getFileStore(Reserve.dossier()).getUsableSpace();
            if (libre < reste + margeDisque) {
                echouer(e, t, String.format(Locale.FRANCE,
                        "Espace disque insuffisant : il faut %.1f Go de libre.", (reste + margeDisque) / 1e9));
                return;
            }
            e.etat = Etat.EN_COURS;
            avertir(true);
            LOG.info("Pré-téléchargement {} ({}) : {} fichiers, {} Mo à télécharger, {} Mo déjà en réserve",
                    t.univers, t.priorite, r.aTelecharger().size(), reste / 1_000_000, r.octetsDeja() / 1_000_000);
            toutTelecharger(e, t, r);
            terminer(e, t, Etat.PRET, null);
        } catch (TelechargeurFichier.Interrompu ex) {
            terminer(e, t, Etat.AUCUN, null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            terminer(e, t, Etat.AUCUN, null);
        } catch (Exception ex) {
            LOG.warn("Pré-téléchargement {} échoué : {}", t.univers, ex.toString());
            echouer(e, t, "Téléchargement interrompu : " + ex.getMessage());
        }
    }

    /**
     * Un échec demandé par le joueur s'affiche (ERREUR + message) ; un échec du fond (hors ligne,
     * disque plein…) reste silencieux : il est journalisé et le passage suivant réessaiera.
     */
    private void echouer(EtatUnivers e, Tache t, String message) {
        if (t.priorite == Priorite.PREMIER_PLAN) {
            terminer(e, t, Etat.ERREUR, message);
        } else {
            LOG.info("Pré-téléchargement {} (fond) : {}", t.univers, message);
            terminer(e, t, Etat.AUCUN, null);
        }
    }

    private void toutTelecharger(EtatUnivers e, Tache t, PlanPack.Resultat r) throws Exception {
        Limiteur limiteur = new Limiteur(debitVoulu(t, -1));
        ConcurrentLinkedQueue<PlanPack.Element> restants = new ConcurrentLinkedQueue<>(r.aTelecharger());
        Map<String, Long> parFichier = new ConcurrentHashMap<>();
        AtomicReference<Exception> premiereErreur = new AtomicReference<>();
        long debut = System.nanoTime();
        AtomicLong recusDepuisDebut = new AtomicLong();
        long[] mesure = {-1};             // bande mesurée (octets/s), -1 = pas encore
        long[] prochainControle = {0};

        Runnable reglerDebit = () -> {
            long maintenant = System.nanoTime();
            synchronized (mesure) {
                if (maintenant < prochainControle[0]) return;
                prochainControle[0] = maintenant + TimeUnit.SECONDS.toNanos(2);
                if (mesure[0] < 0 && t.priorite == Priorite.FOND && plafondFond == 0) {
                    long ecoule = maintenant - debut;
                    if (recusDepuisDebut.get() >= mesureOctets || ecoule >= mesureNanos) {
                        mesure[0] = (long) (recusDepuisDebut.get() * 1e9 / Math.max(1, ecoule));
                        LOG.info("Pré-téléchargement {} : bande mesurée {} Ko/s, fond plafonné à {} Ko/s",
                                t.univers, mesure[0] / 1024, Math.max(plancherFond, mesure[0] / 2) / 1024);
                    }
                }
            }
            limiteur.setDebit(debitVoulu(t, mesure[0]));
        };

        Runnable travailleur = () -> {
            PlanPack.Element el;
            while ((el = restants.poll()) != null) {
                if (premiereErreur.get() != null || t.arretee()) return;
                final PlanPack.Element courant = el;
                try {
                    TelechargeurFichier.telecharger(courant.url(), courant.sha(), courant.taille(), limiteur,
                            new TelechargeurFichier.Suivi() {
                                @Override
                                public void progression(long octets) {
                                    Long avant = parFichier.put(courant.sha(), octets);
                                    long delta = octets - (avant == null ? 0 : avant);
                                    e.faits.addAndGet(delta);
                                    if (delta > 0) recusDepuisDebut.addAndGet(delta);
                                    avertir(false);
                                }

                                @Override
                                public boolean doitArreter() {
                                    reglerDebit.run();
                                    return t.arretee() || premiereErreur.get() != null
                                            || (t.priorite == Priorite.FOND && conditions.doitAttendre());
                                }
                            });
                } catch (Exception ex) {
                    premiereErreur.compareAndSet(null, ex);
                    return;
                }
            }
        };

        ExecutorService flux = Executors.newFixedThreadPool(FLUX, run -> {
            Thread th = new Thread(run, "nyle-pretelechargement-flux");
            th.setDaemon(true);
            th.setPriority(Thread.MIN_PRIORITY);
            return th;
        });
        try {
            for (int i = 0; i < FLUX; i++) flux.submit(travailleur);
            flux.shutdown();
            while (!flux.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            }
        } finally {
            flux.shutdownNow();
        }
        // Un fond qui s'est arrêté de lui-même (partie lancée…) remonte ici comme une interruption.
        Exception err = premiereErreur.get();
        if (err != null) throw err;
        if (t.arretee() || !restants.isEmpty()) throw new TelechargeurFichier.Interrompu();
    }

    /** Débit autorisé maintenant (octets/s, 0 = illimité). */
    private long debitVoulu(Tache t, long bandeMesuree) {
        if (t.priorite == Priorite.PREMIER_PLAN) {
            return conditions.jeuEnCours.getAsBoolean() ? plafondJeuEnCours : 0;
        }
        if (plafondFond > 0) return plafondFond;
        if (bandeMesuree < 0) return 0;  // phase de mesure
        return Math.max(plancherFond, bandeMesuree / 2);
    }

    private void appliquerPlan(EtatUnivers e, PlanPack.Resultat r) {
        e.total = r.octetsTotal();
        e.faits.set(r.octetsDeja());
        e.utiles = r.utiles();
    }

    private void terminer(EtatUnivers e, Tache t, Etat etat, String erreur) {
        synchronized (e) {
            if (e.tache == t) e.tache = null;
            e.etat = etat;
            e.erreur = erreur;
        }
        if (etat == Etat.PRET) LOG.info("Pré-téléchargement {} : prêt", t.univers);
        avertir(true);
    }

    private EtatUnivers etatDe(String univers) {
        String id = PlanPack.mode(univers).id;  // lève sur un univers inconnu
        return etats.computeIfAbsent(id, k -> new EtatUnivers());
    }

    private EtatUnivers etatOuNull(String univers) {
        try {
            return etatDe(univers);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Prévient l'interface ; pendant un téléchargement, au plus quatre fois par seconde. */
    private void avertir(boolean toujours) {
        long maintenant = System.nanoTime();
        if (!toujours && maintenant - dernierAvis < TimeUnit.MILLISECONDS.toNanos(250)) return;
        dernierAvis = maintenant;
        for (Runnable r : rappels) {
            try {
                r.run();
            } catch (Exception ex) {
                LOG.debug("rappel du pré-téléchargement : {}", ex.toString());
            }
        }
    }
}
