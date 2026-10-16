package fr.nylerp.launcher.ui.reglages;

import fr.nylerp.launcher.ui.SettingsView;
import fr.nylerp.launcher.ui.kit.Interrupteur;
import fr.nylerp.launcher.ui.kit.Jetons;
import fr.nylerp.launcher.ui.kit.Polices;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Banc d'essai des paramètres, sans MainView ni LauncherApp (refonte 2026-10, lot L6).
 *
 * <pre>
 *   java -cp nylelauncher.jar fr.nylerp.launcher.ui.reglages.ApercuReglages                     # fenêtre 1180×700
 *   java -cp nylelauncher.jar fr.nylerp.launcher.ui.reglages.ApercuReglages --capture DOSSIER   # captures, sans fenêtre
 *   java -Duser.home=DOSSIER_ISOLE -Djpackage.app-path=…/NyleLauncher.app/Contents/MacOS/NyleLauncher \
 *        -cp nylelauncher.jar fr.nylerp.launcher.ui.reglages.ApercuReglages --test-demarrage
 * </pre>
 * {@code --capture} écrit la vue telle qu'à l'ouverture (fenêtre 1180×700, en 1x et 2x), la page
 * entière et la confirmation de « Tout supprimer ». {@code --test-demarrage} bascule l'interrupteur
 * « Lancer au démarrage » comme un clic et vérifie que l'entrée de démarrage est créée puis retirée :
 * à lancer UNIQUEMENT avec un {@code user.home} isolé. {@code --echelle E} : échelle du kit (défaut
 * 0,82, soit la fenêtre de 1180 px rapportée au site à 1440 px).
 */
public final class ApercuReglages {

    public static void main(String[] args) { Application.launch(App.class, args); }

    public static final class App extends Application {

