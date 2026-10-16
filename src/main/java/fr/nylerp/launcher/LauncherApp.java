package fr.nylerp.launcher;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.auth.AccountStore;
import fr.nylerp.launcher.config.Constants;
import fr.nylerp.launcher.fond.Fond;
import fr.nylerp.launcher.ui.LoginView;
import fr.nylerp.launcher.ui.SettingsView;
import fr.nylerp.launcher.ui.coquille.Coquille;
import fr.nylerp.launcher.ui.kit.ChromeFenetre;
import fr.nylerp.launcher.ui.kit.Jetons;
import fr.nylerp.launcher.ui.kit.Polices;
import fr.nylerp.launcher.util.CrashReporter;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * L'application. Refonte 2026-10 (lot L5) : une seule fenêtre sans bordure ({@link ChromeFenetre} du
 * kit, coins arrondis), dont la zone de contenu accueille tour à tour la connexion, la coquille
 * (univers Pokényle / NyleRP) et les paramètres. La croix, Cmd+W et Alt+F4 passent par
 * {@link Fond} : la fenêtre se range dans la barre système (ou le launcher quitte si l'icône n'existe pas).
 */
public class LauncherApp extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(LauncherApp.class);

    /** Taille de référence de la fenêtre (contenu) ; réduite si l'écran est plus petit. */
    public static final double LARGEUR = 1180, HAUTEUR = 700;
    /** Rapport entre le rem du site (19,2 px à 1440) et celui du haut de page dans la fenêtre 1180 × 700
     *  (20,61 px, relevé par l'agent du site) : les composants du kit gardent leurs proportions face au fond. */
    private static final double REM_FENETRE = 20.6136 / 19.2;

    private Stage stage;
    private ChromeFenetre chrome;
    private double echelle = 1;
    private Account account;
    /** La coquille est gardée d'une page à l'autre (paramètres → retour) : ses vidéos, sa musique et
     *  son ciel ne redémarrent pas. Reconstruite seulement quand le compte change. */
    private Coquille coquille;
    private boolean fermetureEnCours;

    @Override
    public void start(Stage stage) {
        loadFonts();
        Polices.charger();
        String installedBs = fr.nylerp.launcher.update.SelfUpdater.installedVersion();
        LOG.info("Bootstrap version detected: {} (min required: {})",
                installedBs, fr.nylerp.launcher.update.BrokenBootstrapDialog.MIN_REQUIRED_BOOTSTRAP);
        if (fr.nylerp.launcher.update.BrokenBootstrapDialog.showIfBootstrapTooOld(installedBs)) {
            LOG.warn("Bootstrap {} is below the required minimum {} — showing redownload dialog",
                    installedBs, fr.nylerp.launcher.update.BrokenBootstrapDialog.MIN_REQUIRED_BOOTSTRAP);
            return;
        }
        // RAM du PC détectée en tâche de fond (sous Windows elle peut passer par PowerShell, ~1 s) :
        // la section Mémoire des paramètres (mode Auto) lit ensuite la valeur en cache sans figer l'UI.
        Thread ram = new Thread(fr.nylerp.launcher.util.SystemRam::totalMb, "nyle-ram-detect");
        ram.setDaemon(true);
        ram.start();
        CrashReporter.promptIfPending();
        // Médias distants (fond et oiseaux de Pokényle, musique de NyleRP) : téléchargés en fond dès
        // maintenant, pour que l'écran les ait au plus tôt.
        fr.nylerp.launcher.update.Medias.demarrer();
        // Annonces et état distant de Pokényle (pose Services.etatPokenyle), puis pré-téléchargement
        // (pose Services.preTelechargeur) : avant la coquille, qui lit ces deux services. Aussi en mode fond.
        fr.nylerp.launcher.annonces.Annonces.demarrer();
        fr.nylerp.launcher.update.pretelechargement.PreTelechargement.demarrer();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { fr.nylerp.launcher.update.pretelechargement.PreTelechargement.arreter(); } catch (Throwable ignore) { }
            try { fr.nylerp.launcher.annonces.Annonces.arreter(); } catch (Throwable ignore) { }
        }, "nyle-arret-services"));

        this.stage = stage;
        stage.setTitle(Constants.APP_NAME);
        try {
            stage.getIcons().add(new javafx.scene.image.Image(getClass().getResourceAsStream("/images/app_icon.png")));
        } catch (Exception e) {
            LOG.warn("Could not load app icon: {}", e.toString());
        }

        // Fenêtre : 1180 × 700, réduite d'un bloc (et tout le kit avec elle) si l'écran est plus petit.
        echelle = calculerEchelle();
        Jetons.setEchelle(echelle * REM_FENETRE);
        LOG.info("Fenêtre : échelle {} ({} × {})", String.format("%.3f", echelle),
                Math.round(LARGEUR * echelle), Math.round(HAUTEUR * echelle));
        chrome = ChromeFenetre.installer(stage, null, LARGEUR * echelle, HAUTEUR * echelle);
        var styleHistorique = getClass().getResource("/css/style.css");
        if (styleHistorique != null) stage.getScene().getStylesheets().add(0, styleHistorique.toExternalForm());
        // La croix et Cmd+W demandent la fermeture comme le ferait le système : c'est Fond qui décide
        // (ranger dans la barre système, ou quitter sans icône).
        chrome.setSurFermer(() -> {
            if (fermetureEnCours) return;
            fermetureEnCours = true;
            try { stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST)); }
            finally { fermetureEnCours = false; }
        });

        Fond.surMasquage(() -> { if (coquille != null) coquille.suspendre(); });
        Fond.surAffichage(() -> { if (coquille != null && chromeMontre(coquille)) coquille.reprendre(); });
        Fond.surNavigation(cible -> {
            if ("reglages".equalsIgnoreCase(cible)) { if (account != null) showSettings(); return; }
            if (fr.nylerp.launcher.annonces.Annonces.estUrl(cible)) {
                fr.nylerp.launcher.annonces.Annonces.ouvrirUrl(cible);
                return;
            }
            if (coquille != null) {
                if (!chromeMontre(coquille)) show(coquille);
                coquille.naviguer(cible);
            }
        });

        // Mode fond (démarrage automatique) : icône de barre seulement, l'interface sera construite
        // à la première ouverture. Sinon : démarrage habituel.
        if (Fond.installer(stage, this::construireEtAfficher)) return;
        construireEtAfficher();
    }

    /** L'échelle qui fait tenir la fenêtre (ombre comprise) dans l'écran principal, sans jamais agrandir. */
    private static double calculerEchelle() {
        try {
            Rectangle2D vb = Screen.getPrimary().getVisualBounds();
            double marge = 2 * ChromeFenetre.MARGE + 24;
            return Math.max(0.6, Math.min(1.0, Math.min((vb.getWidth() - marge) / LARGEUR, (vb.getHeight() - marge) / HAUTEUR)));
        } catch (Throwable t) {
            return 1.0;
        }
    }

    /** Construit le premier écran (session reprise ou connexion) et montre la fenêtre. */
    private void construireEtAfficher() {
        Account saved = AccountStore.active();
        if (saved != null) {
            LOG.info("Resuming saved session for {} ({})", saved.username(), saved.type());
            onAuthenticated(saved);
            silentRefresh(saved);
        } else {
            showLogin();
        }
        stage.show();
        stage.centerOnScreen();
    }

    /** Background MS token refresh for the given account (no-op for offline).
     *  On hard rejection (revoked/expired) the account is dropped from the
     *  roster ; transient failures keep the cached token. */
    private void silentRefresh(Account acc) {
        if (acc.type() != Account.Type.MICROSOFT || acc.refreshToken() == null) return;
        fr.nylerp.launcher.auth.MicrosoftAuth.refresh(acc.refreshToken())
                .whenComplete((refreshed, err) -> javafx.application.Platform.runLater(() -> {
                    if (err != null) {
                        String msg = err.toString();
                        if (msg.contains("invalid_grant") || msg.contains("expired")
                                || msg.contains("revoked")) {
                            LOG.warn("MS refresh token rejected — removing account: {}", msg);
                            if (account != null && !account.isOffline()
                                    && acc.uuid() != null && acc.uuid().equals(account.uuid())) {
                                onLogout();
                            }
                        } else {
                            LOG.warn("Silent MS refresh failed (keeping cached token): {}", msg);
                        }
                    } else {
                        LOG.info("MS token refreshed silently for {}", refreshed.username());
                        AccountStore.updateActive(refreshed);
                        this.account = refreshed;
                    }
                }));
    }

    public void showLogin() {
        show(new LoginView(this::onAuthenticated));
    }

    /** "Ajouter un compte" — login screen with a back arrow to the main view. */
    public void showAddAccount() {
        show(new LoginView(this::onAuthenticated, () -> {
            if (account != null && coquille != null) show(coquille);
            else showLogin();
        }));
    }

    public void onAuthenticated(Account account) {
        if (!AccountStore.addAndActivate(account)) {
            javafx.scene.control.Alert a = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.WARNING,
                    "Tu as déjà 3 comptes enregistrés. Retire un compte (bouton "
                    + "Déconnexion) avant d'en ajouter un nouveau.");
            a.setTitle("Comptes");
            a.setHeaderText(null);
            a.showAndWait();
            return;
        }
        this.account = account;
        nouvelleCoquille(account);
        fr.nylerp.launcher.annonces.Annonces.compteChange();
        fr.nylerp.launcher.update.pretelechargement.PreTelechargement.reverifierAcces();
    }

    /** Switches to another roster account from the account menu. */
    public void switchAccount(Account target) {
        AccountStore.setActive(target);
        this.account = AccountStore.active();
        nouvelleCoquille(this.account);
        fr.nylerp.launcher.annonces.Annonces.compteChange();
        fr.nylerp.launcher.update.pretelechargement.PreTelechargement.reverifierAcces();
        silentRefresh(this.account);
    }

    public void showSettings() {
        if (coquille != null) coquille.enArrierePlan();
        show(new SettingsView(() -> {
            if (account != null) {
                if (coquille == null) nouvelleCoquille(account);
                else { show(coquille); coquille.reprendre(); }
            } else {
                showLogin();
            }
        }));
    }

    /** "Déconnexion" = remove the ACTIVE account from the roster. If other
     *  accounts remain, the first one takes over ; otherwise back to login. */
    public void onLogout() {
        AccountStore.removeActive();
        Account next = AccountStore.active();
        fr.nylerp.launcher.update.pretelechargement.PreTelechargement.reverifierAcces();
        if (next != null) {
            this.account = next;
            nouvelleCoquille(next);
        } else {
            this.account = null;
            if (coquille != null) { coquille.liberer(); coquille = null; }
            showLogin();
        }
    }

    /** Un compte = une coquille (son lanceur, ses univers) : la précédente rend ses vidéos et son ciel. */
    private void nouvelleCoquille(Account compte) {
        if (coquille != null) coquille.liberer();
        coquille = new Coquille(echelle, compte, this::showSettings, this::onLogout,
                this::switchAccount, this::showAddAccount);
        // Boutique intégrée : la vraie page du site dans un WebView, sous l'en-tête, « Retour » la referme.
        final Coquille c = coquille;
        c.setSurBoutique((univers, pseudo) -> {
            fr.nylerp.launcher.ui.boutique.BoutiqueView vue =
                    fr.nylerp.launcher.ui.boutique.Boutique.ouvrir(univers, pseudo);
            vue.setSurRetour(c::retour);
            // Quand la vue quitte l'écran (retour de l'en-tête ou de la boutique), son WebView est libéré.
            vue.parentProperty().addListener((o, avant, apres) -> { if (apres == null) vue.fermer(); });
            c.afficherVue(vue);
        });
        if (!fr.nylerp.launcher.config.Settings.get().accueilVu) {
            // Premier lancement après la mise à jour : l'écran « Nouveau launcher » explique le
            // démarrage en fond et permet de le couper, puis on arrive dans les univers.
            fr.nylerp.launcher.ui.Accueil accueil = new fr.nylerp.launcher.ui.Accueil(fr.nylerp.launcher.config.Settings.get());
            accueil.setSurValider(reglages -> {
                Fond.appliquerReglages();
                show(c);
            });
            show(accueil);
            return;
        }
        show(coquille);
    }

    private boolean chromeMontre(Parent root) {
        return chrome != null && root != null && root.getParent() == chrome.getCarte();
    }

    /** Remplace l'écran ; l'en-tête de la coquille ne vit dans la barre que quand la coquille est affichée. */
    private void show(Parent root) {
        var barre = chrome.getBarre().getChildren();
        barre.removeIf(n -> n.getProperties().containsKey("coquille"));
        chrome.setContenu(root);
        if (root instanceof Coquille c) {
            var g = c.elementsGauche();
            var d = c.elementsDroite();
            g.getProperties().put("coquille", true);
            d.getProperties().put("coquille", true);
            barre.add(0, g);
            // juste après la poignée, avant réduire / fermer
            barre.add(barre.size() - 2, d);
        }
    }

    public static void loadFonts() {
        String[] files = {
                "Montserrat-Regular.ttf",
                "Montserrat-Medium.ttf",
                "Montserrat-SemiBold.ttf",
                "Montserrat-Bold.ttf",
                "Montserrat-Black.ttf"
        };
        for (String f : files) {
            try {
                javafx.scene.text.Font font = javafx.scene.text.Font.loadFont(
                        LauncherApp.class.getResourceAsStream("/fonts/" + f), 14);
                if (font != null) {
                    LOG.info("Font loaded: file='{}' family='{}' name='{}' style='{}'",
                            f, font.getFamily(), font.getName(), font.getStyle());
                } else {
                    LOG.warn("Font.loadFont returned null for {}", f);
                }
            } catch (Exception e) {
                LOG.warn("Could not load font {}: {}", f, e.toString());
            }
        }
    }
}
