package fr.nylerp.launcher.annonces;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Les annonces du Studio, montrées en carte dans le coin de l'écran (contrat L7, règles 4 à 9).
 *
 * <p>À chaque cycle : lecture de {@code /api/launcher/annonces}, puis au plus {@value #PAR_CYCLE}
 * cartes, une à la fois, espacées d'au moins 30 s après la fin de la précédente. Chaque carte est
 * revérifiée juste avant d'être montrée (réglage, déjà vue, toujours en cours) : entre deux cartes,
 * le joueur a pu couper les notifications ou l'annonce a pu expirer.
 */
final class ServiceAnnonces {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceAnnonces.class);

    static final int PAR_CYCLE = 3;
    /** Durée d'affichage d'une carte (8 s) + 30 s d'écart minimum avant la suivante. */
    static final Duration ESPACEMENT = Duration.ofSeconds(38);

    enum TypeCompte { AUCUN, PREMIUM, CRACK }

    /** Ce qui montre la carte : {@code Fond.notifier} en vrai, un enregistreur dans les tests. */
    interface Afficheur { void afficher(String titre, String texte, String cible); }

    private final PointAcces point;
    private final HorlogeServeur horloge;
    private final VuesAnnonces vues;
    private final Supplier<Set<String>> universJoues;
    private final Supplier<TypeCompte> compte;
    private final BooleanSupplier notificationsActives;
    private final Afficheur afficheur;
    private final ScheduledExecutorService planif;
    private final Duration espacement;
    private volatile boolean sequenceEnCours;

    ServiceAnnonces(PointAcces point, HorlogeServeur horloge, VuesAnnonces vues,
                    Supplier<Set<String>> universJoues, Supplier<TypeCompte> compte,
                    BooleanSupplier notificationsActives, Afficheur afficheur,
                    ScheduledExecutorService planif, Duration espacement) {
        this.point = point;
        this.horloge = horloge;
        this.vues = vues;
        this.universJoues = universJoues;
        this.compte = compte;
        this.notificationsActives = notificationsActives;
        this.afficheur = afficheur;
        this.planif = planif;
        this.espacement = espacement;
    }

    /** Un cycle : lecture puis programmation des cartes. Faux si le point d'accès a échoué. */
    boolean rafraichir() {
        PointAcces.Resultat r = point.rafraichir();
        programmer();
        return r != PointAcces.Resultat.ECHEC;
    }

    /** Annonces à montrer maintenant, dans l'ordre, au plus {@value #PAR_CYCLE}. */
    List<Annonce> aMontrer() {
        Optional<OffsetDateTime> maintenant = horloge.maintenant();
        if (maintenant.isEmpty()) return List.of(); // jamais d'après l'horloge du PC seule
        Set<String> joues = universJoues.get();
        TypeCompte tc = compte.get();
        List<Annonce> l = new ArrayList<>();
        for (Annonce a : toutes()) {
            if (a.enCours(maintenant.get()) && cibleCorrespond(a, joues, tc) && !vues.vue(a.id())) l.add(a);
        }
        l.sort(Comparator.comparingInt(Annonce::priorite).reversed().thenComparing(Annonce::debut));
        return l.size() > PAR_CYCLE ? List.copyOf(l.subList(0, PAR_CYCLE)) : List.copyOf(l);
    }

    static boolean cibleCorrespond(Annonce a, Set<String> joues, TypeCompte tc) {
        boolean univers = "tous".equals(a.univers()) || joues.contains(a.univers());
        boolean compteOk = switch (a.compte()) {
            case "premium" -> tc == TypeCompte.PREMIUM;
            case "crack" -> tc == TypeCompte.CRACK;
            default -> true;
        };
        return univers && compteOk;
    }

    private List<Annonce> toutes() {
        JsonObject corps = point.corps();
        if (corps == null || !corps.has("annonces") || !corps.get("annonces").isJsonArray()) return List.of();
        List<Annonce> l = new ArrayList<>();
        for (JsonElement e : corps.getAsJsonArray("annonces")) {
            Annonce a = Annonce.depuis(e);
            if (a != null) l.add(a);
        }
        return l;
    }

    private void programmer() {
        if (sequenceEnCours || !notificationsActives.getAsBoolean()) return;
        List<Annonce> l = aMontrer();
        if (l.isEmpty()) return;
        sequenceEnCours = true;
        montrer(l, 0);
    }

    private void montrer(List<Annonce> l, int i) {
        try {
            if (i >= l.size() || !notificationsActives.getAsBoolean()) { sequenceEnCours = false; return; }
            Annonce a = l.get(i);
            Optional<OffsetDateTime> maintenant = horloge.maintenant();
            boolean valable = maintenant.isPresent() && a.enCours(maintenant.get()) && !vues.vue(a.id())
                    && cibleCorrespond(a, universJoues.get(), compte.get());
            if (valable) {
                vues.marquer(a.id()); // « vue » dès que la carte s'affiche, clic ou non
                LOG.info("Annonces : carte « {} » (id {})", a.titre(), a.id());
                afficheur.afficher(a.titre(), a.texte(), a.cible());
            }
            if (i + 1 >= l.size()) { sequenceEnCours = false; return; }
            long attente = valable ? espacement.toMillis() : 0;
            planif.schedule(() -> montrer(l, i + 1), attente, TimeUnit.MILLISECONDS);
        } catch (Throwable t) {
            sequenceEnCours = false;
            LOG.warn("Annonces : carte en échec : {}", t.toString());
        }
    }

    boolean sequenceEnCours() { return sequenceEnCours; }
}