        @Override
        public void start(Stage stage) {
            List<String> a = getParameters().getRaw();
            Path capture = null;
            boolean testDemarrage = a.contains("--test-demarrage");
            double echelle = 0.82;
            for (int i = 0; i < a.size() - 1; i++) {
                if ("--capture".equals(a.get(i))) capture = Path.of(a.get(i + 1));
                if ("--echelle".equals(a.get(i))) echelle = Double.parseDouble(a.get(i + 1));
            }
            Polices.charger();
            Jetons.setEchelle(echelle);
            SettingsView vue = new SettingsView(() -> System.out.println("retour demandé"));
            Scene scene = new Scene(vue, 1180, 700, Jetons.CIEL);
            // La scène réelle du launcher charge style.css : on la charge aussi pour vérifier qu'elle
            // ne déteint pas sur les paramètres.
            scene.getStylesheets().add(ApercuReglages.class.getResource("/css/style.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("Paramètres — refonte 2026-10");

            if (testDemarrage) { testerDemarrage(scene); return; }
            if (capture == null) { stage.show(); return; }

            final Path dossier = capture;
            Platform.runLater(() -> Platform.runLater(() -> {
                try {
                    Files.createDirectories(dossier);
                    Parent racine = scene.getRoot();
                    racine.applyCss();
                    racine.layout();
                    ecrire(racine, 1, dossier.resolve("reglages-1x.png"));
                    ecrire(racine, 2, dossier.resolve("reglages-2x.png"));

                    // page entière : on agrandit la scène à la hauteur du contenu, sans défilement
                    Node page = scene.lookup("#reglages-page");
                    double h = page.prefHeight(1180) + 120;
                    racine.resize(1180, h);
                    racine.applyCss();
                    racine.layout();
                    ecrire(racine, 1, dossier.resolve("reglages-page-1x.png"));
                    racine.resize(1180, 700);
                    racine.layout();

                    // confirmation de « Tout supprimer » (double clic sur le bouton armé)
                    Button des = (Button) scene.lookup("#reglages-desinstaller");
                    des.fire();
                    ecrire(racine, 1, dossier.resolve("reglages-desinstaller-arme-1x.png"));
                    des.fire();
                    racine.applyCss();
                    racine.layout();
                    ecrire(racine, 1, dossier.resolve("reglages-confirmation-1x.png"));
                    ecrire(racine, 2, dossier.resolve("reglages-confirmation-2x.png"));
                    System.out.println("captures écrites dans " + dossier.toAbsolutePath());
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    Platform.exit();
                }
            }));
        }

        private static void ecrire(Node n, double k, Path fichier) throws Exception {
            SnapshotParameters p = new SnapshotParameters();
            p.setFill(Color.TRANSPARENT);
            p.setTransform(Transform.scale(k, k));
            WritableImage img = n.snapshot(p, null);
            ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", fichier.toFile());
        }

        /** Bascule « Lancer au démarrage » comme un clic et vérifie l'entrée sur disque (macOS, Linux). */
        private static void testerDemarrage(Scene scene) {
            Path home = Path.of(System.getProperty("user.home"));
            Path reel = Path.of("/Users/" + System.getProperty("user.name"));
            if (home.equals(reel) || home.toString().equals(System.getenv("HOME"))) {
                System.out.println("REFUS : lancer ce test avec -Duser.home=<dossier isolé>, jamais le vrai dossier personnel");
                Platform.exit();
                System.exit(2);
                return;
            }
            Path agents = home.resolve("Library/LaunchAgents");
            Path autostart = home.resolve(".config/autostart");
            // le contenu d'un ScrollPane n'entre dans le graphe qu'avec son habillage : appliquer la CSS d'abord
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            Interrupteur inter = (Interrupteur) scene.lookup("#reglage-demarrageAuto");
            new Thread(() -> {
                boolean ok = true;
                try {
                    Platform.runLater(() -> { if (inter.isActif()) inter.setActif(false); });
                    ok &= attendre(() -> entrees(agents, autostart) == 0, "aucune entrée au départ");
                    Platform.runLater(() -> inter.setActif(true));
                    ok &= attendre(() -> entrees(agents, autostart) == 1, "interrupteur activé : entrée créée");
                    lister(agents, autostart);
                    boolean regle = fr.nylerp.launcher.config.Settings.get().demarrageAuto;
                    System.out.println((regle ? "OK" : "ÉCHEC") + " : réglage demarrageAuto=" + regle + " enregistré");
                    ok &= regle;
                    Platform.runLater(() -> inter.setActif(false));
                    ok &= attendre(() -> entrees(agents, autostart) == 0, "interrupteur coupé : entrée retirée");
                    boolean coupe = !fr.nylerp.launcher.config.Settings.get().demarrageAuto;
                    System.out.println((coupe ? "OK" : "ÉCHEC") + " : réglage demarrageAuto=false enregistré");
                    ok &= coupe;
                } catch (Exception e) {
                    e.printStackTrace();
                    ok = false;
                }
                System.out.println(ok ? "TEST DÉMARRAGE : RÉUSSI" : "TEST DÉMARRAGE : ÉCHEC");
                boolean fin = ok;
                Platform.runLater(() -> { Platform.exit(); System.exit(fin ? 0 : 1); });
            }, "test-demarrage").start();
        }

        private static long entrees(Path... dossiers) {
            long n = 0;
            for (Path d : dossiers) {
                if (!Files.isDirectory(d)) continue;
                try (Stream<Path> s = Files.list(d)) {
                    n += s.filter(p -> p.getFileName().toString().startsWith("fr.nylerp.launcher")
                            || p.getFileName().toString().startsWith("nylelauncher")).count();
                } catch (Exception e) { return -1; }
            }
            return n;
        }

        private static void lister(Path... dossiers) {
            for (Path d : dossiers) {
                if (!Files.isDirectory(d)) continue;
                try (Stream<Path> s = Files.list(d)) {
                    s.forEach(p -> System.out.println("   entrée : " + p));
                } catch (Exception ignored) { }
            }
        }

        private static boolean attendre(java.util.function.BooleanSupplier condition, String etape) throws InterruptedException {
            for (int i = 0; i < 50; i++) {
                if (condition.getAsBoolean()) { System.out.println("OK : " + etape); return true; }
                Thread.sleep(100);
            }
            System.out.println("ÉCHEC : " + etape);
            return false;
        }
    }

    private ApercuReglages() {}
}
