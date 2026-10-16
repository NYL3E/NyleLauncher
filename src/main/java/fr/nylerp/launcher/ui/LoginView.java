package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.auth.MicrosoftAuth;
import fr.nylerp.launcher.auth.OfflineAuth;
import fr.nylerp.launcher.ui.compte.Ajuste;
import fr.nylerp.launcher.ui.compte.CarteChoix;
import fr.nylerp.launcher.ui.compte.FondCiel;
import fr.nylerp.launcher.ui.compte.VerifPseudo;
import fr.nylerp.launcher.ui.kit.CarteVerre;
import fr.nylerp.launcher.ui.kit.ChromeFenetre;
import fr.nylerp.launcher.ui.kit.Jetons;
import fr.nylerp.launcher.ui.kit.Polices;
import fr.nylerp.launcher.ui.kit.TitreEclats;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * Écran de connexion (refonte 2026-10, lot L4) : le ciel du site, un titre court et DEUX grandes
 * cartes — « J'ai acheté Minecraft » / « Je joue en crack » — avec une seule ligne chacune. Rien
 * d'autre à lire (owner, 16/10 : « simplifie au max, on doit avoir tout de suite envie de passer à
 * la suite »).
 * <ul>
 *   <li>Microsoft → la connexion embarquée de toujours ({@link MicrosoftAuth#loginWithWebview()}) ;</li>
 *   <li>crack → étape « Ton pseudo » : validation {@link OfflineAuth#login(String)} comme avant, puis
 *       vérification que le pseudo n'est pas celui d'un compte acheté (NyleAuth l'imposerait en
 *       premium et refuserait le joueur).</li>
 * </ul>
 * Mode « Ajouter un compte » (multicompte, 3 au plus, limite tenue par {@code LauncherApp} /
 * {@code AccountStore}) quand {@code onBack} n'est pas nul : autre titre et bouton Retour.
 */
public class LoginView extends StackPane {

    private static final String MSG_ERREUR = "#d0475b";

    private final Consumer<Account> onAuthenticated;
    private final StackPane etapes = new StackPane();
    private final HBox etapeChoix;
    private final VBox etapeCrack;
    private final CarteChoix carteMicrosoft;
    private final CarteChoix carteCrack;
    private final TextField champPseudo = new TextField();
    private final Label message = new Label();
    private final Button validerCrack = new Button("Jouer");
    private boolean verificationEnCours;

    public LoginView(Consumer<Account> onAuthenticated) {
        this(onAuthenticated, null);
    }

    /**
     * @param onBack when non-null, the view is in "add an account" mode :
     *               title changes and a back button returns to the main view.
     */
    public LoginView(Consumer<Account> onAuthenticated, Runnable onBack) {
        Polices.charger();
        this.onAuthenticated = onAuthenticated;
        getStyleClass().add("compte-racine");
        getStylesheets().add(LoginView.class.getResource("/css/kit.css").toExternalForm());

        boolean adding = onBack != null;
        TitreEclats titre = new TitreEclats(adding ? "Ajouter un compte" : "Bienvenue",
                TitreEclats.Style.PRINCIPAL, Jetons.rem(2.6), null);

        carteMicrosoft = new CarteChoix(Icons.microsoftLogo(Jetons.rem(1.9)), "J'ai acheté Minecraft", "Compte Microsoft");
        carteMicrosoft.setId("carte-microsoft");
        carteMicrosoft.setSurChoix(this::connexionMicrosoft);

        carteCrack = new CarteChoix(silhouette(Jetons.rem(2.3)), "Je joue en crack", "Gratuit, juste un pseudo");
        carteCrack.setId("carte-crack");
        carteCrack.setSurChoix(() -> montrer(true));

        etapeChoix = new HBox(Jetons.rem(1.8), carteMicrosoft, carteCrack);
        etapeChoix.setAlignment(Pos.CENTER);
        etapeChoix.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        etapeCrack = construireEtapeCrack();
        etapeCrack.setVisible(false);
        etapeCrack.setManaged(false);

        etapes.getChildren().addAll(etapeChoix, etapeCrack);
        VBox colonne = new VBox(Jetons.rem(1.8), titre, etapes);
        colonne.setAlignment(Pos.CENTER);

        Label version = new Label("v" + fr.nylerp.launcher.update.SelfUpdater.installedVersion());
        version.setFont(Polices.gras(Jetons.rem(0.55)));
        version.setTextFill(Color.rgb(255, 255, 255, 0.75));
        StackPane.setAlignment(version, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(version, new Insets(0, Jetons.rem(0.8), Jetons.rem(0.6), 0));

        getChildren().addAll(new FondCiel(),
                new Ajuste(colonne, new Insets(Jetons.rem(1.2), Jetons.rem(1.2), Jetons.rem(1.6), Jetons.rem(1.2))), version);

        if (adding) {
            Button back = new Button("← Retour");
            back.getStyleClass().add("kit-sortir");
            back.setId("retour-accueil");
            back.setOnAction(e -> onBack.run());
            StackPane.setAlignment(back, Pos.TOP_LEFT);
            // SOUS la barre de la fenêtre (sa poignée capte les clics sur toute la largeur) ; cible de clic d'au moins 40 px
            back.setMinHeight(40);
            back.setPickOnBounds(true);
            StackPane.setMargin(back, new Insets(ChromeFenetre.hauteurBarre() + Jetons.rem(0.2), 0, 0, Jetons.rem(1.0)));
            getChildren().add(back);
        }

        int i = 0;
        for (Node n : new Node[]{titre, carteMicrosoft, carteCrack}) Animations.enter(n, Duration.millis(80 + (i++) * 70));
    }

    // ── étape « Ton pseudo » ──────────────────────────────────────────────────────────────────────

    private VBox construireEtapeCrack() {
        Label t = new Label("Ton pseudo");
        t.setFont(Polices.noir(Jetons.rem(1.5)));
        t.setTextFill(Jetons.ENCRE);

        champPseudo.getStyleClass().add("kit-champ");
        champPseudo.setId("champ-pseudo");
        champPseudo.setPromptText("Ton pseudo");
        champPseudo.setMaxWidth(Double.MAX_VALUE);
        champPseudo.setOnAction(e -> validerCrack());
        champPseudo.textProperty().addListener((o, a, b) -> cacherMessage());

        message.setFont(Polices.extra(Jetons.rem(0.7)));
        message.setTextFill(Color.web(MSG_ERREUR));
        message.setWrapText(true);
        message.setId("message-pseudo");
        message.setMaxWidth(Jetons.rem(16));
        message.setMinHeight(Region.USE_PREF_SIZE);
        cacherMessage();

        validerCrack.getStyleClass().add("kit-valider");
        validerCrack.setId("valider-crack");
        validerCrack.setMaxWidth(Double.MAX_VALUE);
        validerCrack.setOnAction(e -> validerCrack());

        Label securite = new Label("Mot de passe à choisir en jeu.");
        securite.setFont(Polices.gras(Jetons.rem(0.66)));
        securite.setTextFill(Jetons.ENCRE_3);

        Button retour = new Button("← Retour");
        retour.getStyleClass().add("kit-lien");
        retour.setStyle("-fx-background-color: transparent; -fx-padding: 0; -fx-font-size: " + px(Jetons.rem(0.7)) + ";");
        retour.setId("retour-choix");
        retour.setOnAction(e -> montrer(false));

        VBox contenu = new VBox(Jetons.rem(0.7), t, champPseudo, message, validerCrack, securite, retour);
        contenu.setAlignment(Pos.CENTER);
        contenu.setPrefWidth(Jetons.rem(16));

        CarteVerre carte = new CarteVerre(CarteVerre.Variante.OPAQUE, contenu);
        carte.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        VBox etape = new VBox(carte);
        etape.setAlignment(Pos.CENTER);
        etape.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return etape;
    }

    // ── Microsoft : flux de toujours, inchangé ────────────────────────────────────────────────────

    private void connexionMicrosoft() {
        carteMicrosoft.setOccupe(true, "Connexion…");
        carteCrack.setDisable(true);

        // Fenêtre de connexion Microsoft EMBARQUÉE (WebView OpenAuth) : l'utilisateur se
        // connecte entièrement dans la fenêtre (login + 2FA + compte famille gérés nativement
        // par la page Microsoft), et le launcher récupère le token via le redirect desktop
        // legacy — pas d'Azure, pas de device-code (qui échouait en invalid_grant pour les
        // comptes 2FA/famille). C'est le flux standard de MultiMC / Prism / HMCL.
        MicrosoftAuth.loginWithWebview().whenComplete((acc, err) -> Platform.runLater(() -> {
            carteMicrosoft.setOccupe(false, null);
            carteCrack.setDisable(false);
            if (err != null) {
                Throwable c = err.getCause() != null ? err.getCause() : err;
                String m = c.getMessage() != null ? c.getMessage() : c.toString();
                // L'utilisateur a simplement fermé la fenêtre = annulation, pas une erreur.
                String low = m == null ? "" : m.toLowerCase();
                if (low.contains("closed") || low.contains("cancel") || low.contains("annul")
                        || low.contains("user closed") || low.contains("aborted")) {
                    return;
                }
                // Explication CLAIRE (cause probable + étapes concrètes) au lieu du message HTTP brut.
                var explained = fr.nylerp.launcher.auth.AuthErrorExplainer.explain(m);
                NyleErrorDialog.show(explained.title(), explained.message(),
                        explained.steps(), explained.technical());
            } else {
                onAuthenticated.accept(acc);
            }
        }));
    }

    // ── crack ─────────────────────────────────────────────────────────────────────────────────────

    private void validerCrack() {
        if (verificationEnCours) return;
        Account compte;
        try {
            compte = OfflineAuth.login(champPseudo.getText());
        } catch (IllegalArgumentException ex) {
            afficherMessage("3 à 16 caractères : lettres, chiffres ou _");
            return;
        }
        verificationEnCours = true;
        validerCrack.setText("Un instant…");
        validerCrack.setDisable(true);
        VerifPseudo.verifier(compte.username()).whenComplete((r, e) -> Platform.runLater(() -> {
            verificationEnCours = false;
            validerCrack.setText("Jouer");
            validerCrack.setDisable(false);
            if (r == VerifPseudo.Resultat.ACHETE) {
                afficherMessage("Ce pseudo appartient à un compte acheté. Choisis-en un autre.");
                return;
            }
            onAuthenticated.accept(compte);
        }));
    }

    private void afficherMessage(String texte) {
        message.setText(texte);
        message.setVisible(true);
        message.setManaged(true);
    }

    private void cacherMessage() {
        message.setText("");
        message.setVisible(false);
        message.setManaged(false);
    }

    /** Bascule entre les deux cartes et l'étape « Ton pseudo », en fondu glissé. */
    private void montrer(boolean crack) {
        Region entrant = crack ? etapeCrack : etapeChoix;
        Region sortant = crack ? etapeChoix : etapeCrack;
        sortant.setVisible(false);
        sortant.setManaged(false);
        entrant.setManaged(true);
        entrant.setVisible(true);
        entrant.setOpacity(0);
        entrant.setTranslateY(Jetons.rem(0.6));
        FadeTransition f = new FadeTransition(Duration.millis(260), entrant);
        f.setToValue(1);
        TranslateTransition t = new TranslateTransition(Duration.millis(320), entrant);
        t.setToY(0);
        t.setInterpolator(Jetons.SORTIE);
        new ParallelTransition(f, t).play();
        if (crack) Platform.runLater(champPseudo::requestFocus);
        else cacherMessage();
    }

    // ── petits outils ─────────────────────────────────────────────────────────────────────────────

    private static Node silhouette(double taille) {
        SVGPath s = new SVGPath();
        s.setContent("M12 12.2a4.1 4.1 0 1 0 0-8.2 4.1 4.1 0 0 0 0 8.2Zm-7.4 7.6c.6-3.4 3.6-5.6 7.4-5.6s6.8 2.2 7.4 5.6c.1.6-.4 1.2-1 1.2H5.6c-.6 0-1.1-.6-1-1.2Z");
        s.setFill(Jetons.ICONE);
        s.setScaleX(taille / 24);
        s.setScaleY(taille / 24);
        return new StackPane(s);
    }

    private static String px(double v) { return String.format(java.util.Locale.ROOT, "%.1fpx", v); }
}
