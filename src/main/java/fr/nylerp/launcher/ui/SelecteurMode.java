package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.config.ModeDeJeu;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

/**
 * LE SÉLECTEUR DE MODE — troisième pastille, à gauche du son et de la vidéo.
 *
 * <h2>Un outil de test, pas une fonctionnalité</h2>
 * Ce composant n'existe que pour permettre à l'owner de basculer entre NyleRP et pokényle sans
 * réinstaller quoi que ce soit. Aucun autre joueur ne doit soupçonner qu'un second univers
 * existe : {@link #pour(Account, Runnable)} rend {@code null} pour tout autre compte, et un
 * {@code null} n'est jamais ajouté à la scène. Ce n'est pas un bouton grisé ni caché par une
 * propriété qu'on pourrait retourner : l'objet n'est pas construit.
 *
 * <h2>Pourquoi il ressemble exactement aux deux autres</h2>
 * Il vit dans la même rangée que « couper le son » et « couper la vidéo », au même endroit et
 * avec la même géométrie : quarante-quatre pixels de côté, même fond, même cadre, même rayon —
 * y compris sur le canal DEV, dont les pastilles sont à angles droits. Un bouton qui rejoint
 * une rangée existante doit en épouser la règle, sinon il a l'air posé là par accident.
 *
 * <h2>Le geste</h2>
 * Un clic ouvre les deux choix AU-DESSUS de la pastille — il n'y a pas la place en dessous, la
 * barre du bouton « Jouer » commence juste là. Un second clic sur une ligne bascule et referme.
 */
public final class SelecteurMode extends VBox {

    /** Le seul compte pour lequel ce composant est construit. */
    private static final String PROPRIETAIRE = "NYL3E";

    private static final Color ENCRE      = Color.web("#F4F4F7");
    private static final Color ENCRE_PALE = Color.web("#8A8A93");

    private final Runnable auChangement;
    private final boolean dev;
    private VBox menu;

    /**
     * Construit le sélecteur — ou rend {@code null} si ce compte n'y a pas droit.
     *
     * @param compte       le joueur connecté ; {@code null} accepté (rien ne sera construit)
     * @param auChangement à exécuter après une bascule, pour rafraîchir l'écran
     */
    public static SelecteurMode pour(Account compte, Runnable auChangement) {
        return estAutorise(compte) ? new SelecteurMode(auChangement) : null;
    }

    /**
     * Ce compte a-t-il droit au sélecteur ?
     *
     * <p>Séparée de la construction pour deux raisons. D'abord parce que la règle d'accès n'a
     * rien à voir avec du dessin : elle se lit, se relit et se teste seule, sans démarrer une
     * interface graphique. Ensuite parce que c'est LA garantie que l'on veut pouvoir prouver —
     * qu'aucun autre joueur ne voit ce bouton — et une garantie ne vaut que si on peut
     * l'éprouver.
     */
    public static boolean estAutorise(Account compte) {
        if (compte == null || compte.username() == null) return false;
        return PROPRIETAIRE.equalsIgnoreCase(compte.username().trim());
    }

    private SelecteurMode(Runnable auChangement) {
        this.auChangement = auChangement;
        this.dev = fr.nylerp.launcher.config.Constants.DEV;
        setAlignment(Pos.BOTTOM_RIGHT);
        setSpacing(8);
        // Sans cela, la boîte invisible qui entoure la pastille avalerait les clics destinés à
        // ce qui se trouve derrière elle.
        setPickOnBounds(false);
        setMaxSize(44, Region.USE_PREF_SIZE);
        getChildren().add(construirePastille());
    }

