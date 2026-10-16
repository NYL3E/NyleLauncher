package fr.nylerp.launcher.ui.coquille;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.ui.kit.BoutonJouer;
import fr.nylerp.launcher.ui.kit.CarteVerre;
import fr.nylerp.launcher.ui.kit.ChromeFenetre;
import fr.nylerp.launcher.ui.kit.Jetons;
import fr.nylerp.launcher.ui.kit.Polices;
import fr.nylerp.launcher.ui.univers.BlocLancement;
import fr.nylerp.launcher.ui.univers.CielPokenyle;
import fr.nylerp.launcher.ui.univers.UniversNylerp;
import fr.nylerp.launcher.ui.univers.UniversPokenyle;
import fr.nylerp.launcher.univers.PreTelechargeur;
import fr.nylerp.launcher.univers.Services;
import fr.nylerp.launcher.univers.SourceEtatPokenyle;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Essai de non-régression « la zone des univers n'est JAMAIS vide » (refonte 2026-10, lot L12).
 *
 * <p>Bug d'origine : en changeant d'univers deux fois en moins de 420 ms (ou après un changement de compte), le
 * nettoyage différé de la première transition retirait le nœud que la seconde venait d'afficher, puis la seconde
 * retirait le reste : zone vide, bleu uni. Ici, chaque séquence se termine par une vérification : le bon univers est
 * le seul enfant, opaque, à l'échelle 1, avec son ciel / son bloc de lancement.
 *
 * <pre>java -Duser.home=ISOLÉ -cp … fr.nylerp.launcher.ui.coquille.EssaiBascule [--capture DOSSIER]</pre>
 * Code de sortie 0 si tout passe, 1 sinon. Ne JAMAIS le lancer sans {@code -Duser.home} isolé : il écrit les réglages.
 */
public final class EssaiBascule {

    private static File sortieCaptures;
    private static Stage stage;
    private static ChromeFenetre chrome;
    private static Coquille coquille;
    private static final List<String> echecs = new ArrayList<>();

    private static final Account PREMIUM = new Account(Account.Type.MICROSOFT, "Sacha", "9b3f3a0e-6a1d-4f7e-9d0a-7e0c4a1b2c3d", "x", null);
    private static final Account CRACK = new Account(Account.Type.OFFLINE, "NYLERPTEST5", "9e5b2be7-592c-3bbb-abcc-d347e774af9d", "0", null);

