package fr.nylerp.launcher.ui.coquille;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.config.ModeDeJeu;
import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.jeu.LanceurJeu;
import fr.nylerp.launcher.ui.kit.Jetons;
import fr.nylerp.launcher.ui.kit.Polices;
import fr.nylerp.launcher.ui.univers.UniversNylerp;
import fr.nylerp.launcher.ui.univers.UniversPokenyle;
import fr.nylerp.launcher.univers.Services;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import fr.nylerp.launcher.ui.kit.BoutonRond;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * LA COQUILLE — l'écran principal de la nouvelle interface (refonte 2026-10, lot L5).
 *
 * <p>Elle porte un {@link LanceurJeu} (le lancement, partagé par les deux univers), les deux univers
 * (construits à la première visite, puis gardés), le choix d'univers du premier lancement, la bannière
 * de mise à jour du launcher. La boutique n'est PAS une vue du launcher : le bouton de l'en-tête ouvre la
 * boutique du site dans le navigateur.
 *
 * <p>Changer d'univers = {@link ModeDeJeu#definir} (instance, pack et serveur suivent, comme avec
 * l'ancien sélecteur) + {@link LanceurJeu#apresChangementUnivers()} + le dernier univers retenu dans
 * {@code Settings.universVu}. Interdit pendant un lancement ou un téléchargement : le segment est
 * verrouillé tant que le lanceur est occupé.
 */
public final class Coquille extends StackPane {

    private static final Logger LOG = LoggerFactory.getLogger(Coquille.class);
    /** Crochet des essais : fait échouer la construction d'un univers pour prouver la vue de repli. Nul en production. */
    static volatile java.util.function.Predicate<ModeDeJeu> echecConstructionSimule;

    private final double echelle;
    private final Account compte;
    private final LanceurJeu lanceur = new LanceurJeu();
    private final EnTete entete;
    private final StackPane scene = new StackPane();
    private final HBox banniere;
    private UniversPokenyle pokenyle;
    private UniversNylerp nylerp;
    private ModeDeJeu courant;
    private ChoixUnivers choix;
    /** Transition d'univers en cours (une seule à la fois : voir {@link #terminerBascule()}). */
    private Animation bascule;
    private boolean enFond;
    /** Groupe discret du bas à droite, commun aux deux univers : son, pause, réglages. */
    private final BoutonRond boutonSon = new BoutonRond(BoutonRond.SON);
    private final BoutonRond boutonPause = new BoutonRond(BoutonRond.PAUSE);
    private final BoutonRond boutonReglages = new BoutonRond(BoutonRond.ROUE);
    private final HBox outils;

