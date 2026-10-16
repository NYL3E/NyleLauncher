package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.auth.MicrosoftAuth;
import fr.nylerp.launcher.auth.OfflineAuth;
import fr.nylerp.launcher.ui.compte.Ajuste;
import fr.nylerp.launcher.ui.compte.CarteChoix;
import fr.nylerp.launcher.ui.compte.FondCiel;
import fr.nylerp.launcher.ui.compte.VerifPseudo;
import fr.nylerp.launcher.ui.kit.CarteVerre;
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
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * Écran de connexion (refonte 2026-10, lot L4), au design du site Pokényle : le ciel aux trois
 * coffres en fond, un titre Northura, et DEUX grandes cartes qui parlent la langue des joueurs :
 * <ul>
 *   <li>« J'ai acheté Minecraft » → la connexion Microsoft embarquée de toujours
 *       ({@link MicrosoftAuth#loginWithWebview()}, inchangée) ;</li>
 *   <li>« Je joue en crack » → une étape « Ton pseudo », validée comme avant par
 *       {@link OfflineAuth#login(String)}, plus une vérification que le pseudo n'appartient pas à
 *       un compte acheté (NyleAuth l'imposerait en premium et refuserait le joueur).</li>
 * </ul>
 * Mode « Ajouter un compte » (multicompte, 3 au plus — la limite est tenue par
 * {@code LauncherApp.onAuthenticated} / {@code AccountStore}) quand {@code onBack} n'est pas nul :
 * autre titre, rappel de la limite, bouton Retour vers l'accueil.
 */
public class LoginView extends StackPane {

    private static final String MSG_ERREUR = "#d0475b";
    private static final String MSG_ALERTE = "#b0661a";

    private final Consumer<Account> onAuthenticated;
    private final StackPane scene = new StackPane();
    private final VBox etapeChoix;
    private final VBox etapeCrack;
    private final CarteChoix carteMicrosoft;
    private final CarteChoix carteCrack;
    private final TextField champPseudo = new TextField();
    private final Label message = new Label();
    private final Button validerCrack = new Button("Jouer en crack");
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

        // ── titre ─────────────────────────────────────────────────────────────────────────────────
        TitreEclats titre = new TitreEclats(adding ? "Ajouter un compte" : "Bienvenue",
                TitreEclats.Style.PRINCIPAL, Jetons.rem(2.7), null);
        Label sous = noteBlanche(adding ? "3 comptes max · Microsoft ou crack" : "Comment joues-tu à Minecraft ?",
                Jetons.rem(1.0));

        // ── étape 1 : les deux cartes ─────────────────────────────────────────────────────────────
        carteMicrosoft = new CarteChoix(Icons.microsoftLogo(Jetons.rem(1.55)),
                "J'ai acheté Minecraft", "Compte Microsoft, premium",
                new String[]{"Tu te connectes sur la page officielle de Microsoft.",
                        "Ton pseudo et ton skin sont repris automatiquement."},
                "Se connecter avec Microsoft");
        carteMicrosoft.setId("carte-microsoft");
        carteMicrosoft.setSurChoix(this::connexionMicrosoft);

        carteCrack = new CarteChoix(silhouette(Jetons.rem(1.9)),
                "Je joue en crack", "Gratuit, juste un pseudo, pas besoin d'acheter le jeu",
                new String[]{"Pas de compte Microsoft : ton pseudo suffit.",
                        "Il est protégé par un mot de passe que tu choisis en jeu."},
                "Jouer en crack");
        carteCrack.setId("carte-crack");
        carteCrack.setSurChoix(() -> montrer(true));

        HBox cartes = new HBox(Jetons.rem(1.6), carteMicrosoft, carteCrack);
        cartes.setAlignment(Pos.CENTER);
        // capsule claire (la .f-date du site) : posée sur l'herbe et les coffres, du texte blanc ne se lirait pas
        Label aide = new Label("Pas sûr ? Tu as payé Minecraft Java : « J'ai acheté Minecraft ». Sinon : « Je joue en crack ».");
        aide.getStyleClass().add("kit-capsule");
        aide.setStyle("-fx-font-size: " + px(Jetons.rem(0.72)) + "; -fx-padding: " + px(Jetons.rem(0.3)) + " " + px(Jetons.rem(0.9)) + ";");
        etapeChoix = new VBox(Jetons.rem(1.0), cartes, aide);
        etapeChoix.setAlignment(Pos.CENTER);

        // ── étape 2 : le pseudo crack ─────────────────────────────────────────────────────────────
        etapeCrack = construireEtapeCrack();
        etapeCrack.setVisible(false);
        etapeCrack.setManaged(false);

        scene.getChildren().addAll(etapeChoix, etapeCrack);
        VBox colonne = new VBox(Jetons.rem(0.45), titre, sous, espace(Jetons.rem(0.9)), scene);
        colonne.setAlignment(Pos.CENTER);
        colonne.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        // ── pied de page et retour ────────────────────────────────────────────────────────────────
        Label footer = noteBlanche("NyleLauncher v" + fr.nylerp.launcher.update.SelfUpdater.installedVersion()
                + "  ·  play.nylerp.fr", Jetons.rem(0.62));
        footer.setOpacity(0.9);
        StackPane.setAlignment(footer, Pos.BOTTOM_CENTER);
        StackPane.setMargin(footer, new Insets(0, 0, Jetons.rem(0.9), 0));

        // la colonne se réduit si elle ne tient pas (fenêtre 1000 × 620, message d'erreur long…)
        Ajuste ajuste = new Ajuste(colonne, new Insets(Jetons.rem(1.2), Jetons.rem(1.2), Jetons.rem(2.2), Jetons.rem(1.2)));
        getChildren().addAll(new FondCiel(), ajuste, footer);

        if (adding) {
            Button back = new Button("← Retour");
            back.getStyleClass().add("kit-sortir");
            back.setId("retour-accueil");
            back.setOnAction(e -> onBack.run());
            StackPane.setAlignment(back, Pos.TOP_LEFT);
            StackPane.setMargin(back, new Insets(Jetons.rem(1.0), 0, 0, Jetons.rem(1.0)));
            getChildren().add(back);
        }

        int i = 0;
        for (Node n : new Node[]{titre, sous, carteMicrosoft, carteCrack, aide}) {
            Animations.enter(n, Duration.millis(80 + (i++) * 60));
        }
    }

    // ── construction ──────────────────────────────────────────────────────────────────────────────

    private VBox construireEtapeCrack() {
        Button retour = new Button("← Changer de choix");
        retour.getStyleClass().add("kit-lien");
        retour.setStyle("-fx-background-color: transparent; -fx-padding: 0; -fx-font-size: " + px(Jetons.rem(0.74)) + ";");
        retour.setId("retour-choix");
        retour.setOnAction(e -> montrer(false));

        Label t = new Label("Ton pseudo");
        t.setFont(Polices.noir(Jetons.rem(1.45)));
        t.setTextFill(Jetons.ENCRE);
        Label st = new Label("Le nom que les autres joueurs verront en jeu.");
        st.setFont(Polices.gras(Jetons.rem(0.78)));
        st.setTextFill(Jetons.ENCRE_2);

        champPseudo.getStyleClass().add("kit-champ");
        champPseudo.setId("champ-pseudo");
        champPseudo.setPromptText("Steve");
        champPseudo.setMaxWidth(Double.MAX_VALUE);
        champPseudo.setOnAction(e -> validerCrack());
        champPseudo.textProperty().addListener((o, a, b) -> cacherMessage());

        Label regles = new Label("3 à 16 caractères : lettres, chiffres ou _");
        regles.setFont(Polices.gras(Jetons.rem(0.66)));
        regles.setTextFill(Jetons.ENCRE_3);

        message.setFont(Polices.extra(Jetons.rem(0.7)));
        message.setWrapText(true);
        message.setId("message-pseudo");
        message.setMaxWidth(Jetons.rem(19));
        message.setMinHeight(Region.USE_PREF_SIZE);
        cacherMessage();

        validerCrack.getStyleClass().add("kit-valider");
        validerCrack.setId("valider-crack");
        validerCrack.setMaxWidth(Double.MAX_VALUE);
        validerCrack.setOnAction(e -> validerCrack());

        SVGPath cadenas = new SVGPath();
        cadenas.setContent("M7 10V7a5 5 0 0 1 10 0v3h1a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V11a1 1 0 0 1 1-1h1Zm2 0h6V7a3 3 0 0 0-6 0v3Z");
        cadenas.setFill(Jetons.ACTIF);
        cadenas.setScaleX(Jetons.rem(0.7) / 24);
        cadenas.setScaleY(Jetons.rem(0.7) / 24);
        Label securite = new Label("À ta première connexion au serveur, tu choisis un mot de passe : personne d'autre "
                + "ne pourra jouer sous ton pseudo. Prends un pseudo qui n'est pas déjà celui d'un compte Minecraft acheté.");
        securite.setFont(Polices.gras(Jetons.rem(0.64)));
        securite.setTextFill(Jetons.ENCRE_2);
        securite.setWrapText(true);
        securite.setMaxWidth(Jetons.rem(17.4));
        securite.setMinHeight(Region.USE_PREF_SIZE);
        HBox ligneSecurite = new HBox(Jetons.rem(0.4), new StackPane(cadenas), securite);
        ligneSecurite.setAlignment(Pos.TOP_LEFT);

        VBox champ = new VBox(Jetons.rem(0.3), champPseudo, regles, message);
        VBox contenu = new VBox(Jetons.rem(0.55), retour, espace(Jetons.rem(0.1)), t, st, espace(Jetons.rem(0.25)),
                champ, validerCrack, espace(Jetons.rem(0.15)), ligneSecurite);
        contenu.setFillWidth(true);
        contenu.setPrefWidth(Jetons.rem(19));

        CarteVerre carte = new CarteVerre(CarteVerre.Variante.OPAQUE, contenu);
        carte.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        VBox etape = new VBox(carte);
        etape.setAlignment(Pos.CENTER);
        return etape;
    }

    // ── Microsoft : flux de toujours, inchangé ────────────────────────────────────────────────────

    private void connexionMicrosoft() {
        carteMicrosoft.setOccupe(true, "Connexion Microsoft…");
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
            afficherMessage(ex.getMessage(), MSG_ERREUR);
            return;
        }
        verificationEnCours = true;
        validerCrack.setText("Vérification du pseudo…");
        validerCrack.setDisable(true);
        VerifPseudo.verifier(compte.username()).whenComplete((r, e) -> Platform.runLater(() -> {
            verificationEnCours = false;
            validerCrack.setText("Jouer en crack");
            validerCrack.setDisable(false);
            if (r == VerifPseudo.Resultat.ACHETE) {
                afficherMessage("« " + compte.username() + " » est le pseudo d'un compte Minecraft acheté : sur nos serveurs, "
                        + "il faut alors se connecter avec ce compte Microsoft. Choisis un autre pseudo pour jouer en crack.",
                        MSG_ALERTE);
                return;
            }
            onAuthenticated.accept(compte);
        }));
    }

    private void afficherMessage(String texte, String couleur) {
        message.setText(texte);
        message.setTextFill(Color.web(couleur));
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
        VBox entrant = crack ? etapeCrack : etapeChoix;
        VBox sortant = crack ? etapeChoix : etapeCrack;
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

    /** Texte blanc posé sur le ciel (la .lanceur-note du site, en plus grand). */
    private static Label noteBlanche(String texte, double taille) {
        Label l = new Label(texte);
        l.setFont(Polices.extra(taille));
        l.setTextFill(Color.WHITE);
        l.setWrapText(true);
        l.setAlignment(Pos.CENTER);
        l.setStyle("-fx-text-alignment: center;");
        l.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(30, 70, 140, 0.65), taille * 0.6, 0.15, 0, taille * 0.08));
        return l;
    }

    private static Node silhouette(double taille) {
        SVGPath s = new SVGPath();
        s.setContent("M12 12.2a4.1 4.1 0 1 0 0-8.2 4.1 4.1 0 0 0 0 8.2Zm-7.4 7.6c.6-3.4 3.6-5.6 7.4-5.6s6.8 2.2 7.4 5.6c.1.6-.4 1.2-1 1.2H5.6c-.6 0-1.1-.6-1-1.2Z");
        s.setFill(Jetons.ICONE);
        s.setScaleX(taille / 24);
        s.setScaleY(taille / 24);
        return new StackPane(s);
    }

    private static Region espace(double h) {
        Region r = new Region();
        r.setMinHeight(h);
        r.setPrefHeight(h);
        r.setMaxHeight(h);
        return r;
    }

    private static String px(double v) { return String.format(java.util.Locale.ROOT, "%.1fpx", v); }
}
