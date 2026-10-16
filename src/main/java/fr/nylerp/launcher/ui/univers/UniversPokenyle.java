package fr.nylerp.launcher.ui.univers;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.fond.Fond;
import fr.nylerp.launcher.jeu.LanceurJeu;
import fr.nylerp.launcher.ui.SelecteurMode;
import fr.nylerp.launcher.ui.kit.BoutonJouer;
import fr.nylerp.launcher.ui.kit.BoutonPilule;
import fr.nylerp.launcher.ui.kit.CarteVerre;
import fr.nylerp.launcher.ui.kit.Jetons;
import fr.nylerp.launcher.ui.kit.Polices;
import fr.nylerp.launcher.ui.kit.TitreEclats;
import fr.nylerp.launcher.univers.PreTelechargeur;
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
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * L'UNIVERS POKÉNYLE — le haut de page du site : ciel animé (nuées et Ho-Oh), fond aux coffres-
 * Pokéballs, titre en Northura, gros bouton du kit.
 *
 * <p>Trois états, lus sur {@link Services#etatPokenyle} et {@link Services#preTelechargeur} (jamais une
 * implémentation) :
 * <ul>
 *   <li><b>ouvert pour ce joueur</b> (ouverture générale, testeur distant, ou testeur historique de
 *       {@link SelecteurMode}) : « Jouer », branché sur le {@link LanceurJeu} ;</li>
 *   <li><b>compte crack</b> alors que Pokényle ne les accepte pas encore : le message distant, honnête —
 *       jamais un lancement voué à l'échec ;</li>
 *   <li><b>pas encore ouvert</b> : compte à rebours (date connue) ou libellé (« mi-décembre »),
 *       pré-téléchargement du pack s'il existe, « Me prévenir », liste d'attente si Discord est actif.</li>
 * </ul>
 */
public final class UniversPokenyle extends StackPane {

    private final double largeur, hauteur;
    private final Account compte;
    private final ImageView fond = new ImageView();
    private final CielPokenyle ciel;
    private final StackPane zoneAction = new StackPane();
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
        fond.setFitWidth(largeur * 1.025);
        fond.setFitHeight(hauteur * 1.025);
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

        // ── titre ──
        TitreEclats titre = new TitreEclats("Pokényle", TitreEclats.Style.PRINCIPAL, Jetons.rem(3.6), null);

        blocOuvert = new BlocLancement(lanceur, false, "POKÉNYLE", Pos.TOP_LEFT);
        zoneAction.setAlignment(Pos.TOP_LEFT);
        zoneAction.setPickOnBounds(false);
        zoneAction.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        VBox gauche = new VBox(Jetons.rem(0.4), titre, zoneAction);
        VBox.setMargin(zoneAction, new Insets(Jetons.rem(1.1), 0, 0, -Jetons.rem(0.6)));
        gauche.setAlignment(Pos.TOP_LEFT);
        gauche.setPickOnBounds(false);
        gauche.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(gauche, Pos.TOP_LEFT);
        StackPane.setMargin(gauche, new Insets(Jetons.rem(4.6), 0, 0, Jetons.rem(2.4)));

        getChildren().addAll(fond, ciel, voile, gauche, ciel.avant());

