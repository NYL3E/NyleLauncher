package fr.nylerp.launcher.ui.univers;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.fond.Fond;
import fr.nylerp.launcher.jeu.LanceurJeu;
import fr.nylerp.launcher.ui.SelecteurMode;
import fr.nylerp.launcher.ui.kit.BoutonJouer;
import fr.nylerp.launcher.ui.kit.BoutonPilule;
import fr.nylerp.launcher.ui.kit.Jetons;
import fr.nylerp.launcher.ui.kit.Polices;
import fr.nylerp.launcher.univers.AccesPokenyle;
import fr.nylerp.launcher.univers.Services;
import fr.nylerp.launcher.univers.SourceEtatPokenyle;
import fr.nylerp.launcher.update.CheminsMedias;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * L'UNIVERS POKÉNYLE — le haut de page du site : ciel animé (nuées et Ho-Oh), fond aux coffres-
 * Pokéballs, gros logo officiel, gros bouton du kit.
 *
 * <p>Deux états, lus sur {@link Services#etatPokenyle} (jamais une implémentation) :
 * <ul>
 *   <li><b>ouvert pour ce joueur</b> (ouverture générale, testeur distant, ou testeur historique de
 *       {@link SelecteurMode}) : « Jouer », branché sur le {@link LanceurJeu} ;</li>
 *   <li><b>pas encore ouvert</b> : compte à rebours (date connue) ou libellé (« mi-décembre »), « Me prévenir »,
 *       liste d'attente si Discord est actif. AUCUN pré-téléchargement : il est réservé à ceux qui peuvent déjà
 *       jouer (décision de l'owner, 16/10/2026).</li>
 * </ul>
 * Aucune variante selon le type de compte : un compte crack voit EXACTEMENT les mêmes états qu'un premium. C'est
 * le serveur qui décide qui peut entrer, pas le launcher (décision de l'owner, 16/10/2026).
 */
public final class UniversPokenyle extends StackPane {

    /** Bord gauche visible commun au logo et au bouton (même colonne). */
    private static double margeGauche() { return Jetons.rem(1.8); }
    /** Distance du pied du bloc au bas de la fenêtre : celle du groupe son / pause / réglages (Coquille). */
    private static double margeBasse() { return Jetons.rem(1.2); }
    private static double haloBas() { return Jetons.rem(0.8); }

    /** Agrandissement du fond (1 = cadrage du site). */
    private static final double ZOOM_FOND = 1.14;

    private final double largeur, hauteur;
    private final Account compte;
    private final ImageView fond = new ImageView();
    private final CielPokenyle ciel;
    private final StackPane zoneAction = new StackPane();
    /** La capsule « Ouverture mi-décembre » / compte à rebours : sous le logo, dans le ciel (jamais sur les Poké Balls). */
    private final StackPane zoneEtat = new StackPane();
    private final LogoPokenyle logo;
    private final BlocLancement blocOuvert;
    private final Timeline seconde;
    private String etatAffiche = "";
    private Text compteur;

    public UniversPokenyle(double echelle, Account compte, LanceurJeu lanceur) {
        this.largeur = 1180 * echelle;
        this.hauteur = 700 * echelle;
        this.compte = compte;
        setPrefSize(largeur, hauteur);
        setMinSize(largeur, hauteur);
        setMaxSize(largeur, hauteur);
        setClip(new Rectangle(largeur, hauteur));
        // ciel du site en attendant l'image (premier démarrage, paquet de médias pas encore là)
        setStyle("-fx-background-color: linear-gradient(to bottom, #4f9fe0 0%, #8cc8f2 55%, #cfe9fb 75%, #8fc56a 76%, #5fa043 100%);");

        // fond : un poil plus grand que la fenêtre, pour que la parallaxe ne découvre jamais un bord
        fond.setPreserveRatio(false);
        // cadrage : l'image est agrandie et calée en bas à gauche, pour que la Poké Ball de gauche sorte de l'emprise du
        // bloc de lancement (bas à gauche) ; la parallaxe garde ainsi une marge de chaque côté.
        fond.setFitWidth(largeur * ZOOM_FOND);
        fond.setFitHeight(hauteur * ZOOM_FOND);
        StackPane.setAlignment(fond, Pos.BOTTOM_LEFT);
        fond.setSmooth(true);
        fond.setOpacity(0);
        ImagesDistantes.charger(CheminsMedias.POKENYLE_FOND_2X, "/images/univers/fond-pokenyle-1x.jpg", 0, 0, img -> {
            boolean premier = fond.getImage() == null;
            fond.setImage(img);
            if (premier) {
                FadeTransition f = new FadeTransition(Duration.millis(450), fond);
                f.setToValue(1);
                f.play();
            }
        });

        ciel = new CielPokenyle(echelle);
        fond.translateXProperty().bind(ciel.parallaxeXProperty().multiply(-6 * echelle));
        fond.translateYProperty().bind(ciel.parallaxeYProperty().multiply(-4 * echelle));
        setOnMouseMoved(e -> ciel.viser(e.getX() / largeur * 2 - 1, e.getY() / hauteur * 2 - 1));

        // voile clair à gauche : le texte reste lisible sur les nuages
        Region voile = new Region();
        voile.setMouseTransparent(true);
        voile.setStyle("-fx-background-color: linear-gradient(to right, rgba(30,80,150,0.30) 0%, rgba(30,80,150,0.0) 46%);");

        // ── logo (remplace le titre en texte) ──
        logo = new LogoPokenyle();

        blocOuvert = new BlocLancement(lanceur, false, "POKÉNYLE", Pos.TOP_LEFT);
        zoneAction.setAlignment(Pos.TOP_LEFT);
        zoneAction.setPickOnBounds(false);
        zoneAction.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        // colonne gauche : le logo en haut, le bloc de lancement ancré en bas à gauche. Même ligne de base que le groupe
        // son / pause / réglages du coin opposé (marge basse de la coquille), et même bord gauche visible que le logo.
        zoneEtat.setAlignment(Pos.TOP_LEFT);
        zoneEtat.setPickOnBounds(false);
        zoneEtat.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        VBox haut = new VBox(Jetons.rem(0.55), logo, zoneEtat);
        haut.setAlignment(Pos.TOP_LEFT);
        haut.setPickOnBounds(false);
        haut.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(haut, Pos.TOP_LEFT);
        StackPane.setMargin(haut, new Insets(Jetons.rem(3.3), 0, 0, margeGauche()));
        // la zone d'action contient halo et marge interne de la pilule (0,6 rem) : on les retire pour que le bord VISIBLE
        // du bouton tombe sur margeGauche() et que son pied tombe sur margeBasse().
        StackPane.setAlignment(zoneAction, Pos.BOTTOM_LEFT);
        poserPied(null);

        // voile doux sombre en bas à gauche : le texte blanc reste lisible sur l'herbe, sans bloc visible
        Region voileBas = new Region();
        voileBas.setMouseTransparent(true);
        voileBas.setStyle("-fx-background-color: radial-gradient(center 0% 100%, radius 62%, rgba(6,34,24,0.46) 0%,"
                + " rgba(6,34,24,0.22) 45%, rgba(6,34,24,0.0) 100%);");

        getChildren().addAll(fond, ciel, voile, voileBas, haut, zoneAction, ciel.avant());

        seconde = new Timeline(new KeyFrame(Duration.seconds(1), e -> majCompteur()));
        seconde.setCycleCount(Timeline.INDEFINITE);
        Services.etatPokenyle.surChangement(() -> Platform.runLater(this::rafraichir));
        rafraichir();
    }

    // ── Cycle de vie (coquille) ──────────────────────────────────────────────────────────────────

    /** L'univers devient visible : le ciel s'anime, le compte à rebours tourne. */
    public void afficher() {
        if (!animationEnPause) ciel.demarrer();
        seconde.play();
        rafraichir();
        logo.entrer();
    }

    /** L'univers est caché (autre univers, fenêtre rangée) : rien ne tourne. */
    public void cacher() {
        ciel.arreter();
        seconde.stop();
    }

    public CielPokenyle ciel() { return ciel; }

    /** Pause du ciel demandée par le joueur (bouton du bas) : le ciel reste figé jusqu'à la reprise. */
    private static boolean animationEnPause;

    public static boolean animationEnPause() { return animationEnPause; }

    /** Met le ciel en pause ou le relance (bouton pause du groupe du bas). */
    public void basculerAnimation() {
        animationEnPause = !animationEnPause;
        if (animationEnPause) ciel.arreter(); else ciel.demarrer();
    }

    /** Vrai si ce joueur peut lancer Pokényle maintenant. */
    public static boolean ouvertPour(Account compte) {
        return AccesPokenyle.autorise(compte);
    }


    /**
     * Pied du bloc : le dernier élément VISIBLE tombe sur la ligne de base du groupe son / pause / réglages. Une pilule
     * a un halo dessous (compris dans la zone, pas dans ce qu'on voit) ; la rangée de boutons secondaires n'en a pas.
     */
    private void poserPied(Node contenu) {
        boolean pilule = contenu == null || contenu == blocOuvert || !(contenu instanceof VBox v && v.getChildren().size() > 1);
        double bas = pilule ? margeBasse() - haloBas() : margeBasse();
        StackPane.setMargin(zoneAction, new Insets(0, 0, Math.max(0, bas), margeGauche() - Jetons.rem(0.6)));
    }

    // ── États ────────────────────────────────────────────────────────────────────────────────────

    /** Reconstruit la zone d'action si l'état a changé (sinon, seul le compteur bouge). */
    public void rafraichir() {
        SourceEtatPokenyle s = Services.etatPokenyle;
        String cle;
        if (ouvertPour(compte)) cle = "ouvert";
        else cle = "attente|" + s.ouverture().isPresent() + "|" + s.libelleOuverture() + "|" + s.discordActif();
        if (cle.equals(etatAffiche)) {
            majCompteur();
            return;
        }
        etatAffiche = cle;
        Node contenu;
        if (cle.equals("ouvert")) contenu = blocOuvert;
        else contenu = vueAttente(s);
        if (cle.equals("ouvert")) zoneEtat.getChildren().clear();
        zoneAction.getChildren().setAll(contenu);
        poserPied(contenu);
        contenu.setOpacity(0);
        FadeTransition f = new FadeTransition(Duration.millis(220), contenu);
        f.setToValue(1);
        f.play();
        majCompteur();
    }

    private Node vueAttente(SourceEtatPokenyle s) {
        // compte à rebours (date connue) ou libellé
        Text quand = new Text(s.ouverture().isPresent() ? "Ouverture dans" : "Ouverture");
        quand.setFont(Polices.extra(Jetons.rem(0.62)));
        quand.setFill(Color.web("#8fa1c2"));
        compteur = new Text();
        compteur.setFont(Polices.titre(Jetons.rem(0.9)));
        compteur.setFill(Jetons.ENCRE);
        HBox capsule = new HBox(Jetons.rem(0.4), quand, compteur);
        capsule.setAlignment(Pos.CENTER_LEFT);
        capsule.setPadding(new Insets(Jetons.rem(0.45), Jetons.rem(0.9), Jetons.rem(0.45), Jetons.rem(0.9)));
        capsule.setStyle("-fx-background-color: rgba(255,255,255,0.86); -fx-background-radius: 99;"
                + "-fx-border-color: white; -fx-border-radius: 99; -fx-border-width: 1.5;");
        capsule.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(40, 80, 150, 0.18), Jetons.rem(0.9), 0, 0, Jetons.rem(0.2)));
        capsule.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        zoneEtat.getChildren().setAll(capsule);

        VBox v = new VBox(Jetons.rem(0.35));
        v.setAlignment(Pos.TOP_LEFT);

        BoutonJouer principal = new BoutonJouer("Me prévenir");
        principal.setFiligrane("POKÉNYLE");
        principal.setParticules(true);
        principal.setId("principal");
        principal.setOnAction(ev -> mePrevenir());
        v.getChildren().add(principal);


        HBox secondaire = rangeeSecondaire(s);
        if (!secondaire.getChildren().isEmpty()) {
            VBox.setMargin(secondaire, new Insets(Jetons.rem(0.3), 0, 0, Jetons.rem(0.6)));
            v.getChildren().add(secondaire);
        }
        return v;
    }

    /** Liste d'attente (si Discord est actif). */
    private HBox rangeeSecondaire(SourceEtatPokenyle s) {
        HBox h = new HBox(Jetons.rem(0.5));
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPickOnBounds(false);
        if (s.discordActif()) {
            BoutonPilule discord = new BoutonPilule("Liste d'attente", BoutonPilule.Variante.DISCORD);
            discord.setOnAction(e -> UniversNylerp.ouvrirNavigateur(s.discordUrl()));
            h.getChildren().add(discord);
        }
        return h;
    }

    /** Garde les annonces actives (c'est par elles que vient l'alerte d'ouverture) et le confirme, honnêtement. */
    private void mePrevenir() {
        Settings r = Settings.get();
        r.notifications = true;
        r.save();
        Fond.appliquerReglages();
        // La promesse n'est tenue que si le launcher tourne en fond le jour J : barre système gardée ET démarrage
        // avec l'ordinateur. Sinon on le dit, au lieu de promettre une alerte qui ne viendrait pas.
        boolean enFond = r.fondActif && r.demarrageAuto && r.accueilVu;
        Fond.notifier("C'est noté",
                enFond ? "Le launcher te préviendra à l'approche de l'ouverture de Pokényle."
                       : "Pour être prévenu, active « Lancer au démarrage » dans les réglages : le launcher doit rester en fond.",
                "pokenyle");
    }

    private void majCompteur() {
        if (compteur == null || !etatAffiche.startsWith("attente")) return;
        Optional<ZonedDateTime> date = Services.etatPokenyle.ouverture();
        if (date.isEmpty()) {
            compteur.setText(Services.etatPokenyle.libelleOuverture().toUpperCase(java.util.Locale.FRENCH));
            return;
        }
        long s = java.time.Duration.between(ZonedDateTime.now(), date.get()).getSeconds();
        if (s <= 0) { compteur.setText("IMMINENTE"); return; }
        long j = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60, sec = s % 60;
        compteur.setText(j > 0 ? String.format("%d J  %02d H  %02d MIN  %02d S", j, h, m, sec)
                : String.format("%02d H  %02d MIN  %02d S", h, m, sec));
    }
}