    /** Source d'état simulée : Pokényle fermé, ou ouvert. */
    private record Etat(boolean ouvert) implements SourceEtatPokenyle {
        public boolean ouvertPour(String p) { return ouvert; }
        public java.util.Optional<java.time.ZonedDateTime> ouverture() { return java.util.Optional.empty(); }
        public String libelleOuverture() { return "mi-décembre"; }
        public boolean crackAutorise() { return false; }
        public String messageCrack() { return "Pokényle demande pour l'instant un compte Minecraft acheté."; }
        public boolean discordActif() { return false; }
        public String discordUrl() { return "https://discord.gg/nyle"; }
        public void surChangement(Runnable r) { }
    }

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < args.length - 1; i++) if (args[i].equals("--capture")) sortieCaptures = new File(args[i + 1]);
        if (sortieCaptures != null) sortieCaptures.mkdirs();
        Platform.startup(() -> { });
        fx(() -> {
            Polices.charger();
            Jetons.setEchelle(20.6136 / 19.2);
            stage = new Stage();
            chrome = ChromeFenetre.installer(stage, null, 1180, 700);
            var css = EssaiBascule.class.getResource("/css/style.css");
            if (css != null) stage.getScene().getStylesheets().add(0, css.toExternalForm());
            stage.show();
            return null;
        });
        int code = 1;
        try {
            scenarios();
            code = echecs.isEmpty() ? 0 : 1;
        } catch (Throwable t) {
            t.printStackTrace();
            echecs.add("exception : " + t);
        }
        System.out.println(echecs.isEmpty() ? "TOUT PASSE" : "ECHECS :\n - " + String.join("\n - ", echecs));
        Platform.exit();
        System.exit(echecs.isEmpty() ? 0 : code);
    }

    // ── scénarios ────────────────────────────────────────────────────────────────────────────────

    private static void scenarios() throws Exception {
        Services.etatPokenyle = new Etat(false);
        Services.preTelechargeur = PreTelechargeur.AUCUN;

        // A. le scénario de l'owner : crack, NyleRP, changement de compte, puis NyleRP → Pokényle
        ouvrir(CRACK, "nylerp");
        attendre(1200);
        verifier("A1 crack, NyleRP au départ", "nylerp");
        changerDeCompte(PREMIUM, "nylerp");
        attendre(1200);
        verifier("A2 après changement de compte (premium), NyleRP", "nylerp");
        naviguer("pokenyle");
        attendre(1200);
        verifier("A3 NyleRP → Pokényle après changement de compte", "pokenyle");
        capturer("apres-A3");
        changerDeCompte(CRACK, "pokenyle");
        attendre(1200);
        verifier("A4 changement de compte vers un crack sur Pokényle", "pokenyle");
        naviguer("nylerp");
        attendre(1200);
        verifier("A5 Pokényle → NyleRP", "nylerp");
        naviguer("pokenyle");
        attendre(1200);
        verifier("A6 NyleRP → Pokényle (crack)", "pokenyle");

        // B. allers-retours rapides (moins de 420 ms entre deux changements : la transition précédente n'est pas finie)
        for (int pas : new int[]{40, 150, 300}) {
            ouvrir(CRACK, "nylerp");
            attendre(1000);
            naviguer("pokenyle"); attendre(pas);
            naviguer("nylerp"); attendre(pas);
            naviguer("pokenyle"); attendre(pas);
            naviguer("nylerp"); attendre(pas);
            naviguer("pokenyle");
            attendre(1300);
            verifier("B pas=" + pas + " ms, 5 bascules rapides, fin sur Pokényle", "pokenyle");
            if (pas == 150) capturer("apres-B");
            naviguer("nylerp"); attendre(pas);
            naviguer("pokenyle"); attendre(pas);
            naviguer("nylerp");
            attendre(1300);
            verifier("B' pas=" + pas + " ms, fin sur NyleRP", "nylerp");
        }

        // C. changement de compte PENDANT une transition
        ouvrir(CRACK, "nylerp");
        attendre(900);
        naviguer("pokenyle"); attendre(100);
        changerDeCompte(PREMIUM, "pokenyle");
        attendre(100);
        naviguer("nylerp"); attendre(100);
        naviguer("pokenyle");
        attendre(1300);
        verifier("C bascule rapide autour d'un changement de compte", "pokenyle");

        // D. boutique (vue de navigation), retour, réglages (arrière-plan), retour, fenêtre rangée puis rouverte
        ouvrir(CRACK, "pokenyle");
        attendre(1000);
        fx(() -> { coquille.afficherVue(new StackPane()); return null; });
        attendre(400);
        fx(() -> { coquille.retour(); return null; });
        attendre(800);
        verifier("D1 boutique puis retour", "pokenyle");
        fx(() -> { coquille.enArrierePlan(); return null; });
        attendre(300);
        fx(() -> { coquille.reprendre(); return null; });
        attendre(800);
        verifier("D2 réglages puis retour", "pokenyle");
        fx(() -> { coquille.suspendre(); return null; });
        attendre(300);
        changerDeCompte(PREMIUM, "pokenyle");
        fx(() -> { coquille.reprendre(); return null; });
        attendre(1000);
        verifier("D3 fenêtre rangée, changement de compte, rouverte", "pokenyle");
        naviguer("nylerp");
        fx(() -> { coquille.suspendre(); return null; });
        attendre(600);
        fx(() -> { coquille.reprendre(); return null; });
        attendre(1000);
        verifier("D4 bascule puis rangée / rouverte", "nylerp");

        // F. une construction d'univers qui échoue ne laisse JAMAIS un écran vide : vue de repli, puis « Réessayer »
        ouvrir(CRACK, "nylerp");
        attendre(900);
        fx(() -> { Coquille.echecConstructionSimule = m -> m == fr.nylerp.launcher.config.ModeDeJeu.POKENYLE; return null; });
        naviguer("pokenyle");
        attendre(1000);
        String repli = fx(() -> {
            var enfants = coquille.sceneUnivers().getChildren();
            if (enfants.size() != 1) return enfants.size() + " enfants";
            return trouver(enfants.get(0), javafx.scene.control.Button.class) == null ? "pas de bouton Réessayer" : null;
        });
        if (repli == null) System.out.println("OK   F1 échec de construction : vue de repli avec « Réessayer »");
        else { System.out.println("FAIL F1 : " + repli); echecs.add("F1 : " + repli); }
        capturer("repli");
        fx(() -> { Coquille.echecConstructionSimule = null; return null; });
        fx(() -> {
            var b = trouver(coquille.sceneUnivers().getChildren().get(0), javafx.scene.control.Button.class);
            if (b != null) b.fire();
            return null;
        });
        attendre(1300);
        verifier("F2 « Réessayer » reconstruit Pokényle", "pokenyle");

        // E. pendant un téléchargement : le segment est verrouillé, l'univers ne bouge pas et reste valide
        ouvrir(CRACK, "pokenyle");
        attendre(1000);
        fx(() -> { coquille.lanceur().avancement().setProgress(0.4); return null; });
        naviguer("nylerp");
        attendre(1000);
        verifier("E pendant un lancement (vue inchangée, valide)", "pokenyle", "nylerp");
    }

    // ── actions ──────────────────────────────────────────────────────────────────────────────────

    private static void ouvrir(Account compte, String univers) throws Exception {
        fx(() -> {
            Settings.get().universVu = univers;
            if (coquille != null) coquille.liberer();
            coquille = new Coquille(1.0, compte, () -> { }, () -> { }, a -> { }, () -> { });
            monter(coquille);
            return null;
        });
    }

    /** Comme LauncherApp.nouvelleCoquille : la précédente rend ses vidéos, la nouvelle reprend l'univers retenu. */
    private static void changerDeCompte(Account compte, String universRetenu) throws Exception {
        fx(() -> {
            Settings.get().universVu = universRetenu;
            if (coquille != null) coquille.liberer();
            coquille = new Coquille(1.0, compte, () -> { }, () -> { }, a -> { }, () -> { });
            monter(coquille);
            return null;
        });
    }

    private static void monter(Coquille c) {
        var barre = chrome.getBarre().getChildren();
        barre.removeIf(n -> n.getProperties().containsKey("coquille"));
        chrome.setContenu(c);
        c.elementsGauche().getProperties().put("coquille", true);
        c.elementsDroite().getProperties().put("coquille", true);
        barre.add(0, c.elementsGauche());
        barre.add(barre.size() - 2, c.elementsDroite());
    }

    private static void naviguer(String cible) throws Exception {
        fx(() -> { coquille.naviguer(cible); return null; });
    }

    private static void attendre(long ms) throws InterruptedException { Thread.sleep(ms); }

    // ── vérifications ────────────────────────────────────────────────────────────────────────────

    /** Vérifie que la zone des univers contient UN univers valide, parmi ceux attendus. */
    private static void verifier(String nom, String... attendus) throws Exception {
        String probleme = fx(() -> diagnostiquer(attendus));
        if (probleme == null) System.out.println("OK   " + nom);
        else { System.out.println("FAIL " + nom + " : " + probleme); echecs.add(nom + " : " + probleme); capturer("echec-" + nom.split(" ")[0]); }
    }

    private static String diagnostiquer(String[] attendus) {
        List<Node> enfants = coquille.sceneUnivers().getChildren();
        if (enfants.isEmpty()) return "zone des univers VIDE";
        if (enfants.size() > 1) return enfants.size() + " enfants (transition jamais nettoyée)";
        Node n = enfants.get(0);
        String quel = n instanceof UniversPokenyle ? "pokenyle" : n instanceof UniversNylerp ? "nylerp" : "inconnu";
        if (java.util.Arrays.stream(attendus).noneMatch(quel::equals)) return "univers affiché = " + quel + ", attendu " + String.join("/", attendus);
        if (n.getOpacity() < 0.99) return "opacité " + n.getOpacity();
        if (Math.abs(n.getScaleX() - 1) > 0.001) return "échelle " + n.getScaleX();
        if (!n.isVisible()) return "nœud invisible";
        if (quel.equals("pokenyle")) {
            if (trouver(n, CielPokenyle.class) == null) return "pas de ciel";
            if (trouver(n, BoutonJouer.class) == null && trouver(n, CarteVerre.class) == null && trouver(n, BlocLancement.class) == null)
                return "ni bouton ni carte de lancement";
        } else if (trouver(n, BlocLancement.class) == null) return "pas de bloc de lancement";
        return null;
    }

    private static <T> T trouver(Node n, Class<T> type) {
        if (type.isInstance(n)) return type.cast(n);
        if (n instanceof Parent p) for (Node e : p.getChildrenUnmodifiable()) {
            T t = trouver(e, type);
            if (t != null) return t;
        }
        return null;
    }

    private static void capturer(String nom) {
        if (sortieCaptures == null) return;
        try {
            fx(() -> {
                SnapshotParameters sp = new SnapshotParameters();
                sp.setFill(Color.TRANSPARENT);
                WritableImage img = chrome.getCarte().snapshot(sp, null);
                try { ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", new File(sortieCaptures, nom + ".png")); }
                catch (Exception e) { e.printStackTrace(); }
                return null;
            });
            System.out.println("capture : " + nom);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static <T> T fx(Callable<T> c) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> { try { f.complete(c.call()); } catch (Throwable t) { f.completeExceptionally(t); } });
        return f.get(60, TimeUnit.SECONDS);
    }
}
