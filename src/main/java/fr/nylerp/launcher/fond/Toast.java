package fr.nylerp.launcher.fond;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Carte d'annonce au style du site (fond nacré, encre #30384A, accent #10A7E6), affichée dans le
 * coin de l'écran même quand la fenêtre du launcher est fermée : en bas à droite sous Windows et
 * macOS (en haut, les bannières du système les recouvraient), en haut à droite sous Linux.
 *
 * <p>Pas de {@code TrayIcon.displayMessage} : sous macOS il signe la notification « Java » ou
 * « Éditeur de script », et son rendu varie d'un système à l'autre.
 *
 * <p>Elle disparaît seule (le survol suspend le délai), se ferme par la croix (une vraie zone de 32 px
 * dessinée, jamais un caractère de texte : un Label trop étroit le remplace par « … »), et un clic n'importe
 * où ailleurs lance l'action reçue (ouvrir le launcher sur la bonne page). Plusieurs cartes
 * s'empilent. Fil JavaFX obligatoire.
 */
final class Toast {

    private static final Logger LOG = LoggerFactory.getLogger(Toast.class);

    static final double LARGEUR = 360;
    /** Zone cliquable de la croix (au moins 28 px). */
    static final double CROIX = 32;
    private static final double PADDING_CARTE = 14;
    private static final double LOGO = 40;
    private static final double ESPACE_LIGNE = 12;
    /** Largeur fixe des textes : le reste de la carte, croix et logo déduits, donc rien ne se comprime. */
    static final double LARGEUR_TEXTE = LARGEUR - 2 * PADDING_CARTE - LOGO - CROIX - 2 * ESPACE_LIGNE;
    private static final double MARGE = 16;
    private static final double ECART = 10;
    private static final Duration DUREE = Duration.seconds(8);

    private static final List<Stage> ouvertes = new ArrayList<>();
    private static Runnable apresToutFerme;
    /** Propriétaire invisible : sous Windows, une fenêtre possédée n'a pas de bouton dans la barre des tâches. */
    private static Stage proprietaire;

    static void afficher(String titre, String texte, Runnable surClic) {
        LOG.info("Toast : affichée « {} »", titre);
        Stage st = new Stage(StageStyle.TRANSPARENT);
        st.initOwner(proprietaire());
        st.setAlwaysOnTop(true);

        VBox carte = contenu(titre, texte, st, surClic);
        StackPane racine = new StackPane(carte);
        racine.setPadding(new Insets(24)); // place pour l'ombre
        racine.setStyle("-fx-background-color: transparent;");
        Scene scene = new Scene(racine);
        scene.setFill(Color.TRANSPARENT);
        st.setScene(scene);
        st.sizeToScene();

        // Position, puis entrée : glissement depuis le bord + fondu.
        st.setOpacity(0);
        st.show();
        ouvertes.add(st);
        disposer();
        carte.setTranslateX(40);
        FadeTransition f = new FadeTransition(Duration.millis(260), racine);
        f.setFromValue(0);
        f.setToValue(1);
        TranslateTransition t = new TranslateTransition(Duration.millis(320), carte);
        t.setToX(0);
        st.setOpacity(1);
        new ParallelTransition(f, t).play();

        PauseTransition attente = new PauseTransition(DUREE);
        attente.setOnFinished(e -> fermer(st));
        racine.setOnMouseEntered(e -> attente.pause());
        racine.setOnMouseExited(e -> attente.play());
        attente.play();
    }

