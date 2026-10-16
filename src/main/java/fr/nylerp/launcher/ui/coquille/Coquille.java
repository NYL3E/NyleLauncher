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
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * LA COQUILLE — l'écran principal de la nouvelle interface (refonte 2026-10, lot L5).
 *
 * <p>Elle porte un {@link LanceurJeu} (le lancement, partagé par les deux univers), les deux univers
 * (construits à la première visite, puis gardés), le choix d'univers du premier lancement, la bannière
 * de mise à jour du launcher et les vues de navigation (boutique…), affichées dans la zone de contenu
 * sous l'en-tête, avec un retour.
 *
 * <p>Changer d'univers = {@link ModeDeJeu#definir} (instance, pack et serveur suivent, comme avec
 * l'ancien sélecteur) + {@link LanceurJeu#apresChangementUnivers()} + le dernier univers retenu dans
 * {@code Settings.universVu}. Interdit pendant un lancement ou un téléchargement : le segment est
 * verrouillé tant que le lanceur est occupé.
 */
public final class Coquille extends StackPane {

    private final double echelle;
    private final Account compte;
    private final LanceurJeu lanceur = new LanceurJeu();
    private final EnTete entete;
    private final StackPane scene = new StackPane();
    private final StackPane vues = new StackPane();
    private final HBox banniere;
    private UniversPokenyle pokenyle;
    private UniversNylerp nylerp;
    private ModeDeJeu courant;
    private ChoixUnivers choix;
    private Node vueNavigation;
    private boolean enFond;

    private BiConsumer<String, String> surBoutique = (univers, pseudo) -> Liens.boutique(univers);

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
        entete.setSurReglages(surReglages);
        entete.setSurBoutique(this::ouvrirBoutique);
        entete.setSurDiscord(() -> Liens.ouvrir(Services.etatPokenyle.discordUrl()));
        entete.setSurRetour(this::retour);
        entete.setComptes(surChangerCompte, surAjouterCompte, surDeconnexion);
        lanceur.occupeProperty().addListener((o, a, occupe) -> entete.verrouillerUnivers(occupe));

        banniere = construireBanniere();
        StackPane.setAlignment(banniere, Pos.TOP_LEFT);
        StackPane.setMargin(banniere, new Insets(Jetons.rem(2.6), 0, 0, Jetons.rem(1.2)));

        // les vues de navigation commencent SOUS la barre de la fenêtre (en-tête toujours visible)
        vues.setPadding(new Insets(Jetons.rem(2.3), 0, 0, 0));
        vues.setPickOnBounds(false);
        getChildren().addAll(scene, vues, banniere);

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

    /**
     * Ce que fait le bouton « Boutique » de l'en-tête : reçoit l'univers affiché (« pokenyle » ou
     * « nylerp ») et le pseudo. Par défaut, la boutique du site s'ouvre dans le navigateur ; l'intégration
     * branche ici la boutique intégrée ({@code Boutique.ouvrir}) via {@link #afficherVue(Node)}.
     */
    public void setSurBoutique(BiConsumer<String, String> r) {
        surBoutique = r == null ? (u, p) -> Liens.boutique(u) : r;
    }

    /** Une cible venue d'une annonce : « pokenyle », « nylerp », « boutique ». */
    public void naviguer(String cible) {
        if (cible == null) return;
        switch (cible.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "pokenyle" -> { retourSansAnimation(); changerUnivers(ModeDeJeu.POKENYLE); }
            case "nylerp" -> { retourSansAnimation(); changerUnivers(ModeDeJeu.NYLERP); }
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
        if (vueNavigation != null || choix != null) return;
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

    // ── Vues de navigation (boutique…) ──────────────────────────────────────────────────────────

    /** Affiche une vue dans la zone de contenu, sous l'en-tête ; « Retour » la referme. */
    public void afficherVue(Node vue) {
        if (vue == null) return;
        vueNavigation = vue;
        if (pokenyle != null) pokenyle.cacher();
        vues.setStyle("-fx-background-color: #eaf7ff;");
        vues.getChildren().setAll(vue);
        entete.modeRetour(true);
        vue.setOpacity(0);
        FadeTransition f = new FadeTransition(Duration.millis(220), vue);
        f.setToValue(1);
        f.play();
    }

    /** Referme la vue de navigation ouverte (sans effet s'il n'y en a pas). */
    public void retour() {
        if (vueNavigation == null) return;
        Node v = vueNavigation;
        FadeTransition f = new FadeTransition(Duration.millis(180), v);
        f.setToValue(0);
        f.setOnFinished(e -> { vues.getChildren().remove(v); if (vueNavigation == null) vues.setStyle(""); });
        f.play();
        vueNavigation = null;
        entete.modeRetour(false);
        if (!enFond) reprendre();
    }

    private void retourSansAnimation() {
        if (vueNavigation == null) return;
        vues.getChildren().clear();
        vues.setStyle("");
        vueNavigation = null;
        entete.modeRetour(false);
    }

    private void ouvrirBoutique() {
        String univers = courant == ModeDeJeu.POKENYLE ? "pokenyle" : "nylerp";
        surBoutique.accept(univers, compte == null ? null : compte.username());
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
    }

    private void montrer(ModeDeJeu m, boolean anime) {
        ModeDeJeu ancien = courant;
        courant = m;
        entete.montrerUnivers(m == ModeDeJeu.POKENYLE ? 0 : 1);
        Node nouveau;
        if (m == ModeDeJeu.POKENYLE) {
            if (pokenyle == null) pokenyle = new UniversPokenyle(echelle, compte, lanceur);
            nouveau = pokenyle;
            if (nylerp != null) nylerp.suspendre();
            if (!enFond) pokenyle.afficher();
        } else {
            if (nylerp == null) nylerp = new UniversNylerp(echelle, compte, lanceur);
            nouveau = nylerp;
            if (pokenyle != null) pokenyle.cacher();
            if (!enFond) nylerp.reprendre();
            else nylerp.suspendre();
        }
        Node avant = ancien == null ? null : (ancien == ModeDeJeu.POKENYLE ? pokenyle : nylerp);
        if (!scene.getChildren().contains(nouveau)) scene.getChildren().add(nouveau);
        nouveau.toFront();
        if (!anime || avant == null) {
            scene.getChildren().retainAll(nouveau);
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
        p.setOnFinished(e -> scene.getChildren().retainAll(nouveau));
        p.play();
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
