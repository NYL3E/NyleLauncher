package fr.nylerp.launcher.ui.compte;

import fr.nylerp.launcher.LauncherApp;
import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.ui.Accueil;
import fr.nylerp.launcher.ui.LoginView;
import fr.nylerp.launcher.ui.kit.Etat;
import fr.nylerp.launcher.ui.kit.Jetons;
import fr.nylerp.launcher.ui.kit.Polices;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Banc d'essai des écrans de compte (lot L4), comme la {@code Vitrine} du kit. Trois modes :
 * <pre>
 *   java -cp jar fr.nylerp.launcher.ui.compte.ApercuCompte --capture DOSSIER [--taille 1180x700] [--echelle 0.82]
 *   java -Duser.home=/tmp/iso -cp jar fr.nylerp.launcher.ui.compte.ApercuCompte --parcours-crack DOSSIER
 *   java -Duser.home=/tmp/iso -cp jar fr.nylerp.launcher.ui.compte.ApercuCompte --parcours-ms DOSSIER
 * </pre>
 * {@code --capture} : connexion (repos, survol de chaque carte), étape crack (vide, pseudo invalide,
 * pseudo d'un compte acheté), mode « Ajouter un compte », Accueil, puis la connexion Microsoft
 * réelle (fenêtre ouverte, capturée, refermée sans aller au bout), en 1x et 2x.
 * {@code --parcours-crack} : le VRAI {@link LauncherApp} dans un dossier personnel isolé, compte crack
 * saisi à l'écran → doit arriver sur MainView. {@code --parcours-ms} : un compte Microsoft déjà
 * enregistré (sans jeton de rafraîchissement) → MainView directement, sans écran de connexion.
 */
public final class ApercuCompte {

    public static void main(String[] args) { Application.launch(App.class, args); }

    public static final class App extends Application {

        private Path dossier;
        private final Deque<Runnable> etapes = new ArrayDeque<>();

        @Override
        public void start(Stage stage) throws Exception {
            List<String> a = getParameters().getRaw();
            String mode = a.isEmpty() ? "--capture" : a.get(0);
            dossier = Path.of(a.size() > 1 ? a.get(1) : "captures-compte");
            Files.createDirectories(dossier);
            double l = 1180, h = 700, e = 0.82;
            for (int i = 0; i < a.size() - 1; i++) {
                if ("--taille".equals(a.get(i))) { String[] t = a.get(i + 1).split("x"); l = Double.parseDouble(t[0]); h = Double.parseDouble(t[1]); }
                if ("--echelle".equals(a.get(i))) e = Double.parseDouble(a.get(i + 1));
            }
            System.setProperty("nyleauth.installedVersion", System.getProperty("nyleauth.installedVersion", "0.3.23"));
            switch (mode) {
                case "--parcours-crack" -> parcoursCrack(stage);
                case "--parcours-ms" -> parcoursMs(stage);
                default -> capture(stage, l, h, e);
            }
        }

        // ── captures ──────────────────────────────────────────────────────────────────────────────
        private void capture(Stage stage, double l, double h, double e) {
            Polices.charger();
            Jetons.setEchelle(e);
            String suffixe = (int) l + "x" + (int) h;
            LoginView login = new LoginView(acc -> System.out.println("authentifié : " + acc.username() + " (" + acc.type() + ")"));
            Scene scene = new Scene(login, l, h, Color.web("#08080B"));
            stage.setScene(scene);
            stage.setTitle("Banc compte L4");
            stage.show();

            CarteChoix ms = (CarteChoix) login.lookup("#carte-microsoft");
            CarteChoix crack = (CarteChoix) login.lookup("#carte-crack");
            plus(1400, () -> ecrire(login, "connexion-" + suffixe));
            plus(100, () -> ms.apercu(Etat.SURVOL));
            plus(400, () -> ecrire(login, "connexion-survol-microsoft-" + suffixe));
            plus(100, () -> { ms.apercu(Etat.NORMAL); crack.apercu(Etat.SURVOL); });
            plus(400, () -> ecrire(login, "connexion-survol-crack-" + suffixe));
            plus(100, () -> { crack.apercu(Etat.NORMAL); crack.choisir(); });
            plus(700, () -> ecrire(login, "crack-vide-" + suffixe));
            plus(100, () -> { ((TextField) login.lookup("#champ-pseudo")).setText("ab"); ((Button) login.lookup("#valider-crack")).fire(); });
            plus(500, () -> ecrire(login, "crack-pseudo-invalide-" + suffixe));
            plus(100, () -> { ((TextField) login.lookup("#champ-pseudo")).setText("Notch"); ((Button) login.lookup("#valider-crack")).fire(); });
            plus(4500, () -> ecrire(login, "crack-pseudo-achete-" + suffixe));
            plus(100, () -> {
                ((TextField) login.lookup("#champ-pseudo")).setText("Steve_Nyle42");
                LoginView ajout = new LoginView(acc -> {}, () -> System.out.println("retour"));
                scene.setRoot(ajout);
            });
            plus(1400, () -> ecrire(scene.getRoot(), "ajout-compte-" + suffixe));
            plus(100, () -> {
                Settings s = new Settings();
                scene.setRoot(new Accueil(s) {{ setSurValider(x -> System.out.println("accueil validé")); }});
            });
            plus(1400, () -> ecrire(scene.getRoot(), "accueil-" + suffixe));
            // connexion Microsoft réelle : on ouvre, on capture l'écran et la fenêtre Microsoft, on referme
            plus(100, () -> {
                LoginView l2 = new LoginView(acc -> System.out.println("MS ok " + acc.username()));
                scene.setRoot(l2);
            });
            plus(1300, () -> ((CarteChoix) scene.getRoot().lookup("#carte-microsoft")).choisir());
            plus(9000, () -> {
                ecrire(scene.getRoot(), "microsoft-en-cours-" + suffixe);
                for (Window w : List.copyOf(Window.getWindows())) {
                    if (w != stage && w.getScene() != null) {
                        ecrire(w.getScene().getRoot(), "microsoft-fenetre-" + suffixe);
                        System.out.println("fenêtre Microsoft : " + (w instanceof Stage s2 ? s2.getTitle() : w) + " "
                                + (int) w.getWidth() + "x" + (int) w.getHeight());
                    }
                }
                // la fenêtre Microsoft d'OpenAuth est une JFrame Swing (JFXPanel) : on la ferme comme le
                // ferait le joueur (événement de fermeture), pour vérifier le chemin « annulation »
                java.awt.EventQueue.invokeLater(() -> {
                    for (java.awt.Window w : java.awt.Window.getWindows()) {
                        if (w.isShowing()) {
                            System.out.println("fermeture de la fenêtre Swing : " + w.getClass().getSimpleName());
                            w.dispatchEvent(new java.awt.event.WindowEvent(w, java.awt.event.WindowEvent.WINDOW_CLOSING));
                        }
                    }
                });
            });
            plus(3500, () -> {
                ecrire(scene.getRoot(), "microsoft-apres-fermeture-" + suffixe);
                CarteChoix c = (CarteChoix) scene.getRoot().lookup("#carte-microsoft");
                long autres = Window.getWindows().stream().filter(w -> w != stage && w.isShowing()).count();
                System.out.println(c.isOccupe() ? "ÉCHEC : la carte Microsoft reste occupée après fermeture"
                        : autres > 0 ? "ÉCHEC : une fenêtre (dialogue d'erreur ?) s'est ouverte après la fermeture : " + autres
                        : "OK : fermeture de la fenêtre Microsoft = annulation silencieuse, carte rendue, aucun dialogue");
                for (Window w : List.copyOf(Window.getWindows()))
                    if (w != stage && w.isShowing() && w.getScene() != null) ecrire(w.getScene().getRoot(), "microsoft-dialogue-" + suffixe);
            });
            plus(200, () -> { System.out.println("captures écrites dans " + dossier.toAbsolutePath()); Platform.exit(); System.exit(0); });
            suivante();
        }

        // ── parcours réels ────────────────────────────────────────────────────────────────────────
        private void parcoursCrack(Stage stage) throws Exception {
            System.out.println("dossier personnel : " + System.getProperty("user.home"));
            LauncherApp app = new LauncherApp();
            app.start(stage);
            Scene scene = stage.getScene();
            String depart = scene.getRoot().getClass().getSimpleName();
            System.out.println("écran de départ : " + depart);
            if (!(scene.getRoot() instanceof LoginView)) { System.out.println("ÉCHEC : pas d'écran de connexion au départ"); quitter(1); return; }
            plus(1500, () -> ecrire(scene.getRoot(), "parcours-1-connexion"));
            plus(100, () -> ((CarteChoix) scene.getRoot().lookup("#carte-crack")).choisir());
            plus(700, () -> ((TextField) scene.getRoot().lookup("#champ-pseudo")).setText("TesteurRefonteL4"));
            plus(300, () -> ecrire(scene.getRoot(), "parcours-2-pseudo"));
            plus(100, () -> ((Button) scene.getRoot().lookup("#valider-crack")).fire());
            attendre(scene, "MainView", 20000, "parcours-3-mainview");
            suivante();
        }

        private void parcoursMs(Stage stage) throws Exception {
            Path etat = Path.of(System.getProperty("user.home"), "Library", "Application Support", "NyleLauncher", "state");
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) etat = Path.of(System.getenv("APPDATA"), "NyleLauncher", "state");
            Files.createDirectories(etat);
            Files.writeString(etat.resolve("accounts.json"), """
                    {"schema":1,"accounts":[{"type":"MICROSOFT","username":"CompteMsEssai","uuid":"0f3a6c2e-1111-4222-8333-944455556666","accessToken":"jeton-factice","refreshToken":null}],"activeIndex":0}
                    """);
            LauncherApp app = new LauncherApp();
            app.start(stage);
            Scene scene = stage.getScene();
            String depart = scene.getRoot().getClass().getSimpleName();
            System.out.println("écran de départ : " + depart);
            plus(2500, () -> {
                ecrire(scene.getRoot(), "parcours-ms-depart");
                String r = scene.getRoot().getClass().getSimpleName();
                System.out.println(r.equals("MainView") && depart.equals("MainView")
                        ? "OK : compte Microsoft enregistré → MainView directement, sans écran de connexion"
                        : "ÉCHEC : écran " + depart + " puis " + r);
                quitter(r.equals("MainView") && depart.equals("MainView") ? 0 : 1);
            });
            suivante();
        }

        private void attendre(Scene scene, String classe, long delaiMs, String capture) {
            long fin = System.currentTimeMillis() + delaiMs;
            Runnable[] sonde = new Runnable[1];
            sonde[0] = () -> {
                String r = scene.getRoot().getClass().getSimpleName();
                if (r.equals(classe)) {
                    plus(2500, () -> {
                        ecrire(scene.getRoot(), capture);
                        System.out.println("OK : arrivé sur " + classe);
                        quitter(0);
                    });
                    suivante();
                } else if (System.currentTimeMillis() > fin) {
                    System.out.println("ÉCHEC : toujours sur " + r + " après " + delaiMs + " ms");
                    ecrire(scene.getRoot(), capture + "-echec");
                    quitter(1);
                } else {
                    PauseTransition p = new PauseTransition(Duration.millis(250));
                    p.setOnFinished(x -> sonde[0].run());
                    p.play();
                }
            };
            plus(250, sonde[0]);
        }

        // ── outils ────────────────────────────────────────────────────────────────────────────────
        private void plus(long ms, Runnable r) {
            etapes.add(() -> {
                PauseTransition p = new PauseTransition(Duration.millis(ms));
                p.setOnFinished(x -> {
                    try { r.run(); } catch (Exception ex) { ex.printStackTrace(); }
                    suivante();
                });
                p.play();
            });
        }

        private void suivante() {
            Runnable r = etapes.poll();
            if (r != null) r.run();
        }

        private void ecrire(Node n, String nom) {
            try {
                if (n instanceof Parent p) { p.applyCss(); p.layout(); }
                for (double k : new double[]{1, 2}) {
                    SnapshotParameters sp = new SnapshotParameters();
                    sp.setFill(Color.TRANSPARENT);
                    sp.setTransform(Transform.scale(k, k));
                    WritableImage img = n.snapshot(sp, null);
                    ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png",
                            dossier.resolve(nom + "-" + (int) k + "x.png").toFile());
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        private void quitter(int code) {
            Platform.exit();
            System.exit(code);
        }
    }

    private ApercuCompte() {}
}
