package fr.nylerp.launcher.ui.coquille;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.ui.kit.ChromeFenetre;
import fr.nylerp.launcher.ui.kit.Jetons;
import fr.nylerp.launcher.ui.kit.Polices;
import fr.nylerp.launcher.univers.PreTelechargeur;
import fr.nylerp.launcher.univers.Services;
import fr.nylerp.launcher.univers.SourceEtatPokenyle;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Banc de captures du lot L13 : l'écran Pokényle selon le compte. Un joueur qui n'est pas testeur n'a QUE « Me prévenir »
 * (jamais de pré-téléchargement, même si le service en simule un en cours) ; un testeur a « Jouer ».
 *
 * <pre>java -Duser.home=… -cp … fr.nylerp.launcher.ui.coquille.LancerApercuAcces DOSSIER</pre>
 */
public final class ApercuAcces extends Application {

    private static File sortie;
    private Stage stage;
    private ChromeFenetre chrome;
    private final List<Runnable> etapes = new ArrayList<>();

    public static void main(String[] args) {
        sortie = new File(args.length > 0 ? args[0] : "build/apercu-acces");
        sortie.mkdirs();
        launch(args);
    }

    /** État distant simulé : seuls les testeurs de la liste sont autorisés avant l'ouverture générale. */
    record Etat(boolean ouvert, Set<String> testeurs) implements SourceEtatPokenyle {
        public boolean ouvertPour(String p) { return ouvert || (p != null && testeurs.contains(p.toLowerCase())); }
        public Optional<ZonedDateTime> ouverture() { return Optional.of(ZonedDateTime.now().plusDays(58).plusHours(6)); }
        public String libelleOuverture() { return "mi-décembre"; }
        public boolean crackAutorise() { return true; }
        public String messageCrack() { return ""; }
        public boolean discordActif() { return true; }
        public String discordUrl() { return "https://discord.gg/nyle"; }
        public void surChangement(Runnable r) { }
    }

    /** Un pré-téléchargement « en cours » : l'écran d'un non-testeur ne doit PAS l'afficher. */
    record Pre() implements PreTelechargeur {
        public Etat etat(String u) { return PreTelechargeur.Etat.EN_COURS; }
        public double progression(String u) { return 0.42; }
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

        Account crack = new Account(Account.Type.OFFLINE, "L13Crack", "9e5b2be7-592c-3bbb-abcc-d347e774af9d", "0", null);
        Account premium = new Account(Account.Type.MICROSOFT, "Sacha", "9b3f3a0e-6a1d-4f7e-9d0a-7e0c4a1b2c3d", "x", null);
        Account testeur = new Account(Account.Type.MICROSOFT, "NYL3E", "1b2c3d4e-0000-4000-8000-000000000001", "x", null);

        etape(() -> pokenyle(crack, false), 3500, "01-non-testeur-crack");
        etape(() -> pokenyle(premium, false), 3500, "02-non-testeur-premium");
        etape(() -> pokenyle(testeur, false), 3500, "03-testeur-NYL3E");
        etape(() -> pokenyle(crack, true), 3500, "04-ouverture-generale-crack");
        etape(Platform::exit, 0, null);
        suivante();
    }

    private void pokenyle(Account compte, boolean ouvertAtous) {
        Services.etatPokenyle = new Etat(ouvertAtous, Set.of("nyl3e"));
        Services.preTelechargeur = new Pre();
        Settings.get().universVu = "pokenyle";
        Coquille c = new Coquille(1.0, compte, () -> { }, () -> { }, a -> { }, () -> { });
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