    /**
     * @param surReglages   ouvre les paramètres
     * @param surDeconnexion retire le compte actif
     * @param surChangerCompte bascule vers un autre compte de la liste
     * @param surAjouterCompte ouvre l'écran de connexion « ajouter un compte »
     */
    public Coquille(double echelle, Account compte, Runnable surReglages, Runnable surDeconnexion,
                    Consumer<Account> surChangerCompte, Runnable surAjouterCompte) {
        this.echelle = echelle;
        this.compte = compte;
        double l = 1180 * echelle, h = 700 * echelle;
        setPrefSize(l, h);
        setMinSize(l, h);
        setMaxSize(l, h);
        setClip(new Rectangle(l, h));

        entete = new EnTete(compte);
        entete.setSurUnivers(i -> changerUnivers(i == 0 ? ModeDeJeu.POKENYLE : ModeDeJeu.NYLERP));
        entete.setSurBoutique(this::ouvrirBoutique);
        entete.setSurDiscord(() -> Liens.ouvrir(Services.etatPokenyle.discordUrl()));
        entete.setComptes(surChangerCompte, surAjouterCompte, surDeconnexion);
        lanceur.occupeProperty().addListener((o, a, occupe) -> entete.verrouillerUnivers(occupe));

        banniere = construireBanniere();
        StackPane.setAlignment(banniere, Pos.TOP_LEFT);
        StackPane.setMargin(banniere, new Insets(Jetons.rem(2.6), 0, 0, Jetons.rem(1.2)));

        boutonSon.setOnAction(e -> { UniversNylerp.basculerSon(); majOutils(); });
        Tooltip.install(boutonSon, new Tooltip("Couper / réactiver la musique"));
        boutonPause.setOnAction(e -> {
            if (courant == ModeDeJeu.NYLERP && nylerp != null) nylerp.basculerVideo();
            else if (courant == ModeDeJeu.POKENYLE && pokenyle != null) pokenyle.basculerAnimation();
            majOutils();
        });
        Tooltip.install(boutonPause, new Tooltip("Mettre en pause / relancer le fond animé"));
        boutonReglages.setRotationSurvol(60);
        boutonReglages.setOnAction(e -> surReglages.run());
        Tooltip.install(boutonReglages, new Tooltip("Paramètres"));
        outils = new HBox(Jetons.rem(0.5), boutonSon, boutonPause, boutonReglages);
        outils.setAlignment(Pos.CENTER_RIGHT);
        outils.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        outils.setPickOnBounds(false);
        StackPane.setAlignment(outils, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(outils, new Insets(0, Jetons.rem(1.4), Jetons.rem(1.2), 0));
        getChildren().addAll(scene, outils, banniere);

        // Le mode suit le dernier univers choisi AVANT la vérification du pack (qui lit le mode).
        String vu = Settings.get().universVu;
        ModeDeJeu retenu = "pokenyle".equals(vu) ? ModeDeJeu.POKENYLE : "nylerp".equals(vu) ? ModeDeJeu.NYLERP : null;
        if (retenu != null) ModeDeJeu.definir(retenu);
        lanceur.demarrerVerifications();

        if (retenu == null) {
            choix = new ChoixUnivers(echelle, compte, this::premierChoix);
            entete.masquerSegment(true);
            getChildren().add(choix);
        } else {
            montrer(retenu, false);
        }
    }

    // ── Pour LauncherApp et la barre de la fenêtre ──────────────────────────────────────────────

    /** Les éléments à poser à gauche de la poignée de la barre de la fenêtre. */
    public HBox elementsGauche() { return entete.gauche(); }
    /** Les éléments à poser à droite de la poignée (avant réduire / fermer). */
    public HBox elementsDroite() { return entete.droite(); }
    public LanceurJeu lanceur() { return lanceur; }

    /** Zone des univers (pour les essais de non-régression : jamais vide après un changement d'univers ou de compte). */
    StackPane sceneUnivers() { return scene; }

    /** Une cible venue d'une annonce : « pokenyle », « nylerp », « boutique ». */
    public void naviguer(String cible) {
        if (cible == null) return;
        switch (cible.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "pokenyle" -> changerUnivers(ModeDeJeu.POKENYLE);
            case "nylerp" -> changerUnivers(ModeDeJeu.NYLERP);
            case "boutique" -> ouvrirBoutique();
            default -> { }
        }
    }

    /** Fenêtre rangée dans la barre système : rien ne joue ni ne s'anime. */
    public void suspendre() {
        enFond = true;
        if (pokenyle != null) pokenyle.cacher();
        if (nylerp != null) nylerp.suspendre();
    }

    /** Fenêtre de retour : seul l'univers affiché reprend. */
    public void reprendre() {
        enFond = false;
        if (choix != null) return;
        if (courant == ModeDeJeu.POKENYLE && pokenyle != null) pokenyle.afficher();
        if (courant == ModeDeJeu.NYLERP && nylerp != null) nylerp.reprendre();
    }

    /** Une autre page (paramètres) passe devant : le ciel s'arrête, la musique de NyleRP continue (comme avant). */
    public void enArrierePlan() {
        if (pokenyle != null) pokenyle.cacher();
    }

    /** Cette coquille est remplacée (autre compte) : on rend vidéos et animations. */
    public void liberer() {
        suspendre();
        if (nylerp != null) nylerp.liberer();
    }

    // ── Boutique ────────────────────────────────────────────────────────────────────────────────

    /** Ouvre la boutique du site de l'univers affiché dans le navigateur du système (un clic = un onglet). */
    public void ouvrirBoutique() {
        Liens.boutique(courant == ModeDeJeu.POKENYLE ? "pokenyle" : "nylerp");
    }

    // ── Univers ─────────────────────────────────────────────────────────────────────────────────

    private void premierChoix(ModeDeJeu m) {
        choix = null;
        entete.masquerSegment(false);
        if (m != ModeDeJeu.courant()) {
            ModeDeJeu.definir(m);
            lanceur.apresChangementUnivers();
        }
        retenir(m);
        montrer(m, false);
    }

    private void changerUnivers(ModeDeJeu m) {
        if (m == courant) { entete.montrerUnivers(m == ModeDeJeu.POKENYLE ? 0 : 1); return; }
        if (lanceur.occupeProperty().get()) {           // jamais sous les pieds d'un lancement
            entete.montrerUnivers(courant == ModeDeJeu.POKENYLE ? 0 : 1);
            return;
        }
        if (choix != null) { getChildren().remove(choix); choix = null; entete.masquerSegment(false); }
        ModeDeJeu.definir(m);
        lanceur.apresChangementUnivers();
        retenir(m);
        montrer(m, true);
    }

    private static void retenir(ModeDeJeu m) {
        Settings s = Settings.get();
        s.universVu = m.id;
        s.save();
        fr.nylerp.launcher.annonces.Annonces.universOuvert(m.id);
    }

    private void montrer(ModeDeJeu m, boolean anime) {
        courant = m;
        majOutils();
        entete.montrerUnivers(m == ModeDeJeu.POKENYLE ? 0 : 1);
        // Une transition encore en cours est terminée d'abord. Son nettoyage différé retirait sinon le nœud que cette
        // bascule vient d'afficher (deux changements en moins de 420 ms), puis la seconde retirait le reste : zone vide.
        terminerBascule();
        Node nouveau = universPour(m);
        if (m == ModeDeJeu.POKENYLE) {
            if (nylerp != null) nylerp.suspendre();
            if (nouveau == pokenyle && !enFond) securise(pokenyle::afficher, "affichage de Pokényle");
        } else {
            if (pokenyle != null) pokenyle.cacher();
            if (nouveau == nylerp) securise(enFond ? nylerp::suspendre : nylerp::reprendre, "reprise de NyleRP");
        }
        ObservableList<Node> enfants = scene.getChildren();
        Node avant = enfants.isEmpty() ? null : enfants.get(enfants.size() - 1);
        if (avant == nouveau) avant = null;
        if (!enfants.contains(nouveau)) enfants.add(nouveau);
        nouveau.toFront();
        if (!anime || avant == null) {
            enfants.setAll(nouveau);
            nouveau.setOpacity(1);
            return;
        }
        nouveau.setOpacity(0);
        nouveau.setScaleX(1.03);
        nouveau.setScaleY(1.03);
        FadeTransition f = new FadeTransition(Duration.millis(420), nouveau);
        f.setToValue(1);
        ScaleTransition s = new ScaleTransition(Duration.millis(420), nouveau);
        s.setToX(1);
        s.setToY(1);
        ParallelTransition p = new ParallelTransition(f, s);
        p.setInterpolator(Jetons.SORTIE);
        p.setOnFinished(e -> {
            // setAll (et non retainAll) : l'univers affiché reste, quoi qu'il se soit passé entre-temps
            if (bascule == p) { bascule = null; scene.getChildren().setAll(nouveau); }
        });
        bascule = p;
        p.play();
    }

    /** Arrête la transition d'univers en cours (sans son nettoyage) et remet les nœuds présents à l'état normal. */
    private void terminerBascule() {
        if (bascule != null) {
            bascule.setOnFinished(null);
            bascule.stop();
            bascule = null;
        }
        for (Node n : scene.getChildren()) {
            n.setOpacity(1);
            n.setScaleX(1);
            n.setScaleY(1);
        }
    }

    /** L'univers à afficher. Une construction qui échoue ne laisse JAMAIS la zone vide : vue de repli avec « Réessayer ». */
    private Node universPour(ModeDeJeu m) {
        try {
            if (echecConstructionSimule != null && echecConstructionSimule.test(m)) throw new IllegalStateException("échec simulé (essai)");
            if (m == ModeDeJeu.POKENYLE) {
                if (pokenyle == null) pokenyle = new UniversPokenyle(echelle, compte, lanceur);
                return pokenyle;
            }
            if (nylerp == null) nylerp = new UniversNylerp(echelle, compte, lanceur);
            return nylerp;
        } catch (Throwable e) {
            LOG.error("Univers {} : construction impossible, vue de repli affichée", m, e);
            if (m == ModeDeJeu.POKENYLE) pokenyle = null; else nylerp = null;
            return vueRepli(m);
        }
    }

    /** Ciel fixe du site (ou nuit de NyleRP), un message court et « Réessayer » (reconstruit l'univers). */
    private Node vueRepli(ModeDeJeu m) {
        boolean poke = m == ModeDeJeu.POKENYLE;
        Text titre = new Text((poke ? "Pokényle" : "NyleRP") + " ne s'est pas affiché");
        titre.setFont(Polices.titre(Jetons.rem(1.1)));
        titre.setFill(poke ? Jetons.ENCRE : Color.WHITE);
        Text detail = new Text("Un petit souci d'affichage. Réessaie : tes données ne sont pas touchées.");
        detail.setFont(Polices.extra(Jetons.rem(0.62)));
        detail.setFill(poke ? Jetons.ENCRE : Color.WHITE);
        Button reessayer = new Button("Réessayer");
        reessayer.getStyleClass().add("kit-valider");
        reessayer.setOnAction(e -> montrer(m, true));
        VBox v = new VBox(Jetons.rem(0.6), titre, detail, reessayer);
        v.setAlignment(Pos.CENTER);
        StackPane repli = new StackPane(v);
        repli.setStyle(poke
                ? "-fx-background-color: linear-gradient(to bottom, #4f9fe0 0%, #8cc8f2 55%, #cfe9fb 75%, #8fc56a 76%, #5fa043 100%);"
                : "-fx-background-color: linear-gradient(to bottom, #1b1740, #3a2a62);");
        return repli;
    }

    private static void securise(Runnable r, String quoi) {
        try {
            r.run();
        } catch (Throwable e) {
            LOG.error("Coquille : échec de {}", quoi, e);
        }
    }

    /** Icônes du groupe du bas selon l'état : son coupé ou non, fond en pause ou non. */
    private void majOutils() {
        boutonSon.setIcone(UniversNylerp.sonCoupe() ? BoutonRond.SON_COUPE : BoutonRond.SON);
        boolean enPause = courant == ModeDeJeu.NYLERP
                ? (nylerp != null ? nylerp.videoCoupee() : Settings.get().launcherVideoDisabled)
                : UniversPokenyle.animationEnPause();
        boutonPause.setIcone(enPause ? BoutonRond.LECTURE : BoutonRond.PAUSE);
    }

    // ── Bannière de mise à jour du launcher ─────────────────────────────────────────────────────

    private HBox construireBanniere() {
        Circle point = new Circle(Jetons.rem(0.17), Color.web("#ff8a3d"));
        Text msg = new Text("Mise à jour disponible");
        msg.setFont(Polices.extra(Jetons.rem(0.56)));
        msg.setFill(Jetons.ENCRE);
        Button dl = new Button("Télécharger");
        dl.getStyleClass().add("kit-valider");
        dl.setStyle("-fx-font-size: " + Jetons.rem(0.52) + "px; -fx-padding: 3 10 3 10;");
        dl.setOnAction(e -> lanceur.telechargerMiseAJour(dl));
        HBox b = new HBox(Jetons.rem(0.4), point, msg, dl);
        b.setAlignment(Pos.CENTER_LEFT);
        b.getStyleClass().add("kit-capsule");
        b.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        b.setVisible(false);
        b.setManaged(false);
        lanceur.miseAJourProperty().addListener((o, a, info) -> {
            if (info == null) return;
            msg.setText("Mise à jour " + info.latestTag() + " disponible");
            b.setVisible(true);
            b.setManaged(true);
        });
        return b;
    }
}
