package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.ui.compte.Ajuste;
import fr.nylerp.launcher.ui.compte.FondCiel;
import fr.nylerp.launcher.ui.kit.BoutonJouer;
import fr.nylerp.launcher.ui.kit.CarteVerre;
import fr.nylerp.launcher.ui.kit.Interrupteur;
import fr.nylerp.launcher.ui.kit.Jetons;
import fr.nylerp.launcher.ui.kit.Polices;
import fr.nylerp.launcher.ui.kit.TitreEclats;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * Écran « Nouveau launcher », montré une fois au premier lancement après la mise à jour de la
 * refonte ({@link Settings#accueilVu} faux). Il dit honnêtement ce qui change — le launcher se
 * lance avec l'ordinateur, discrètement dans la barre système, pour prévenir des ouvertures et des
 * événements — et laisse le joueur couper les deux réglages d'un clic avant de commencer.
 *
 * <p>« C'est parti » écrit {@code demarrageAuto}, {@code notifications} et {@code accueilVu=true},
 * enregistre les réglages, puis appelle le rappel de {@link #setSurValider} : l'intégration (lot
 * L9) y branche l'application réelle du démarrage automatique (paquet {@code fond/}) et la suite
 * de la navigation.
 */
public class Accueil extends StackPane {

    private final Settings reglages;
    private final Interrupteur demarrage;
    private final Interrupteur notifications;
    private Consumer<Settings> surValider;

    public Accueil(Settings reglages) {
        Polices.charger();
        this.reglages = reglages;
        getStyleClass().add("compte-accueil");
        getStylesheets().add(Accueil.class.getResource("/css/kit.css").toExternalForm());

        TitreEclats titre = new TitreEclats("Nouveau launcher", TitreEclats.Style.PRINCIPAL, Jetons.rem(2.5), null);
        Label chapeau = new Label("Plus clair, plus beau, et toujours à portée de main.");
        chapeau.setFont(Polices.extra(Jetons.rem(0.95)));
        chapeau.setTextFill(Color.WHITE);
        chapeau.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(30, 70, 140, 0.65), Jetons.rem(0.6), 0.15, 0, Jetons.rem(0.08)));

        Label explication = new Label("Le launcher se lance avec ton ordinateur, discrètement dans la barre système, "
                + "pour te prévenir des ouvertures et des événements. Tu peux couper ça tout de suite, ou plus tard dans les paramètres.");
        explication.setFont(Polices.gras(Jetons.rem(0.8)));
        explication.setTextFill(Jetons.ENCRE_2);
        explication.setWrapText(true);
        explication.setMinHeight(Region.USE_PREF_SIZE);

        demarrage = new Interrupteur(reglages.demarrageAuto);
        demarrage.setId("inter-demarrage");
        notifications = new Interrupteur(reglages.notifications);
        notifications.setId("inter-notifications");

        VBox contenu = new VBox(Jetons.rem(0.85),
                explication,
                ligne(demarrage, "Lancer au démarrage", "Le launcher s'ouvre en arrière-plan quand tu allumes ton ordinateur, sans fenêtre."),
                ligne(notifications, "Notifications", "Ouvertures, événements et nouveautés des serveurs, dans une petite carte discrète."));
        contenu.setPrefWidth(Jetons.rem(21));

        BoutonJouer partir = new BoutonJouer("C'est parti");
        partir.setId("accueil-valider");
        partir.setOnAction(e -> valider());
        VBox bas = new VBox(partir);
        bas.setAlignment(Pos.CENTER);
        bas.setPadding(new Insets(Jetons.rem(0.6), 0, 0, 0));

        VBox carteContenu = new VBox(Jetons.rem(0.6), contenu, bas);
        CarteVerre carte = new CarteVerre(CarteVerre.Variante.CLAIRE, carteContenu);
        carte.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        carte.setPadding(new Insets(Jetons.rem(1.5), Jetons.rem(1.8), Jetons.rem(1.4), Jetons.rem(1.8)));

        VBox colonne = new VBox(Jetons.rem(0.45), titre, chapeau, espace(Jetons.rem(0.9)), carte);
        colonne.setAlignment(Pos.CENTER);
        colonne.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        getChildren().addAll(new FondCiel(), new Ajuste(colonne, new Insets(Jetons.rem(1.2))));

        int i = 0;
        for (Node n : new Node[]{titre, chapeau, carte}) Animations.enter(n, Duration.millis(80 + (i++) * 70));
    }

    private static HBox ligne(Interrupteur inter, String titre, String detail) {
        Label t = new Label(titre);
        t.setFont(Polices.noir(Jetons.rem(0.95)));
        t.setTextFill(Jetons.ENCRE);
        Label d = new Label(detail);
        d.setFont(Polices.gras(Jetons.rem(0.7)));
        d.setTextFill(Jetons.ENCRE_3);
        d.setWrapText(true);
        d.setMinHeight(Region.USE_PREF_SIZE);
        VBox textes = new VBox(Jetons.rem(0.12), t, d);
        HBox.setHgrow(textes, Priority.ALWAYS);
        // toute la ligne bascule l'interrupteur, pas seulement le petit rail
        HBox h = new HBox(Jetons.rem(0.9), textes, inter);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(Jetons.rem(0.65), Jetons.rem(0.8), Jetons.rem(0.65), Jetons.rem(0.9)));
        h.setStyle("-fx-background-color: rgba(255,255,255,0.7); -fx-background-radius: " + Jetons.rem(0.7)
                + "px; -fx-border-color: rgba(255,255,255,0.95); -fx-border-radius: " + Jetons.rem(0.7) + "px;");
        textes.setOnMouseClicked(e -> inter.setActif(!inter.isActif()));
        textes.setCursor(fr.nylerp.launcher.ui.kit.Curseurs.main());
        return h;
    }

    private void valider() {
        reglages.demarrageAuto = demarrage.isActif();
        reglages.notifications = notifications.isActif();
        reglages.accueilVu = true;
        reglages.save();
        if (surValider != null) surValider.accept(reglages);
    }

    /** Appelé après l'enregistrement des réglages, avec ces réglages. */
    public void setSurValider(Consumer<Settings> rappel) { this.surValider = rappel; }

    private static Region espace(double h) {
        Region r = new Region();
        r.setMinHeight(h);
        r.setPrefHeight(h);
        r.setMaxHeight(h);
        return r;
    }
}
