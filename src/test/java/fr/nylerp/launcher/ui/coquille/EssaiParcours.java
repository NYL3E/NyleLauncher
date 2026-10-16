package fr.nylerp.launcher.ui.coquille;

import fr.nylerp.launcher.LauncherApp;
import fr.nylerp.launcher.auth.OfflineAuth;
import fr.nylerp.launcher.fond.Fond;
import fr.nylerp.launcher.univers.Services;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Essai d'intégration (lot L9) : la VRAIE application, dans un dossier personnel isolé (-Duser.home),
 * parcourue étape par étape avec une capture 1x et 2x de chaque écran.
 * Sortie : propriété {@code essai.sortie}. Étapes : connexion → compte crack → Accueil → choix d'univers
 * → Pokényle (pré-téléchargement lancé puis annulé) → boutique → réglages → NyleRP → Jouer (jeu réel)
 * → fermeture vers la barre → réouverture.
 */
public final class EssaiParcours extends LauncherApp {

    private static File sortie;
    private Stage stage;

    public static void main(String[] args) {
        sortie = new File(System.getProperty("essai.sortie", "essai-parcours"));
        sortie.mkdirs();
        if (!Fond.prendreLaMain(args)) return;
        Application.launch(EssaiParcours.class, args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        super.start(stage);
        Thread t = new Thread(this::scenario, "essai-parcours");
        t.setDaemon(true);
        t.start();
    }

    private void scenario() {
        try {
            dormir(5000);
            capturer("01-connexion");
            fx(() -> onAuthenticated(OfflineAuth.login("LTestL9")));
            dormir(2500);
            capturer("02-accueil-nouveau-launcher");
            fx(() -> {
                Node b = stage.getScene().getRoot().lookup("#accueil-valider");
                if (b == null) { journal("bouton « C'est parti » introuvable"); return; }
                b.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
            });
            dormir(2500);
            capturer("03-choix-univers");
            fx(() -> coquille().naviguer("pokenyle"));
            dormir(5000);
            capturer("04-pokenyle");
            journal("état Pokényle distant : ouvert pour LTestL9 = "
                    + Services.etatPokenyle.ouvertPour("LTestL9") + ", libellé = " + Services.etatPokenyle.libelleOuverture());
            journal("pré-téléchargement : " + Services.preTelechargeur.getClass().getSimpleName()
                    + " état " + Services.preTelechargeur.etat("pokenyle") + ", total " + Services.preTelechargeur.octetsTotal("pokenyle"));
            Services.preTelechargeur.demarrer("pokenyle");
            dormir(6000);
            journal("pré-téléchargement après 6 s : " + Services.preTelechargeur.etat("pokenyle")
                    + " " + Math.round(Services.preTelechargeur.progression("pokenyle") * 100) + " %");
            capturer("05-pokenyle-pretelechargement");
            Services.preTelechargeur.annuler("pokenyle");
            dormir(1500);
            journal("après annulation : " + Services.preTelechargeur.etat("pokenyle"));
            fx(() -> coquille().naviguer("boutique"));
            dormir(12000);
            capturer("06-boutique");
            fx(() -> coquille().retour());
            dormir(1500);
            fx(this::showSettings);
            dormir(2500);
            capturer("07-reglages");
            fx(() -> Fond.afficher("nylerp"));
            dormir(5000);
            capturer("08-nylerp");
            if (Boolean.getBoolean("essai.sansJeu")) { journal("jeu non lancé (essai.sansJeu)"); dormir(45000); journal("FIN sans jeu"); return; }
            fx(() -> coquille().lanceur().jouer());
            journal("clic sur Jouer (NyleRP)");
            dormir(45000);
            capturer("09-apres-jouer");
            fx(Fond::masquer);
            dormir(3000);
            journal("fenêtre visible après rangement : " + litFx(() -> stage.isShowing()));
            fx(() -> Fond.afficher(null));
            dormir(3000);
            journal("fenêtre visible après réouverture : " + litFx(() -> stage.isShowing()));
            capturer("10-reouverture");
            journal("FIN");
        } catch (Throwable e) {
            journal("ERREUR " + e);
            e.printStackTrace();
        }
    }

    private Coquille coquille() {
        return EssaiLancement.trouver(stage.getScene().getRoot());
    }

    private void capturer(String nom) {
        fx(() -> {
            for (int k = 1; k <= 2; k++) {
                SnapshotParameters sp = new SnapshotParameters();
                sp.setTransform(Transform.scale(k, k));
                sp.setFill(Color.TRANSPARENT);
                WritableImage img = stage.getScene().getRoot().snapshot(sp, null);
                try {
                    ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", new File(sortie, nom + "-" + k + "x.png"));
                } catch (Exception e) {
                    journal("capture " + nom + " impossible : " + e);
                }
            }
        });
        journal("capture " + nom);
    }

    private static void journal(String s) { System.out.println("PARCOURS : " + s); }

    private static void dormir(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) { } }

    private static void fx(Runnable r) {
        CountDownLatch l = new CountDownLatch(1);
        Platform.runLater(() -> { try { r.run(); } finally { l.countDown(); } });
        try { l.await(20, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
    }

    private static <T> T litFx(java.util.concurrent.Callable<T> c) {
        Object[] v = new Object[1];
        fx(() -> { try { v[0] = c.call(); } catch (Exception e) { v[0] = e; } });
        @SuppressWarnings("unchecked") T t = (T) v[0];
        return t;
    }
}