    static VBox contenu(String titre, String texte, Stage st, Runnable surClic) {
        ImageView logo = new ImageView();
        try {
            logo.setImage(new Image(Toast.class.getResourceAsStream("/images/logo-mark.png"), 80, 80, true, true));
        } catch (Exception ignored) { }
        logo.setFitWidth(40);
        logo.setFitHeight(40);
        Rectangle coin = new Rectangle(40, 40);
        coin.setArcWidth(18);
        coin.setArcHeight(18);
        logo.setClip(coin);

        Label marque = new Label("NYLELAUNCHER");
        marque.setStyle("-fx-font-family: 'Montserrat Bold', 'Montserrat', 'System'; -fx-font-weight: bold; -fx-font-size: 10px;"
                + " -fx-text-fill: #10A7E6; -fx-letter-spacing: 1;");
        Label lTitre = new Label(titre == null ? "" : titre);
        lTitre.setStyle("-fx-font-family: 'Montserrat Bold', 'Montserrat', 'System'; -fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #30384A;");
        Label lTexte = new Label(texte == null ? "" : texte);
        lTexte.setStyle("-fx-font-family: 'Montserrat', 'System'; -fx-font-size: 12.5px; -fx-text-fill: #5A6478; -fx-line-spacing: 1;");
        // Largeur FIXE : le texte passe à la ligne dans sa colonne, et ne peut jamais prendre la place de la croix.
        for (Label l : new Label[] { marque, lTitre, lTexte }) {
            l.setWrapText(true);
            l.setMinWidth(LARGEUR_TEXTE);
            l.setPrefWidth(LARGEUR_TEXTE);
            l.setMaxWidth(LARGEUR_TEXTE);
            l.setMinHeight(Region.USE_PREF_SIZE);
        }
        VBox textes = new VBox(2, marque, lTitre, lTexte);
        textes.setMinWidth(LARGEUR_TEXTE);
        textes.setPrefWidth(LARGEUR_TEXTE);
        textes.setMaxWidth(LARGEUR_TEXTE);

        StackPane croix = croix(() -> {
            LOG.info("Toast : fermée par la croix");
            fermer(st);
        });

        HBox ligne = new HBox(ESPACE_LIGNE, logo, textes, croix);
        ligne.setAlignment(Pos.TOP_LEFT);
        // La zone de 32 px déborde un peu vers le coin : la croix reste au coin de la carte, la zone est généreuse.
        HBox.setMargin(croix, new Insets(-8, -6, 0, 0));

        VBox carte = new VBox(ligne);
        carte.setPadding(new Insets(PADDING_CARTE));
        carte.setPrefWidth(LARGEUR);
        carte.setMaxWidth(LARGEUR);
        carte.setMinHeight(Region.USE_PREF_SIZE);
        String fond = "-fx-background-color: linear-gradient(to bottom right, #FFFFFF 0%, #F2F8FF 100%);"
                + " -fx-background-radius: 18; -fx-border-radius: 18;"
                + " -fx-border-color: rgba(16,167,230,0.28); -fx-border-width: 1;";
        carte.setStyle(fond);
        DropShadow ombre = new DropShadow(22, 0, 8, Color.rgb(48, 56, 74, 0.28));
        carte.setEffect(ombre);
        carte.setCursor(Cursor.HAND);
        carte.setOnMouseEntered(e -> carte.setStyle(fond.replace("0.28", "0.6")));
        carte.setOnMouseExited(e -> carte.setStyle(fond));
        carte.setOnMouseClicked(e -> {
            LOG.info("Toast : clic sur la carte");
            fermer(st);
            if (surClic != null) surClic.run();
        });
        return carte;
    }

