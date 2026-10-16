package fr.nylerp.launcher.ui.boutique;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Outil d'essai (comme la vitrine du kit) : affiche la boutique dans une fenêtre 1180×700 et, avec
 * {@code --capture DOSSIER}, écrit boutique-1x.png / boutique-2x.png puis se ferme.
 * {@code --paiement URL} ouvre en plus la fenêtre de paiement sur cette adresse et la capture (paiement.png).
 *
 *   java -cp nylelauncher.jar 'fr.nylerp.launcher.ui.boutique.ApercuBoutique$Lanceur' --capture DOSSIER [--univers nylerp]
 *        [--pseudo X] [--defiler PIXELS] [--paiement URL]
 */
public final class ApercuBoutique extends Application {

    public static void main(String[] args) { launch(args); }

    /** Point d'entrée hors Application (sinon le lanceur java exige JavaFX sur le module path). */
    public static final class Lanceur {
        public static void main(String[] args) { Application.launch(ApercuBoutique.class, args); }
    }

    @Override
    public void start(Stage stage) throws Exception {
        List<String> a = getParameters().getRaw();
        String capture = val(a, "--capture"), univers = val(a, "--univers"), pseudo = val(a, "--pseudo");
        String paiement = val(a, "--paiement"), defiler = val(a, "--defiler");
        BoutiqueView vue = Boutique.ouvrir(univers == null ? "pokenyle" : univers, pseudo == null ? "NYL3E" : pseudo);
        vue.setSurRetour(Platform::exit);
        stage.setTitle("Aperçu boutique");
        stage.setScene(new Scene(vue, 1180, 700));
        stage.show();
        if (capture == null) return;
        Path dossier = Path.of(capture);
        Files.createDirectories(dossier);
        javafx.scene.web.WebView wv = (javafx.scene.web.WebView) ((javafx.scene.layout.StackPane) vue.getCenter()).getChildren().get(0);
        wv.getEngine().getLoadWorker().stateProperty().addListener((o, x, s) -> {
            if (s != Worker.State.SUCCEEDED) return;
            apres(5, () -> {
                if (defiler != null) wv.getEngine().executeScript("window.scrollTo(0," + defiler + ")");
                apres(1.5, () -> {
                    ecrire(vue, 1, dossier.resolve("boutique-1x.png"));
                    ecrire(vue, 2, dossier.resolve("boutique-2x.png"));
                    System.out.println("agent=" + wv.getEngine().getUserAgent());
                    System.out.println("titre=" + wv.getEngine().getTitle() + " adresse=" + wv.getEngine().getLocation());
                    if (paiement == null) { Platform.exit(); return; }
                    FenetrePaiement f = FenetrePaiement.ouvrir(stage, adr -> System.out.println("retour=" + adr));
                    f.moteur().load(paiement);
                    f.moteur().getLoadWorker().stateProperty().addListener((o2, x2, s2) -> {
                        if (s2 == Worker.State.SUCCEEDED || s2 == Worker.State.FAILED) {
                            apres(6, () -> {
                                ecrire(f.moteur().getLoadWorker() == null ? null : sceneRacine(f), 1, dossier.resolve("paiement.png"));
                                System.out.println("paiement=" + s2 + " adresse=" + f.moteur().getLocation()
                                        + " titre=" + f.moteur().getTitle());
                                Platform.exit();
                            });
                        }
                    });
                });
            });
        });
    }

    private static Node sceneRacine(FenetrePaiement f) {
        for (javafx.stage.Window w : javafx.stage.Window.getWindows()) {
            if (w instanceof Stage s && "Paiement sécurisé Tebex".equals(s.getTitle())) return s.getScene().getRoot();
        }
        return null;
    }

    private static void apres(double s, Runnable r) {
        PauseTransition p = new PauseTransition(Duration.seconds(s));
        p.setOnFinished(e -> r.run());
        p.play();
    }

    private static void ecrire(Node n, double echelle, Path fichier) {
        if (n == null) return;
        try {
            SnapshotParameters p = new SnapshotParameters();
            p.setTransform(Transform.scale(echelle, echelle));
            WritableImage img = n.snapshot(p, null);
            ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", fichier.toFile());
        } catch (Exception e) {
            System.err.println("capture impossible : " + e);
        }
    }

    private static String val(List<String> a, String cle) {
        int i = a.indexOf(cle);
        return i >= 0 && i + 1 < a.size() ? a.get(i + 1) : null;
    }
}
