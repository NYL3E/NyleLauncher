package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.config.ModeDeJeu;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
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
 * LE SÉLECTEUR DE MODE — en bas à gauche, et pour une seule personne.
 *
 * <h2>Un outil de test, pas une fonctionnalité</h2>
 * Ce composant n'existe que pour permettre à l'owner de basculer entre NyleRP et pokényle sans
 * réinstaller quoi que ce soit. Aucun autre joueur ne doit soupçonner qu'un second univers
 * existe : {@link #pour(Account, Runnable)} rend {@code null} pour tout autre compte, et un
 * {@code null} n'est jamais ajouté à la scène. Ce n'est pas un bouton grisé ni caché par une
 * propriété qu'on pourrait retourner : l'objet n'est pas construit.
 *
 * <h2>Pourquoi le contrôle porte sur le pseudo</h2>
 * C'est le seul élément d'identité dont le launcher dispose avant le jeu, et il suffit ici :
 * la conséquence d'un contournement serait qu'un curieux télécharge un second modpack public,
 * pas qu'il obtienne un privilège. On ne protège pas un secret, on évite d'encombrer l'écran de
 * tout le monde avec un bouton qui ne les concerne pas.
 *
 * <h2>Le geste</h2>
 * Un clic ouvre les deux choix juste au-dessus du bouton ; un second clic sur une ligne bascule
 * et referme. Le mode retenu est écrit dans les réglages, et l'écran se rafraîchit pour que le
 * bouton « Jouer » parle bien du nouvel univers.
 */
public final class SelecteurMode extends VBox {

    /** Le seul compte pour lequel ce composant est construit. */
    private static final String PROPRIETAIRE = "NYL3E";

    private static final Color ENCRE      = Color.web("#F4F4F7");
    private static final Color ENCRE_PALE = Color.web("#8A8A93");
    private static final Color ACCENT     = Color.web("#FF8128");
    private static final Color FOND       = Color.web("#15161A");
    private static final Color BORDURE    = Color.web("#2A2C33");

    private final Runnable auChangement;
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
        setAlignment(Pos.BOTTOM_LEFT);
        setSpacing(8);
        setPickOnBounds(false);
        getChildren().add(construireBouton());
    }

    /** La pastille visible en permanence : elle nomme le mode en cours. */
    private Region construireBouton() {
        ModeDeJeu mode = ModeDeJeu.courant();

        SVGPath fleche = new SVGPath();
        // Un chevron vers le haut : le menu s'ouvre au-dessus, l'icône dit où regarder.
        fleche.setContent("M1 6 L5 2 L9 6");
        fleche.setStroke(ENCRE_PALE);
        fleche.setStrokeWidth(1.6);
        fleche.setFill(Color.TRANSPARENT);

        Label titre = new Label("MODE DE JEU");
        titre.setFont(Fonts.medium(8));
        titre.setTextFill(ENCRE_PALE);
        titre.setStyle("-fx-letter-spacing: 0.16em;");

        Label valeur = new Label(mode.titre);
        valeur.setFont(Fonts.medium(11));
        valeur.setTextFill(ENCRE);

        VBox textes = new VBox(1, titre, valeur);
        textes.setAlignment(Pos.CENTER_LEFT);

        HBox pastille = new HBox(10, textes, fleche);
        pastille.setAlignment(Pos.CENTER_LEFT);
        pastille.setPadding(new Insets(7, 12, 7, 13));
        pastille.setCursor(Cursor.HAND);
        pastille.setBackground(new Background(new BackgroundFill(FOND, new CornerRadii(9), Insets.EMPTY)));
        pastille.setBorder(new Border(new BorderStroke(BORDURE, BorderStrokeStyle.SOLID,
                new CornerRadii(9), new BorderWidths(1))));
        pastille.setOnMouseEntered(e -> pastille.setBorder(new Border(new BorderStroke(
                ACCENT.deriveColor(0, 1, 1, 0.55), BorderStrokeStyle.SOLID,
                new CornerRadii(9), new BorderWidths(1)))));
        pastille.setOnMouseExited(e -> pastille.setBorder(new Border(new BorderStroke(
                BORDURE, BorderStrokeStyle.SOLID, new CornerRadii(9), new BorderWidths(1)))));
        pastille.setOnMouseClicked(e -> basculerMenu());
        return pastille;
    }

    /** Ouvre le menu s'il est fermé, le referme sinon. */
    private void basculerMenu() {
        if (menu != null) {
            getChildren().remove(menu);
            menu = null;
            return;
        }
        menu = new VBox(2);
        menu.setPadding(new Insets(5));
        menu.setBackground(new Background(new BackgroundFill(FOND, new CornerRadii(10), Insets.EMPTY)));
        menu.setBorder(new Border(new BorderStroke(BORDURE, BorderStrokeStyle.SOLID,
                new CornerRadii(10), new BorderWidths(1))));
        menu.setEffect(new DropShadow(18, 0, 6, Color.web("#000000", 0.45)));
        for (ModeDeJeu m : ModeDeJeu.values()) menu.getChildren().add(ligne(m));
        getChildren().add(0, menu);

        FadeTransition f = new FadeTransition(Duration.millis(110), menu);
        f.setFromValue(0);
        f.setToValue(1);
        f.play();
    }

    /** Une ligne du menu : le mode, son sous-titre, et une pastille si c'est le mode actif. */
    private Region ligne(ModeDeJeu m) {
        boolean actif = m == ModeDeJeu.courant();

        Label titre = new Label(m.titre);
        titre.setFont(Fonts.medium(11));
        titre.setTextFill(actif ? ACCENT : ENCRE);

        Label sous = new Label(m.sousTitre);
        sous.setFont(Fonts.medium(9));
        sous.setTextFill(ENCRE_PALE);

        VBox textes = new VBox(1, titre, sous);
        HBox ligne = new HBox(textes);
        ligne.setAlignment(Pos.CENTER_LEFT);
        ligne.setPadding(new Insets(7, 14, 7, 11));
        ligne.setMinWidth(196);
        ligne.setCursor(Cursor.HAND);

        CornerRadii r = new CornerRadii(7);
        Background survol = new Background(new BackgroundFill(Color.web("#20222A"), r, Insets.EMPTY));
        Background repos  = new Background(new BackgroundFill(
                actif ? Color.web("#1D1F26") : Color.TRANSPARENT, r, Insets.EMPTY));
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