    /**
     * Bouton « fermer » : un carré de 32 px (taille minimale = préférée = maximale, donc jamais comprimé), avec une croix
     * dessinée en deux traits, pas un caractère de texte. Le clic se déclenche dès l'appui : fiable même quand la fenêtre
     * n'a pas encore le focus (carte posée sur le bureau, sans fenêtre active du launcher).
     */
    static StackPane croix(Runnable fermer) {
        Line a = new Line(0, 0, 10, 10);
        Line b = new Line(10, 0, 0, 10);
        for (Line l : new Line[] { a, b }) {
            l.setStroke(Color.web("#4A5468"));
            l.setStrokeWidth(2.2);
            l.setStrokeLineCap(StrokeLineCap.ROUND);
            l.setMouseTransparent(true);
        }
        StackPane c = new StackPane(new Group(a, b));
        c.setMinSize(CROIX, CROIX);
        c.setPrefSize(CROIX, CROIX);
        c.setMaxSize(CROIX, CROIX);
        c.setPickOnBounds(true);
        c.setCursor(Cursor.HAND);
        c.setAccessibleText("Fermer la notification");
        String repos = "-fx-background-radius: 16; -fx-background-color: rgba(48,56,74,0);";
        String survol = "-fx-background-radius: 16; -fx-background-color: rgba(48,56,74,0.12);";
        c.setStyle(repos);
        c.setOnMouseEntered(e -> c.setStyle(survol));
        c.setOnMouseExited(e -> c.setStyle(repos));
        c.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            e.consume();
            fermer.run();
        });
        // Le « clic » qui suit l'appui ne doit pas atteindre la carte (qui ouvrirait le launcher).
        c.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
        return c;
    }

    /**
     * Coin supérieur gauche (x, y) d'une carte (ombre comprise) dans la zone visible de l'écran :
     * en bas à droite (Windows, macOS) ou en haut à droite (Linux), {@code cumul} = hauteur déjà prise par les cartes
     * plus proches du bord.
     */
    static double[] position(Rectangle2D zone, double largeur, double hauteur, double cumul, boolean enBas) {
        double ombre = 24; // marge transparente autour de la carte
        double x = zone.getMaxX() - largeur - MARGE + ombre;
        double y = enBas ? zone.getMaxY() - MARGE + ombre - cumul - hauteur : zone.getMinY() + MARGE - ombre + cumul;
        return new double[] { x, y };
    }

    private static Stage proprietaire() {
        if (proprietaire == null) {
            proprietaire = new Stage(StageStyle.UTILITY);
            proprietaire.setOpacity(0);
            proprietaire.setWidth(1);
            proprietaire.setHeight(1);
            proprietaire.setX(-10000);
            proprietaire.setY(-10000);
        }
        // Windows rattache une fenêtre possédée au propriétaire NATIF : il doit donc exister (être
        // montré, invisible et hors écran) pour que la carte n'ait pas son propre bouton dans la barre
        // des tâches. macOS et Linux n'en ont pas besoin (vérifié sur macOS : aucune icône de Dock).
        if (Systeme.windows() && !proprietaire.isShowing()) proprietaire.show();
        return proprietaire;
    }

    /** Empile les cartes ouvertes dans le coin de l'écran principal, la plus ancienne contre le bord. */
    private static void disposer() {
        Rectangle2D zone = Screen.getPrimary().getVisualBounds();
        double ombre = 24; // marge transparente autour de la carte
        // En bas à droite sous Windows ET macOS : en haut à droite, les bannières du système
        // (« Notifications … ») recouvraient nos cartes (constaté au test d'intégration du 16/10).
        boolean enBas = Systeme.windows() || Systeme.mac();
        double cumul = 0;
        for (Stage st : ouvertes) {
            double h = st.getHeight();
            double[] xy = position(zone, st.getWidth(), h, cumul, enBas);
            st.setX(xy[0]);
            st.setY(xy[1]);
            cumul += h - 2 * ombre + ECART;
        }
    }

    private static void fermer(Stage st) {
        if (!ouvertes.contains(st)) return;
        ouvertes.remove(st);
        FadeTransition f = new FadeTransition(Duration.millis(220), st.getScene().getRoot());
        f.setToValue(0);
        f.setOnFinished(e -> {
            st.close();
            disposer();
            if (ouvertes.isEmpty() && apresToutFerme != null) apresToutFerme.run();
        });
        f.play();
    }

    /** Rappel appelé quand la dernière carte ouverte vient de se fermer (outil d'essai). */
    static void surToutFerme(Runnable r) { apresToutFerme = r; }

    private Toast() {}
}
