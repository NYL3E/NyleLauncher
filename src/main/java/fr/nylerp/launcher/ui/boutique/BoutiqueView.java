package fr.nylerp.launcher.ui.boutique;

import fr.nylerp.launcher.ui.kit.BoutonIcone;
import fr.nylerp.launcher.ui.kit.Jetons;
import fr.nylerp.launcher.ui.kit.Polices;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Window;

/**
 * La boutique du site, affichée telle quelle dans le launcher : barre fine (retour, titre, chargement) au-dessus
 * d'un WebView plein cadre. Le paiement Tebex s'ouvre dans {@link FenetrePaiement}. Le WebView est créé ici et
 * libéré par {@link #fermer()} : rien ne reste en mémoire quand la boutique est fermée.
 */
public final class BoutiqueView extends BorderPane {

    /** Le WebKit de JavaFX 21 (WebKit 616) : on se présente en Safari récent pour que Tebex et PayPal l'acceptent. */
    static final String AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 "
            + "(KHTML, like Gecko) Version/17.0 Safari/605.1.15 NyleLauncher";

    private static final String RETOUR = "M14.5 6 8.5 12l6 6";

    private WebView vue;
    private final ProgressBar chargement = new ProgressBar();
    private final String adresse;
    private Runnable surRetour;
    private String derniereAdresseSite;

    BoutiqueView(String titre, String adresse) {
        this.adresse = adresse;
        this.derniereAdresseSite = adresse;
        getStyleClass().add("boutique-vue");
        setStyle("-fx-background-color: " + web(Jetons.CIEL) + ";");

        BoutonIcone retour = new BoutonIcone(RETOUR);
        retour.setOnAction(e -> { if (surRetour != null) surRetour.run(); });
        Label nom = new Label(titre);
        nom.setFont(Polices.extra(Jetons.rem(0.82)));
        nom.setTextFill(Jetons.ENCRE);
        Label securise = new Label("Paiement sécurisé par Tebex");
        securise.setFont(Polices.gras(Jetons.rem(0.62)));
        securise.setTextFill(Jetons.ENCRE_3);
        Region vide = new Region();
        HBox.setHgrow(vide, Priority.ALWAYS);
        HBox barre = new HBox(Jetons.rem(0.5), retour, nom, vide, securise);
        barre.setAlignment(Pos.CENTER_LEFT);
        barre.setPadding(new Insets(Jetons.rem(0.35), Jetons.rem(0.9), Jetons.rem(0.35), Jetons.rem(0.5)));
        barre.setStyle("-fx-background-color: rgba(255,255,255,0.78);"
                + "-fx-border-color: transparent transparent rgba(48,56,74,0.10) transparent;");

        chargement.setMaxWidth(Double.MAX_VALUE);
        chargement.setPrefHeight(3);
        chargement.setStyle("-fx-accent: " + web(Jetons.ACTIF) + "; -fx-control-inner-background: transparent;"
                + "-fx-background-color: transparent; -fx-padding: 0;");
        setTop(new VBox(barre, chargement));

        vue = new WebView();
        vue.setContextMenuEnabled(false);
        WebEngine moteur = vue.getEngine();
        moteur.setUserAgent(AGENT);
        chargement.progressProperty().bind(moteur.getLoadWorker().progressProperty());
        chargement.visibleProperty().bind(moteur.getLoadWorker().runningProperty());
        moteur.locationProperty().addListener((o, avant, apres) -> aiguiller(moteur, apres));
        moteur.setCreatePopupHandler(f -> FenetrePaiement.ouvrir(fenetre(), this::revenirSurLeSite).moteur());
        moteur.getLoadWorker().stateProperty().addListener((o, a, s) -> {
            if (s == Worker.State.FAILED) moteur.loadContent(pageHorsLigne(), "text/html");
        });
        setCenter(new StackPane(vue));
        moteur.load(adresse);
    }

    /** Appelé par le bouton retour de la barre (la coquille revient à l'univers). */
    public void setSurRetour(Runnable r) { surRetour = r; }

    /** Recharge la boutique (par exemple après un changement de compte). */
    public void recharger() { if (vue != null) vue.getEngine().load(adresse); }

    /** Libère le WebView. À appeler quand on quitte la boutique. */
    public void fermer() {
        if (vue == null) return;
        chargement.progressProperty().unbind();
        chargement.visibleProperty().unbind();
        vue.getEngine().load(null);
        setCenter(null);
        vue = null;
    }

    private void aiguiller(WebEngine moteur, String apres) {
        switch (Boutique.destination(apres)) {
            case ICI -> { if (Boutique.hote(apres) != null) derniereAdresseSite = apres; }
            case PAIEMENT -> {
                String retourSur = derniereAdresseSite;
                Platform.runLater(() -> {
                    FenetrePaiement.ouvrir(fenetre(), this::revenirSurLeSite).moteur().load(apres);
                    moteur.load(retourSur);
                });
            }
            case NAVIGATEUR -> {
                String retourSur = derniereAdresseSite;
                Boutique.navigateur(apres);
                Platform.runLater(() -> moteur.load(retourSur));
            }
        }
    }

    /** La fenêtre de paiement s'est refermée sur une page du site (retour de Tebex) : on l'affiche ici. */
    private void revenirSurLeSite(String adresseRetour) {
        if (vue != null && adresseRetour != null) vue.getEngine().load(adresseRetour);
    }

    private Window fenetre() { return getScene() == null ? null : getScene().getWindow(); }

    private static String pageHorsLigne() {
        return "<html><body style=\"margin:0;height:100vh;display:flex;align-items:center;justify-content:center;"
                + "background:#eaf7ff;font-family:-apple-system,Segoe UI,sans-serif;color:#30384a;text-align:center\">"
                + "<div><p style=\"font-size:20px;font-weight:800;margin:0 0 8px\">La boutique ne répond pas</p>"
                + "<p style=\"font-size:14px;color:#587a9a;margin:0\">Vérifie ta connexion, puis reviens dans un instant."
                + "</p></div></body></html>";
    }

    static String web(javafx.scene.paint.Color c) {
        return String.format("#%02x%02x%02x", (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }
}
