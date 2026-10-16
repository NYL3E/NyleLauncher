package fr.nylerp.launcher.ui.boutique;

import fr.nylerp.launcher.ui.kit.Jetons;
import fr.nylerp.launcher.ui.kit.Polices;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.function.Consumer;

/**
 * Fenêtre modale « Paiement sécurisé Tebex » : la page de paiement (et ses fenêtres PayPal, 3-D Secure…)
 * s'affiche dans le launcher. Elle se ferme d'elle-même quand Tebex renvoie vers une page du site (retour de
 * paiement), qui s'affiche alors dans la boutique. Repli toujours visible : ouvrir la même page dans le
 * navigateur système.
 */
final class FenetrePaiement {

    private final Stage stage = new Stage();
    private final WebView vue = new WebView();
    private final Label alerte = new Label("La page de paiement ne s'affiche pas correctement ici.");
    private final Consumer<String> surRetour;
    private boolean ferme;

    private FenetrePaiement(Window parent, Consumer<String> surRetour) {
        this.surRetour = surRetour;
        WebEngine moteur = vue.getEngine();
        moteur.setUserAgent(BoutiqueView.AGENT);
        vue.setContextMenuEnabled(false);

        ProgressBar chargement = new ProgressBar();
        chargement.setMaxWidth(Double.MAX_VALUE);
        chargement.setPrefHeight(3);
        chargement.setStyle("-fx-accent: " + BoutiqueView.web(Jetons.ACTIF) + "; -fx-background-color: transparent; -fx-padding: 0;");
        chargement.progressProperty().bind(moteur.getLoadWorker().progressProperty());
        chargement.visibleProperty().bind(moteur.getLoadWorker().runningProperty());

        alerte.setFont(Polices.gras(13));
        alerte.setTextFill(Jetons.ENCRE);
        alerte.setVisible(false);
        alerte.setManaged(false);
        Label aide = new Label("Un problème d'affichage ?");
        aide.setFont(Polices.gras(12));
        aide.setTextFill(Jetons.ENCRE_3);
        Button navigateur = new Button("Ouvrir le paiement dans mon navigateur");
        navigateur.setFont(Polices.extra(12));
        navigateur.setStyle("-fx-background-color: " + BoutiqueView.web(Jetons.ARDOISE) + "; -fx-text-fill: white;"
                + "-fx-background-radius: 999; -fx-padding: 6 14; -fx-cursor: hand;");
        navigateur.setOnAction(e -> {
            String ici = moteur.getLocation();
            if (ici != null && !ici.isBlank()) Boutique.navigateur(ici);
            fermer(null);
        });
        Region vide = new Region();
        HBox.setHgrow(vide, Priority.ALWAYS);
        HBox pied = new HBox(10, alerte, aide, vide, navigateur);
        pied.setAlignment(Pos.CENTER_LEFT);
        pied.setPadding(new Insets(8, 14, 8, 14));
        pied.setStyle("-fx-background-color: rgba(255,255,255,0.92);"
                + "-fx-border-color: rgba(48,56,74,0.10) transparent transparent transparent;");

        BorderPane racine = new BorderPane(vue);
        racine.setTop(chargement);
        racine.setBottom(pied);
        racine.setStyle("-fx-background-color: white;");

        moteur.locationProperty().addListener((o, a, adresse) -> aiguiller(adresse));
        moteur.setCreatePopupHandler(f -> ouvrir(stage, surRetour).moteur());   // PayPal, 3-D Secure…
        moteur.getLoadWorker().stateProperty().addListener((o, a, s) -> {
            if (s == Worker.State.FAILED) signaler();
            if (s == Worker.State.SUCCEEDED) verifierNavigateur(moteur);
        });
        moteur.setOnError(e -> signaler());

        stage.setTitle("Paiement sécurisé Tebex");
        if (parent != null) {
            stage.initOwner(parent);
            stage.initModality(Modality.WINDOW_MODAL);
        }
        try { stage.getIcons().add(new Image(FenetrePaiement.class.getResourceAsStream("/images/app_icon.png"))); }
        catch (Exception ignored) { }
        stage.setScene(new Scene(racine, 520, 760));
        stage.setMinWidth(420);
        stage.setMinHeight(560);
        stage.setOnHidden(e -> {
            ferme = true;
            chargement.progressProperty().unbind();
            chargement.visibleProperty().unbind();
            moteur.load(null);
        });
    }

    /** Ouvre une fenêtre de paiement vide ; l'appelant y charge l'adresse (ou WebKit le fait pour une fenêtre surgissante). */
    static FenetrePaiement ouvrir(Window parent, Consumer<String> surRetour) {
        FenetrePaiement f = new FenetrePaiement(parent, surRetour);
        f.stage.show();
        return f;
    }

    WebEngine moteur() { return vue.getEngine(); }

    private void aiguiller(String adresse) {
        if (ferme || Boutique.hote(adresse) == null) return;
        switch (Boutique.destination(adresse)) {
            case PAIEMENT -> { }                                   // la page de paiement reste ici
            case ICI -> Platform.runLater(() -> fermer(adresse));  // retour de Tebex vers le site
            case NAVIGATEUR -> {                                   // lien hors paiement : navigateur système
                Boutique.navigateur(adresse);
                Platform.runLater(() -> fermer(null));
            }
        }
    }

    /** Tebex et PayPal affichent un avertissement quand le navigateur n'est pas pris en charge : on met le repli en avant. */
    private void verifierNavigateur(WebEngine moteur) {
        try {
            Object texte = moteur.executeScript("document.body ? document.body.innerText.slice(0, 4000) : ''");
            String t = String.valueOf(texte).toLowerCase(java.util.Locale.ROOT);
            if (t.contains("browser is not supported") || t.contains("unsupported browser")
                    || t.contains("navigateur n'est pas pris en charge") || t.contains("navigateur non pris en charge")
                    || t.contains("update your browser")) {
                signaler();
            }
        } catch (Exception ignored) { }
    }

    private void signaler() {
        alerte.setVisible(true);
        alerte.setManaged(true);
    }

    private void fermer(String adresseRetour) {
        if (ferme) return;
        stage.close();
        if (adresseRetour != null && surRetour != null) surRetour.accept(adresseRetour);
    }
}