    /** La pastille : même géométrie et même style que « son » et « vidéo », canal DEV compris. */
    private Region construirePastille() {
        Button btn = new Button();
        btn.setMinSize(44, 44);
        btn.setPrefSize(44, 44);
        btn.setMaxSize(44, 44);
        final String repos  = dev ? "rgba(5,8,7,0.80)"       : "rgba(8,8,11,0.62)";
        final String survol = dev ? "rgba(34,255,136,0.22)"  : "rgba(20,20,28,0.78)";
        final String cadre  = dev ? "rgba(34,255,136,0.45)"  : "rgba(255,255,255,0.12)";
        final String rayon  = dev ? "0" : "22";
        btn.setStyle(
            "-fx-background-color: " + repos + ";" +
            "-fx-background-radius: " + rayon + ";" +
            "-fx-border-color: " + cadre + ";" +
            "-fx-border-radius: " + rayon + ";" +
            "-fx-border-width: 1;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 0;"
        );
        btn.setOnMouseEntered(e -> btn.setStyle(btn.getStyle().replace(repos, survol)));
        btn.setOnMouseExited(e -> btn.setStyle(btn.getStyle().replace(survol, repos)));
        btn.setTooltip(new Tooltip("Mode de jeu — " + ModeDeJeu.courant().titre));

        // Une manette : le geste qu'on vient faire ici, c'est choisir à quoi on joue.
        SVGPath icone = new SVGPath();
        icone.setContent(
            "M6 8h12a5 5 0 0 1 4.9 4.1l1 5.6A3.2 3.2 0 0 1 20.8 21a3.2 3.2 0 0 1-2.6-1.35L16.8 17.7"
            + "H7.2l-1.4 1.95A3.2 3.2 0 0 1 3.2 21 3.2 3.2 0 0 1 .1 17.7l1-5.6A5 5 0 0 1 6 8Z"
            + "M7 12v3M5.5 13.5h3M16.4 12.6h.01M18.6 14.8h.01");
        icone.setFill(Color.TRANSPARENT);
        icone.setStroke(Color.web(dev ? "#22FF88" : "#F4F4F7"));
        icone.setStrokeWidth(1.7);
        icone.setScaleX(0.86);
        icone.setScaleY(0.86);
        btn.setGraphic(icone);

        btn.setOnAction(e -> basculerMenu());
        return btn;
    }

    /** Ouvre le menu s'il est fermé, le referme sinon. */
    private void basculerMenu() {
        if (menu != null) {
            getChildren().remove(menu);
            menu = null;
            return;
        }
        double r = dev ? 0 : 10;
        menu = new VBox(2);
        menu.setPadding(new Insets(5));
        menu.setBackground(new Background(new BackgroundFill(
                Color.web(dev ? "#050807" : "#15161A"), new CornerRadii(r), Insets.EMPTY)));
        menu.setBorder(new Border(new BorderStroke(
                Color.web(dev ? "#22FF88" : "#2A2C33", dev ? 0.45 : 1.0),
                BorderStrokeStyle.SOLID, new CornerRadii(r), new BorderWidths(1))));
        menu.setEffect(new DropShadow(18, 0, 6, Color.web("#000000", 0.45)));
        // Le menu est plus large que la pastille : on l'aligne par la DROITE pour qu'il s'ouvre
        // vers l'intérieur de la fenêtre, jamais hors de l'écran.
        menu.setMaxWidth(Region.USE_PREF_SIZE);
        for (ModeDeJeu m : ModeDeJeu.values()) menu.getChildren().add(ligne(m));
        getChildren().add(0, menu);
        setAlignment(Pos.BOTTOM_RIGHT);

        FadeTransition f = new FadeTransition(Duration.millis(110), menu);
        f.setFromValue(0);
        f.setToValue(1);
        f.play();
    }

    /** Une ligne du menu : le mode, son sous-titre, surligné si c'est le mode actif. */
    private Region ligne(ModeDeJeu m) {
        boolean actif = m == ModeDeJeu.courant();
        Color accent = Color.web(dev ? "#22FF88" : "#FF8128");

        Label titre = new Label(m.titre);
        titre.setFont(Fonts.medium(11));
        titre.setTextFill(actif ? accent : ENCRE);

        Label sous = new Label(m.sousTitre);
        sous.setFont(Fonts.medium(9));
        sous.setTextFill(ENCRE_PALE);

        VBox textes = new VBox(1, titre, sous);
        HBox ligne = new HBox(textes);
        ligne.setAlignment(Pos.CENTER_LEFT);
        ligne.setPadding(new Insets(7, 14, 7, 11));
        ligne.setMinWidth(196);
        ligne.setCursor(Cursor.HAND);

        CornerRadii r = new CornerRadii(dev ? 0 : 7);
        Background survol = new Background(new BackgroundFill(
                Color.web(dev ? "#0C140F" : "#20222A"), r, Insets.EMPTY));
        Background repos  = new Background(new BackgroundFill(
                actif ? Color.web(dev ? "#0A110D" : "#1D1F26") : Color.TRANSPARENT, r, Insets.EMPTY));
        ligne.setBackground(repos);
        ligne.setOnMouseEntered(e -> ligne.setBackground(survol));
        ligne.setOnMouseExited(e -> ligne.setBackground(repos));
        ligne.setOnMouseClicked(e -> choisir(m));
        return ligne;
    }

    /**
     * Applique le choix. Rien ne se passe si c'est déjà le mode courant — ni écriture, ni
     * rafraîchissement : recharger l'écran pour un choix qui ne change rien ferait clignoter
     * l'interface sans raison.
     */
    private void choisir(ModeDeJeu m) {
        boolean change = m != ModeDeJeu.courant();
        if (menu != null) {
            getChildren().remove(menu);
            menu = null;
        }
        if (!change) return;
        ModeDeJeu.definir(m);
        if (auChangement != null) auChangement.run();
    }
}