        seconde = new Timeline(new KeyFrame(Duration.seconds(1), e -> majCompteur()));
        seconde.setCycleCount(Timeline.INDEFINITE);
        Services.etatPokenyle.surChangement(() -> Platform.runLater(this::rafraichir));
        Services.preTelechargeur.surChangement(() -> Platform.runLater(this::rafraichir));
        rafraichir();
    }

    // ── Cycle de vie (coquille) ──────────────────────────────────────────────────────────────────

    /** L'univers devient visible : le ciel s'anime, le compte à rebours tourne. */
    public void afficher() {
        ciel.demarrer();
        seconde.play();
        rafraichir();
    }

    /** L'univers est caché (autre univers, fenêtre rangée) : rien ne tourne. */
    public void cacher() {
        ciel.arreter();
        seconde.stop();
    }

    public CielPokenyle ciel() { return ciel; }

    /** Vrai si ce joueur peut lancer Pokényle maintenant. */
    public static boolean ouvertPour(Account compte) {
        String pseudo = compte == null ? null : compte.username();
        return Services.etatPokenyle.ouvertPour(pseudo) || SelecteurMode.estAutorise(compte);
    }

    /** Vrai si ce compte est un compte crack que Pokényle refuse encore. */
    public static boolean crackRefuse(Account compte) {
        return compte != null && compte.isOffline() && !Services.etatPokenyle.crackAutorise();
    }

    // ── États ────────────────────────────────────────────────────────────────────────────────────

    /** Reconstruit la zone d'action si l'état a changé (sinon, seul le compteur bouge). */
    public void rafraichir() {
        SourceEtatPokenyle s = Services.etatPokenyle;
        PreTelechargeur pt = Services.preTelechargeur;
        String cle;
        if (crackRefuse(compte)) cle = "crack|" + s.messageCrack() + "|" + s.discordActif();
        else if (ouvertPour(compte)) cle = "ouvert";
        else cle = "attente|" + s.ouverture().isPresent() + "|" + s.libelleOuverture() + "|" + s.discordActif()
                + "|" + (pt == PreTelechargeur.AUCUN ? "-" : pt.etat("pokenyle").name());
        if (cle.equals(etatAffiche)) {
            majCompteur();
            majPreTelechargement();
            return;
        }
        etatAffiche = cle;
        Node contenu;
        if (cle.startsWith("crack")) contenu = vueCrack(s);
        else if (cle.equals("ouvert")) contenu = blocOuvert;
        else contenu = vueAttente(s, pt);
        zoneAction.getChildren().setAll(contenu);
        contenu.setOpacity(0);
        FadeTransition f = new FadeTransition(Duration.millis(220), contenu);
        f.setToValue(1);
        f.play();
        majCompteur();
    }

    private Node vueCrack(SourceEtatPokenyle s) {
        Text t = new Text(s.messageCrack());
        t.setFont(Polices.extra(Jetons.rem(0.7)));
        t.setFill(Jetons.ENCRE);
        TextFlow flux = new TextFlow(t);
        flux.setMaxWidth(Jetons.rem(15.5));
        flux.setPrefWidth(Jetons.rem(15.5));
        VBox v = new VBox(Jetons.rem(0.45), flux, rangeeSecondaire(s, true));
        CarteVerre carte = new CarteVerre(CarteVerre.Variante.CLAIRE, v);
        carte.setPadding(new Insets(Jetons.rem(0.9)));
        carte.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane enveloppe = new StackPane(carte);
        enveloppe.setPadding(new Insets(0, 0, 0, Jetons.rem(0.6)));
        enveloppe.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return enveloppe;
    }

    private Node vueAttente(SourceEtatPokenyle s, PreTelechargeur pt) {
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
        VBox.setMargin(capsule, new Insets(0, 0, Jetons.rem(0.2), Jetons.rem(0.6)));

        VBox v = new VBox(Jetons.rem(0.35));
        v.setAlignment(Pos.TOP_LEFT);
        v.getChildren().add(capsule);

        boolean preDispo = pt != PreTelechargeur.AUCUN;
        BoutonJouer principal = new BoutonJouer(preDispo ? libellePreTelechargement(pt) : "Me prévenir");
        principal.setFiligrane("POKÉNYLE");
        principal.setParticules(true);
        principal.setId("principal");
        if (preDispo) {
            PreTelechargeur.Etat e = pt.etat("pokenyle");
            principal.setDisable(e == PreTelechargeur.Etat.PRET);
            principal.setOnAction(ev -> {
                PreTelechargeur.Etat et = pt.etat("pokenyle");
                if (et == PreTelechargeur.Etat.EN_COURS) pt.annuler("pokenyle");
                else if (et != PreTelechargeur.Etat.PRET) pt.demarrer("pokenyle");
            });
        } else {
            principal.setOnAction(ev -> mePrevenir());
        }
        v.getChildren().add(principal);


        HBox secondaire = rangeeSecondaire(s, preDispo);
        if (!secondaire.getChildren().isEmpty()) {
            VBox.setMargin(secondaire, new Insets(Jetons.rem(0.3), 0, 0, Jetons.rem(0.6)));
            v.getChildren().add(secondaire);
        }
        return v;
    }

    /** « Me prévenir » (si le bouton principal sert déjà au pré-téléchargement) et liste d'attente. */
    private HBox rangeeSecondaire(SourceEtatPokenyle s, boolean avecPrevenir) {
        HBox h = new HBox(Jetons.rem(0.5));
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPickOnBounds(false);
        if (avecPrevenir) {
            HBox prevenir = Pilules.claire("Me prévenir", Pilules.SVG_CLOCHE);
            prevenir.setOnMouseClicked(e -> mePrevenir());
            h.getChildren().add(prevenir);
        }
        if (s.discordActif()) {
            BoutonPilule discord = new BoutonPilule("Liste d'attente", BoutonPilule.Variante.DISCORD);
            discord.setOnAction(e -> UniversNylerp.ouvrirNavigateur(s.discordUrl()));
            h.getChildren().add(discord);
        }
        return h;
    }

    /** Garde les annonces actives (c'est par elles que vient l'alerte de la veille) et le confirme. */
    private void mePrevenir() {
        Settings r = Settings.get();
        r.notifications = true;
        r.save();
        Fond.appliquerReglages();
        Fond.notifier("C'est noté", "Le launcher te préviendra la veille et le jour de l'ouverture de Pokényle.", "pokenyle");
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

    private void majPreTelechargement() {
        PreTelechargeur pt = Services.preTelechargeur;
        if (pt == PreTelechargeur.AUCUN || !etatAffiche.startsWith("attente")) return;
        Node n = zoneAction.lookup("#principal");
        if (n instanceof BoutonJouer b) b.setTexte(libellePreTelechargement(pt));
    }

    private static String libellePreTelechargement(PreTelechargeur pt) {
        return switch (pt.etat("pokenyle")) {
            case EN_COURS -> "Téléchargement " + Math.round(pt.progression("pokenyle") * 100) + " %";
            case PRET -> "Prêt pour l'ouverture";
            case ERREUR -> "Réessayer le téléchargement";
            default -> "Pré-télécharger (≈ 1 Go)";
        };
    }
}
