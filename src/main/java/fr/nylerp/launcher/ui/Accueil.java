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
 * refonte ({@link Settings#accueilVu} faux) : un titre, deux interrupteurs (« Lancer au démarrage »,
 * « Notifications », activés par défaut, coupables d'un clic) et un bouton — pas de paragraphe
 * (owner, 16/10 : « simplifie au max »).
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

        demarrage = new Interrupteur(reglages.demarrageAuto);
        demarrage.setId("inter-demarrage");
        notifications = new Interrupteur(reglages.notifications);
        notifications.setId("inter-notifications");

        VBox contenu = new VBox(Jetons.rem(0.7),
                ligne(demarrage, "Lancer au démarrage"),
                ligne(notifications, "Notifications"));
        contenu.setPrefWidth(Jetons.rem(17));

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

        VBox colonne = new VBox(Jetons.rem(1.8), titre, carte);
        colonne.setAlignment(Pos.CENTER);
        colonne.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        getChildren().addAll(new FondCiel(), new Ajuste(colonne, new Insets(Jetons.rem(1.2))));

        int i = 0;
        for (Node n : new Node[]{titre, carte}) Animations.enter(n, Duration.millis(80 + (i++) * 70));
    }

    private static HBox ligne(Interrupteur inter, String titre) {
        Label t = new Label(titre);
        t.setFont(Polices.noir(Jetons.rem(1.0)));
        t.setTextFill(Jetons.ENCRE);
        HBox.setHgrow(t, Priority.ALWAYS);
        t.setMaxWidth(Double.MAX_VALUE);
        // toute la ligne bascule l'interrupteur, pas seulement le petit rail
        HBox h = new HBox(Jetons.rem(0.9), t, inter);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(Jetons.rem(0.8), Jetons.rem(0.9), Jetons.rem(0.8), Jetons.rem(1.0)));
        h.setStyle("-fx-background-color: rgba(255,255,255,0.85); -fx-background-radius: " + Jetons.rem(0.7)
                + "px; -fx-border-color: rgba(255,255,255,0.95); -fx-border-radius: " + Jetons.rem(0.7) + "px;");
        t.setOnMouseClicked(e -> inter.setActif(!inter.isActif()));
        t.setCursor(fr.nylerp.launcher.ui.kit.Curseurs.main());
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
}
