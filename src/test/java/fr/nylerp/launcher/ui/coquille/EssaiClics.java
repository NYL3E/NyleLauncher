package fr.nylerp.launcher.ui.coquille;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.ui.LoginView;
import fr.nylerp.launcher.ui.SettingsView;
import fr.nylerp.launcher.ui.kit.ChromeFenetre;
import fr.nylerp.launcher.ui.kit.Jetons;
import fr.nylerp.launcher.ui.kit.Polices;
import fr.nylerp.launcher.univers.PreTelechargeur;
import fr.nylerp.launcher.univers.Services;
import fr.nylerp.launcher.univers.SourceEtatPokenyle;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Essai de VRAIS clics (refonte 2026-10, lot L15) : le bouton « Retour » des réglages et celui de l'ajout de compte
 * doivent répondre sur toute leur surface (centre et quatre bords), le bouton « Boutique » de l'en-tête doit ouvrir
 * UNE adresse (la boutique du site de l'univers), et Échap doit revenir en arrière.
 *
 * <p>Bug d'origine : la poignée de déplacement de la barre de la fenêtre (2,3 rem de haut, toute la largeur) recouvrait
 * le haut des écrans qui ne sont pas la coquille ; les boutons Retour y étaient morts sur environ 70 % de leur hauteur.
 *
 * <pre>java -Duser.home=ISOLÉ -cp … fr.nylerp.launcher.ui.coquille.EssaiClics [--capture DOSSIER]</pre>
 * Les clics sont réels (souris du système) : ne touche à rien pendant l'essai. Code de sortie 0 si tout passe.
 */
public final class EssaiClics {

    private static File sortieCaptures;
    private static Stage stage;
    private static ChromeFenetre chrome;
    private static final List<String> echecs = new ArrayList<>();
    private static final Account CRACK = new Account(Account.Type.OFFLINE, "NYLERPTEST5", "9e5b2be7-592c-3bbb-abcc-d347e774af9d", "0", null);

    private record EtatFerme() implements SourceEtatPokenyle {
        public boolean ouvertPour(String p) { return false; }
        public java.util.Optional<java.time.ZonedDateTime> ouverture() { return java.util.Optional.empty(); }
        public String libelleOuverture() { return "mi-décembre"; }
        public boolean crackAutorise() { return false; }
        public String messageCrack() { return ""; }
        public boolean discordActif() { return false; }
        public String discordUrl() { return "https://discord.gg/nyle"; }
        public void surChangement(Runnable r) { }
    }

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < args.length - 1; i++) if (args[i].equals("--capture")) sortieCaptures = new File(args[i + 1]);
        if (sortieCaptures != null) sortieCaptures.mkdirs();
        Platform.startup(() -> { });
        Services.etatPokenyle = new EtatFerme();
        Services.preTelechargeur = PreTelechargeur.AUCUN;
        try {
            for (double taille : new double[]{1.0, 1000.0 / 1180}) essayer(taille);
        } catch (Throwable t) {
            t.printStackTrace();
            echecs.add("exception : " + t);
        }
        System.out.println(echecs.isEmpty() ? "TOUT PASSE" : "ECHECS :\n - " + String.join("\n - ", echecs));
        Platform.exit();
        System.exit(echecs.isEmpty() ? 0 : 1);
    }

    private static void essayer(double e) throws Exception {
        String nom = Math.round(1180 * e) + "x" + Math.round(700 * e);
        fx(() -> {
            Polices.charger();
            Jetons.setEchelle(20.6136 / 19.2 * e);
            if (stage != null) stage.hide();
            stage = new Stage();
            chrome = ChromeFenetre.installer(stage, null, 1180 * e, 700 * e);
            var css = EssaiClics.class.getResource("/css/style.css");
            if (css != null) stage.getScene().getStylesheets().add(0, css.toExternalForm());
            stage.setX(120);
            stage.setY(80);
            stage.show();
            stage.toFront();
            stage.requestFocus();
            return null;
        });
        activerFenetre();

        // 1. réglages : Retour
        AtomicInteger retours = new AtomicInteger();
        fx(() -> { chrome.setContenu(new SettingsView(retours::incrementAndGet)); return null; });
        attendre(900);
        Node retourReglages = fx(() -> chrome.getCarte().lookup("#reglages-retour"));
        verifierBouton(nom + " réglages/Retour", retourReglages, retours, true);
        capturer("reglages-" + nom);
        // Échap revient aussi en arrière
        int avant = retours.get();
        fx(() -> { stage.requestFocus(); new Robot().keyType(KeyCode.ESCAPE); return null; });
        attendre(300);
        if (retours.get() != avant + 1) echecs.add(nom + " réglages : Échap n'a pas déclenché le retour (" + avant + " → " + retours.get() + ")");

        // 2. ajout de compte : Retour
        AtomicInteger retoursAjout = new AtomicInteger();
        fx(() -> { chrome.setContenu(new LoginView(a -> { }, retoursAjout::incrementAndGet)); return null; });
        attendre(1500);
        Node retourAjout = fx(() -> chrome.getCarte().lookup("#retour-accueil"));
        verifierBouton(nom + " ajout de compte/Retour", retourAjout, retoursAjout, false);
        capturer("ajout-compte-" + nom);

        // 3. boutique : UN clic = UNE adresse, celle de la boutique du site de l'univers ; un double-clic n'en ouvre qu'une
        List<String> ouvertes = new ArrayList<>();
        java.util.function.Consumer<String> avantOuvreur = Liens.ouvreur;
        Liens.ouvreur = ouvertes::add;
        try {
            Settings.get().universVu = "pokenyle";
            Coquille coquille = fx(() -> new Coquille(e, CRACK, () -> { }, () -> { }, a -> { }, () -> { }));
            fx(() -> {
                chrome.setContenu(coquille);
                var barre = chrome.getBarre().getChildren();
                barre.add(0, coquille.elementsGauche());
                barre.add(barre.size() - 2, coquille.elementsDroite());
                return null;
            });
            attendre(2500);
            Node boutique = fx(() -> coquille.elementsDroite().getChildren().get(1));
            Liens.oublierDerniereBoutique();
            clic(boutique, 0, 0);
            attendre(400);
            if (!ouvertes.equals(List.of("https://pokerayou.academy/boutique/")))
                echecs.add(nom + " boutique (Pokényle) : un clic a ouvert " + ouvertes);
            clic(boutique, 0, 0); attendre(60); clic(boutique, 0, 0);
            attendre(400);
            if (ouvertes.size() != 1) echecs.add(nom + " boutique : des clics rapprochés ont ouvert " + ouvertes.size() + " onglets");
            fx(() -> { coquille.naviguer("nylerp"); return null; });
            attendre(1800);
            Liens.oublierDerniereBoutique();
            ouvertes.clear();
            clic(boutique, 0, 0);
            attendre(400);
            if (!ouvertes.equals(List.of("https://www.nylerp.fr/boutique/")))
                echecs.add(nom + " boutique (NyleRP) : un clic a ouvert " + ouvertes);
            capturer("coquille-" + nom);
            fx(() -> { coquille.liberer(); return null; });
        } finally {
            Liens.ouvreur = avantOuvreur;
        }
    }

    /** Clique au centre puis aux quatre bords du bouton (à 4 px du bord, dans sa forme) : chacun doit déclencher l'action. */
    private static void verifierBouton(String nom, Node bouton, AtomicInteger compteur, boolean rond) throws Exception {
        if (bouton == null) { echecs.add(nom + " : bouton introuvable"); return; }
        Bounds b = fx(() -> bouton.localToScreen(bouton.getLayoutBounds()));
        Bounds s = fx(() -> bouton.localToScene(bouton.getLayoutBounds()));
        double barre = fx(ChromeFenetre::hauteurBarre);
        double minY = s.getMinY() - fx(() -> chrome.getCarte().localToScene(0, 0).getY());
        if (minY < barre - 0.5) echecs.add(String.format("%s : le bouton commence à %.1f px, sous la barre de la fenêtre il faudrait au moins %.1f px", nom, minY, barre));
        if (b.getWidth() < 39.5 || b.getHeight() < 39.5)
            echecs.add(String.format("%s : cible de clic trop petite (%.0f × %.0f px, 40 minimum)", nom, b.getWidth(), b.getHeight()));
        double marge = 4;
        double dx = b.getWidth() / 2 - marge - (rond ? 3 : 8), dy = b.getHeight() / 2 - marge - (rond ? 3 : 0);
        double[][] points = {{0, 0}, {-dx, 0}, {dx, 0}, {0, -dy}, {0, dy}};
        String[] noms = {"centre", "bord gauche", "bord droit", "bord haut", "bord bas"};
        for (int i = 0; i < points.length; i++) {
            int avant = compteur.get();
            clic(bouton, points[i][0], points[i][1]);
            attendre(250);
            if (compteur.get() != avant + 1) echecs.add(nom + " : clic mort au " + noms[i] + " (" + avant + " → " + compteur.get() + ")");
        }
    }

    private static void clic(Node n, double dx, double dy) throws Exception {
        fx(() -> {
            Bounds b = n.localToScreen(n.getLayoutBounds());
            Robot r = new Robot();
            r.mouseMove(b.getMinX() + b.getWidth() / 2 + dx, b.getMinY() + b.getHeight() / 2 + dy);
            return null;
        });
        attendre(120);
        fx(() -> { new Robot().mouseClick(MouseButton.PRIMARY); return null; });
    }

    /**
     * Une fenêtre qu'on vient d'ouvrir n'est pas active (macOS : le premier clic ne fait que l'activer) : on clique
     * une fois sur la poignée de la barre (zone inerte, qui ne déclenche rien) puis on attend le focus.
     */
    private static void activerFenetre() throws Exception {
        attendre(700);
        for (int i = 0; i < 20 && !fx(() -> stage.isFocused()); i++) {
            fx(() -> {
                Bounds b = stage.getScene().getRoot().localToScreen(stage.getScene().getRoot().getBoundsInLocal());
                Robot r = new Robot();
                r.mouseMove(b.getMinX() + b.getWidth() * 0.5, b.getMinY() + 36);
                r.mouseClick(MouseButton.PRIMARY);
                return null;
            });
            attendre(400);
        }
        if (!fx(() -> stage.isFocused())) echecs.add("la fenêtre d'essai n'a jamais pris le focus : clics non fiables");
    }

    // ── outils ───────────────────────────────────────────────────────────────────────────────────

    private static void capturer(String nom) throws Exception {
        if (sortieCaptures == null) return;
        for (double echelle : new double[]{1, 2}) {
            final double ech = echelle;
            WritableImage img = fx(() -> {
                SnapshotParameters sp = new SnapshotParameters();
                sp.setTransform(javafx.scene.transform.Transform.scale(ech, ech));
                sp.setFill(javafx.scene.paint.Color.TRANSPARENT);
                return chrome.getCarte().snapshot(sp, null);
            });
            ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", new File(sortieCaptures, nom + "-" + (int) echelle + "x.png"));
        }
    }

    private static void attendre(long ms) throws InterruptedException { Thread.sleep(ms); }

    private static <T> T fx(Callable<T> c) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> { try { f.complete(c.call()); } catch (Throwable t) { f.completeExceptionally(t); } });
        return f.get(60, TimeUnit.SECONDS);
    }
}
