package fr.nylerp.launcher.fond;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Carte d'annonce au style du site (fond nacré, encre #30384A, accent #10A7E6), affichée dans le
 * coin de l'écran même quand la fenêtre du launcher est fermée : en haut à droite sous macOS et
 * Linux (là où le système range ses propres notifications), en bas à droite sous Windows.
 *
 * <p>Pas de {@code TrayIcon.displayMessage} : sous macOS il signe la notification « Java » ou
 * « Éditeur de script », et son rendu varie d'un système à l'autre.
 *
 * <p>Elle disparaît seule (le survol suspend le délai), se ferme par la croix, et un clic n'importe
 * où ailleurs lance l'action reçue (ouvrir le launcher sur la bonne page). Plusieurs cartes
 * s'empilent. Fil JavaFX obligatoire.
 */
final class Toast {

    static final double LARGEUR = 360;
    private static final double MARGE = 16;
    private static final double ECART = 10;
    private static final Duration DUREE = Duration.seconds(8);

    private static final List<Stage> ouvertes = new ArrayList<>();
    /** Propriétaire invisible : sous Windows, une fenêtre possédée n'a pas de bouton dans la barre des tâches. */
    private static Stage proprietaire;

    static void afficher(String titre, String texte, Runnable surClic) {
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
        marque.setStyle("-fx-font-family: 'Montserrat Bold', 'Montserrat'; -fx-font-weight: bold; -fx-font-size: 10px;"
                + " -fx-text-fill: #10A7E6; -fx-letter-spacing: 1;");
        Label lTitre = new Label(titre == null ? "" : titre);
        lTitre.setWrapText(true);
        lTitre.setStyle("-fx-font-family: 'Montserrat Bold', 'Montserrat'; -fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #30384A;");
        Label lTexte = new Label(texte == null ? "" : texte);
        lTexte.setWrapText(true);
        lTexte.setStyle("-fx-font-family: 'Montserrat'; -fx-font-size: 12.5px; -fx-text-fill: #5A6478; -fx-line-spacing: 1;");
        VBox textes = new VBox(2, marque, lTitre, lTexte);
        HBox.setHgrow(textes, Priority.ALWAYS);

        Label croix = new Label("×");
        croix.setStyle("-fx-font-size: 18px; -fx-text-fill: #8A93A6; -fx-padding: 0 2 0 6;");
        croix.setCursor(Cursor.HAND);
        croix.setOnMouseEntered(e -> croix.setStyle("-fx-font-size: 18px; -fx-text-fill: #30384A; -fx-padding: 0 2 0 6;"));
        croix.setOnMouseExited(e -> croix.setStyle("-fx-font-size: 18px; -fx-text-fill: #8A93A6; -fx-padding: 0 2 0 6;"));
        croix.setOnMouseClicked(e -> { e.consume(); fermer(st); });

        HBox ligne = new HBox(12, logo, textes, croix);
        ligne.setAlignment(Pos.TOP_LEFT);

        VBox carte = new VBox(ligne);
        carte.setPadding(new Insets(14, 14, 14, 14));
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
            fermer(st);
            if (surClic != null) surClic.run();
        });
        return carte;
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
        double cumul = 0;
        for (Stage st : ouvertes) {
            double h = st.getHeight();
            st.setX(zone.getMaxX() - st.getWidth() - MARGE + ombre);
            if (Systeme.windows()) {
                st.setY(zone.getMaxY() - MARGE + ombre - cumul - h);
            } else {
                st.setY(zone.getMinY() + MARGE - ombre + cumul);
            }
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
        });
        f.play();
    }

    private Toast() {}
}
