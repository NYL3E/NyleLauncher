package fr.nylerp.launcher.ui.coquille;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.ui.kit.ChromeFenetre;
import fr.nylerp.launcher.ui.kit.Jetons;
import fr.nylerp.launcher.ui.kit.Polices;
import fr.nylerp.launcher.ui.univers.CielPokenyle;
import fr.nylerp.launcher.ui.univers.UniversPokenyle;
import fr.nylerp.launcher.univers.PreTelechargeur;
import fr.nylerp.launcher.univers.Services;
import fr.nylerp.launcher.univers.SourceEtatPokenyle;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Banc de captures de la coquille (lot L5) : construit la vraie coquille dans une vraie fenêtre du kit,
 * passe par chaque écran et chaque état, et enregistre des PNG en 1x et en 2x.
 *
 * <pre>java -Duser.home=… -Dnyle.medias.manifest=… -cp … fr.nylerp.launcher.ui.coquille.ApercuCoquille DOSSIER</pre>
 * Les états de Pokényle sont simulés en remplaçant {@link Services} (jamais d'implémentation réelle ici).
 */
public final class ApercuCoquille extends Application {

    private static File sortie;
    private Stage stage;
    private ChromeFenetre chrome;
    private final List<Runnable> etapes = new ArrayList<>();

    public static void main(String[] args) {
        sortie = new File(args.length > 0 ? args[0] : "build/apercu-coquille");
        sortie.mkdirs();
        launch(args);
    }

    /** Une source d'état simulée. */
    record Etat(boolean ouvert, Optional<ZonedDateTime> date, boolean crack, boolean discord) implements SourceEtatPokenyle {
        public boolean ouvertPour(String p) { return ouvert; }
        public Optional<ZonedDateTime> ouverture() { return date; }
        public String libelleOuverture() { return "mi-décembre"; }
        public boolean crackAutorise() { return crack; }
        public String messageCrack() { return "Pokényle demande pour l'instant un compte Minecraft acheté. Les comptes crack arrivent à l'ouverture."; }
        public boolean discordActif() { return discord; }
        public String discordUrl() { return "https://discord.gg/nyle"; }
        public void surChangement(Runnable r) { }
    }

    /** Un pré-téléchargement simulé figé à un état. */
    record Pre(PreTelechargeur.Etat etat, double p) implements PreTelechargeur {
        public Etat etat(String u) { return etat; }
        public double progression(String u) { return p; }
        public long octetsTotal(String u) { return 1_000_000_000L; }
        public void demarrer(String u) { }
        public void annuler(String u) { }
        public void surChangement(Runnable r) { }
    }

    @Override
    public void start(Stage s) {
        stage = s;
        Polices.charger();
        Jetons.setEchelle(20.6136 / 19.2);
        chrome = ChromeFenetre.installer(stage, null, 1180, 700);
        var css = getClass().getResource("/css/style.css");
        if (css != null) stage.getScene().getStylesheets().add(0, css.toExternalForm());
        stage.show();

        Account premium = new Account(Account.Type.MICROSOFT, "Sacha", "9b3f3a0e-6a1d-4f7e-9d0a-7e0c4a1b2c3d", "x", null);
        Account crack = new Account(Account.Type.OFFLINE, "L5Test", "9e5b2be7-592c-3bbb-abcc-d347e774af9d", "0", null);

        // 1. choix d'univers (premier lancement)
        etape(() -> { Settings.get().universVu = ""; montrer(new Coquille(1.0, premium, () -> {}, () -> {}, a -> {}, () -> {})); }, 3500, "01-choix");
        // 2. Pokényle pas encore ouvert, sans date, sans pré-téléchargement (valeurs par défaut)
        etape(() -> pokenyle(premium, new Etat(false, Optional.empty(), false, false), PreTelechargeur.AUCUN), 4000, "02-pokenyle-attente");
        // 3. Pokényle avec date, pré-téléchargement à 42 %, Discord actif
        etape(() -> pokenyle(premium, new Etat(false, Optional.of(ZonedDateTime.now().plusDays(58).plusHours(6)), false, true),
                new Pre(PreTelechargeur.Etat.EN_COURS, 0.42)), 3000, "03-pokenyle-compte-a-rebours");
        // 4. Pokényle ouvert : « Jouer »
        etape(() -> pokenyle(premium, new Etat(true, Optional.empty(), false, false), PreTelechargeur.AUCUN), 3000, "04-pokenyle-ouvert");
        // 5. compte crack refusé
        etape(() -> pokenyle(crack, new Etat(false, Optional.empty(), false, true), PreTelechargeur.AUCUN), 3000, "05-pokenyle-crack");
        // 6. NyleRP
        etape(() -> {
            Services.etatPokenyle = SourceEtatPokenyle.PAR_DEFAUT;
            Services.preTelechargeur = PreTelechargeur.AUCUN;
            Settings.get().universVu = "nylerp";
            montrer(new Coquille(1.0, crack, () -> {}, () -> {}, a -> {}, () -> {}));
        }, 5000, "06-nylerp");
        // 7. vue de navigation (boutique simulée) avec « Retour »
        etape(() -> {
            Coquille c = (Coquille) chrome.getCarte().getChildren().get(0);
            Text t = new Text("Boutique (vue de démonstration)");
            t.setFont(Polices.titre(32));
            StackPane v = new StackPane(t);
            v.setStyle("-fx-background-color: #eaf7ff;");
            c.afficherVue(v);
        }, 1500, "07-vue-navigation");
        // 8. séquence du ciel (temps forcés) : Ho-Oh passe entre 8 s et 53 s
        etape(() -> pokenyle(premium, new Etat(false, Optional.empty(), false, false), PreTelechargeur.AUCUN), 3500, null);
        for (double t : new double[]{9, 16, 24, 32, 40}) {
            final double tt = t;
            etape(() -> {
                Coquille c = (Coquille) chrome.getCarte().getChildren().get(0);
                CielPokenyle ciel = trouverCiel(c);
                if (ciel != null) { ciel.arreter(); ciel.dessinerA(tt); }
            }, 400, String.format("08-ciel-t%02d", (int) t));
        }
        // 9. mesure des images/s en animation réelle
        etape(() -> pokenyle(premium, new Etat(false, Optional.empty(), false, false), PreTelechargeur.AUCUN), 6000, null);
        etape(() -> {
            Coquille c = (Coquille) chrome.getCarte().getChildren().get(0);
            CielPokenyle ciel = trouverCiel(c);
            System.out.println("IPS mesurées : " + (ciel == null ? "?" : ciel.ipsMesurees()) + " · repli : " + (ciel != null && ciel.repliProperty().get()));
        }, 200, null);
        etape(Platform::exit, 0, null);
        suivante();
    }

    private void pokenyle(Account compte, SourceEtatPokenyle etat, PreTelechargeur pre) {
        Services.etatPokenyle = etat;
        Services.preTelechargeur = pre;
        Settings.get().universVu = "pokenyle";
        montrer(new Coquille(1.0, compte, () -> {}, () -> {}, a -> {}, () -> {}));
    }

    private static CielPokenyle trouverCiel(Node n) {
        if (n instanceof CielPokenyle c) return c;
        if (n instanceof javafx.scene.Parent p) for (Node e : p.getChildrenUnmodifiable()) {
            CielPokenyle c = trouverCiel(e);
            if (c != null) return c;
        }
        return null;
    }

    private void montrer(Coquille c) {
        var barre = chrome.getBarre().getChildren();
        if (!chrome.getCarte().getChildren().isEmpty() && chrome.getCarte().getChildren().get(0) instanceof Coquille ancienne) ancienne.liberer();
        barre.removeIf(n -> n.getProperties().containsKey("coquille"));
        chrome.setContenu(c);
        c.elementsGauche().getProperties().put("coquille", true);
        c.elementsDroite().getProperties().put("coquille", true);
        barre.add(0, c.elementsGauche());
        barre.add(barre.size() - 2, c.elementsDroite());
    }

    private void etape(Runnable action, long attenteMs, String capture) {
        etapes.add(() -> {
            try { action.run(); } catch (Throwable t) { t.printStackTrace(); }
            new Thread(() -> {
                try { Thread.sleep(attenteMs); } catch (InterruptedException ignored) { }
                Platform.runLater(() -> {
                    if (capture != null) capturer(capture);
                    suivante();
                });
            }).start();
        });
    }

    private void suivante() {
        if (etapes.isEmpty()) return;
        etapes.remove(0).run();
    }

    private void capturer(String nom) {
        Node racine = chrome.getCarte();
        for (int k = 1; k <= 2; k++) {
            SnapshotParameters sp = new SnapshotParameters();
            sp.setFill(Color.TRANSPARENT);
            sp.setTransform(Transform.scale(k, k));
            WritableImage img = racine.snapshot(sp, null);
            try {
                ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", new File(sortie, nom + "-" + k + "x.png"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println("capture : " + nom);
    }
}
