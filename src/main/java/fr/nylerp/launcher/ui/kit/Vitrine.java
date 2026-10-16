package fr.nylerp.launcher.ui.kit;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Text;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Vitrine du kit : tous les composants dans tous leurs états (normal, survol, pressé, désactivé), à la taille du site à
 * 1440 px (échelle 1), pour la comparaison côte à côte avec les captures du site.
 *
 * <pre>
 *   java -cp nylelauncher.jar fr.nylerp.launcher.ui.kit.Vitrine                       # fenêtre
 *   java -cp nylelauncher.jar fr.nylerp.launcher.ui.kit.Vitrine --capture DOSSIER [--fond IMAGE.jpg]
 * </pre>
 * {@code --capture} écrit vitrine-1x.png, vitrine-2x.png et fenetre-1x.png / fenetre-2x.png sans ouvrir de fenêtre.
 * {@code --fond} : une image (PNG/JPEG) du ciel du site derrière le haut de la vitrine, pour juger le verre et les halos.
 */
public final class Vitrine {

    public static void main(String[] args) { Application.launch(App.class, args); }

    public static final class App extends Application {

        @Override
        public void start(Stage stage) throws Exception {
            List<String> a = getParameters().getRaw();
            Path capture = null, fond = null;
            for (int i = 0; i < a.size() - 1; i++) {
                if ("--capture".equals(a.get(i))) capture = Path.of(a.get(i + 1));
                if ("--fond".equals(a.get(i))) fond = Path.of(a.get(i + 1));
            }
            Polices.charger();
            Jetons.setEchelle(1.0);
            Parent racine = construire(fond);
            Scene scene = new Scene(racine, 1440, 1290, Jetons.CIEL);
            scene.getStylesheets().add(Vitrine.class.getResource("/css/kit.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("Vitrine du kit — refonte 2026-10");

            // la fenêtre sans bordure, dans un second stage jamais affiché en mode capture
            Stage s2 = new Stage();
            ChromeFenetre chrome = ChromeFenetre.installer(s2, ecranExemple(), 640, 360);
            chrome.getBarre().getChildren().add(0, new BoutonCompte("NYL3E"));

            if (capture == null) {
                stage.show();
                s2.show();
                return;
            }
            Files.createDirectories(capture);
            final Path dossier = capture;
            // deux impulsions pour que la CSS, la mise en page et les effets soient posés
            Platform.runLater(() -> Platform.runLater(() -> {
                try {
                    racine.applyCss();
                    racine.layout();
                    ecrire(racine, 1, dossier.resolve("vitrine-1x.png"));
                    ecrire(racine, 2, dossier.resolve("vitrine-2x.png"));
                    Parent r2 = s2.getScene().getRoot();
                    r2.applyCss();
                    r2.layout();
                    ecrire(r2, 1, dossier.resolve("fenetre-1x.png"));
                    ecrire(r2, 2, dossier.resolve("fenetre-2x.png"));
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
    }

    // ── contenu ───────────────────────────────────────────────────────────────────────────────────────
    static Parent construire(Path fond) {
        VBox colonne = new VBox(Jetons.rem(1.4));
        colonne.setPadding(new Insets(Jetons.rem(1.6), Jetons.rem(2.2), Jetons.rem(2), Jetons.rem(2.2)));

        // 1. boutons principaux, sur le ciel du site
        VBox ciel = new VBox(Jetons.rem(0.9));
        ciel.setAlignment(Pos.TOP_LEFT);
        ciel.getChildren().add(legende("BoutonJouer (le .lanceur-principal du héros) — normal avec filigrane et particules · survol · pressé · désactivé"));
        HBox jouer = rangee();
        BoutonJouer j0 = new BoutonJouer("Installer le launcher");
        j0.setFiligrane("POKÉNYLE");
        j0.setParticules(true);
        jouer.getChildren().addAll(etiquette(j0, "normal"), etats(() -> new BoutonJouer("Jouer"), Etat.SURVOL, Etat.PRESSE, Etat.DESACTIVE));
        ciel.getChildren().add(jouer);
        HBox discord = rangee();
        discord.getChildren().add(etats(() -> new BoutonJouer("Rejoindre le Discord", BoutonJouer.Style.DISCORD), Etat.NORMAL, Etat.SURVOL, Etat.PRESSE));
        ciel.getChildren().add(discord);
        ciel.getChildren().add(legende("BoutonPilule (.bouton-entete) et BoutonCompte (.compte-entete) — normal · survol · pressé · désactivé"));
        HBox entete = rangee();
        entete.getChildren().addAll(
                etats(() -> new BoutonPilule("Installer le launcher", BoutonPilule.Variante.LAUNCHER), Etat.NORMAL, Etat.SURVOL, Etat.PRESSE, Etat.DESACTIVE));
        ciel.getChildren().add(entete);
        HBox entete2 = rangee();
        entete2.getChildren().addAll(
                etats(() -> new BoutonPilule("Rejoindre le Discord", BoutonPilule.Variante.DISCORD), Etat.NORMAL, Etat.SURVOL),
                etats(() -> new BoutonCompte(null), Etat.NORMAL, Etat.SURVOL),
                etats(() -> new BoutonCompte("NYL3E"), Etat.NORMAL));
        ciel.getChildren().add(entete2);

        ciel.getChildren().add(legende("Titres Northura Black SemExp (style Aniimo) — principal · secondaire · secondaire avec éclats du site"));
        HBox titres = rangee();
        titres.setAlignment(Pos.CENTER_LEFT);
        titres.getChildren().addAll(new TitreEclats("Disponible dès maintenant", TitreEclats.Style.PRINCIPAL, Jetons.rem(2.4), null),
                new TitreEclats("Pokényle", TitreEclats.Style.PRINCIPAL));
        ciel.getChildren().add(titres);
        HBox titres2 = rangee();
        titres2.setAlignment(Pos.CENTER_LEFT);
        titres2.getChildren().addAll(new TitreEclats("Calendrier des événements", TitreEclats.Style.SECONDAIRE),
                new TitreEclats("Choisis ton univers", TitreEclats.Style.SECONDAIRE, Jetons.rem(1.75), "univers"));
        ciel.getChildren().add(titres2);

        StackPane bandeau = new StackPane(ciel);
        bandeau.setPadding(new Insets(Jetons.rem(1.2)));
        if (fond != null && Files.isRegularFile(fond)) {
            ImageView iv = new ImageView(new Image(fond.toUri().toString()));
            iv.setPreserveRatio(false);
            iv.fitWidthProperty().bind(bandeau.widthProperty());
            iv.fitHeightProperty().bind(bandeau.heightProperty());
            iv.setManaged(false);
            bandeau.getChildren().add(0, iv);
            bandeau.setBackground(Background.EMPTY);
        } else {
            bandeau.setBackground(new Background(new BackgroundFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#7cc0f2")), new Stop(1, Color.web("#cfeafc"))), null, null)));
        }
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(bandeau.widthProperty());
        clip.heightProperty().bind(bandeau.heightProperty());
        clip.setArcWidth(Jetons.rem(1.6));
        clip.setArcHeight(Jetons.rem(1.6));
        bandeau.setClip(clip);
        colonne.getChildren().add(bandeau);

        // 2. contrôles sur fond clair, dans une carte de verre
        VBox controles = new VBox(Jetons.rem(0.9));
        controles.getChildren().add(legende("Segment (onglets du site, pastille qui glisse) — normal · survol d'une option · désactivé"));
        HBox seg = rangee();
        seg.getChildren().add(etats(() -> new Segment("Pokényle", "NyleRP"), Etat.NORMAL, Etat.SURVOL, Etat.DESACTIVE));
        Segment s3 = new Segment("Tout", "Grades", "Gemmes");
        s3.setChoix(1);
        seg.getChildren().add(etiquette(s3, "trois options"));
        controles.getChildren().add(seg);
        controles.getChildren().add(legende("Interrupteur — coupé · actif · survol · désactivé"));
        HBox inter = rangee();
        inter.getChildren().addAll(etiquette(new Interrupteur(false), "coupé"), etiquette(new Interrupteur(true), "actif"),
                etiquette(apercu(new Interrupteur(false), Etat.SURVOL), "survol"), etiquette(apercu(new Interrupteur(true), Etat.DESACTIVE), "désactivé"));
        controles.getChildren().add(inter);
        controles.getChildren().add(legende("Tuile (.plateforme) et BoutonIcone (.barre-icone) — normal · survol · pressé"));
        HBox tuiles = rangee();
        tuiles.getChildren().addAll(
                etats(() -> new Tuile("Windows", Tuile.icone("M3 5.1 10.4 4v7.2H3V5.1Zm0 13.8 7.4 1.1v-7.1H3v6Zm8.3 1.2L21 21.5v-8.6h-9.7v7.2Zm0-16.2v7.3H21V2.5l-9.7 1.4Z", Color.web("#262a33")), Jetons.rem(6.1719), Jetons.rem(1.6406)), Etat.NORMAL, Etat.SURVOL),
                etats(() -> new BoutonIcone(BoutonIcone.REGLAGES), Etat.NORMAL, Etat.SURVOL, Etat.PRESSE),
                etats(() -> new BoutonIcone(BoutonIcone.FERMER), Etat.NORMAL, Etat.SURVOL));
        controles.getChildren().add(tuiles);
        controles.getChildren().add(legende("Textes : corps Nunito, champ, boutons de la fenêtre de compte, capsule"));
        HBox textes = rangee();
        Text corps = new Text("Le serveur PokéRayou est privé. Pokényle est ouvert à tous.");
        corps.getStyleClass().add("kit-texte-fort");
        javafx.scene.control.TextField champ = new javafx.scene.control.TextField();
        champ.setPromptText("Ton pseudo");
        champ.getStyleClass().add("kit-champ");
        champ.setPrefWidth(Jetons.rem(12));
        javafx.scene.control.Button valider = new javafx.scene.control.Button("Valider");
        valider.getStyleClass().add("kit-valider");
        Label capsule = new Label("Ouverture mi-décembre · premium ou crack");
        capsule.getStyleClass().add("kit-capsule");
        textes.getChildren().addAll(corps, champ, valider, capsule);
        controles.getChildren().add(textes);
        CarteVerre carte = new CarteVerre(CarteVerre.Variante.CLAIRE, controles);
        carte.setAlignment(Pos.TOP_LEFT);
        colonne.getChildren().add(carte);

        StackPane racine = new StackPane(colonne);
        racine.setAlignment(Pos.TOP_LEFT);
        racine.setBackground(new Background(new BackgroundFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Jetons.CIEL), new Stop(1, Color.web("#d9eefc"))), null, null)));
        return racine;
    }

    private static Parent ecranExemple() {
        VBox v = new VBox(Jetons.rem(0.8));
        v.setAlignment(Pos.CENTER);
        v.setPadding(new Insets(Jetons.rem(2.6), Jetons.rem(1), Jetons.rem(1), Jetons.rem(1)));
        Segment s = new Segment("Pokényle", "NyleRP");
        v.getChildren().addAll(new TitreEclats("Choisis ton univers", TitreEclats.Style.SECONDAIRE, Jetons.rem(1.4), null), s, new BoutonJouer("Jouer"));
        v.setBackground(new Background(new BackgroundFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#8cc8f4")), new Stop(1, Jetons.CIEL)), null, null)));
        return v;
    }

    private static HBox rangee() {
        HBox h = new HBox(Jetons.rem(1.6));
        h.setAlignment(Pos.BOTTOM_LEFT);
        return h;
    }

    private static Node legende(String s) {
        Text t = new Text(s);
        t.getStyleClass().add("kit-surtitre");
        t.setFill(Color.web("#3d5a8a"));
        Label l = new Label(s);
        l.setStyle("-fx-font-family: 'Nunito ExtraBold'; -fx-font-size: 12px; -fx-text-fill: #30384a; -fx-background-color: rgba(255,255,255,0.75); -fx-background-radius: 99px; -fx-padding: 3px 10px;");
        return l;
    }

    private static <T extends Node> T apercu(T n, Etat e) {
        if (n instanceof Etat.Apercu a) a.apercu(e);
        return n;
    }

    private static Node etiquette(Node n, String nom) {
        Label l = new Label(nom);
        l.setStyle("-fx-font-family: 'Nunito Bold'; -fx-font-size: 11px; -fx-text-fill: #4d6b84;");
        VBox v = new VBox(Jetons.rem(0.35), n, l);
        v.setAlignment(Pos.BOTTOM_CENTER);
        return v;
    }

    private static Node etats(java.util.function.Supplier<Node> fabrique, Etat... liste) {
        HBox h = new HBox(Jetons.rem(1.2));
        h.setAlignment(Pos.BOTTOM_LEFT);
        for (Etat e : liste) {
            Node n = fabrique.get();
            apercu(n, e);
            h.getChildren().add(etiquette(n, e.name().toLowerCase(java.util.Locale.ROOT)));
        }
        return h;
    }

    private Vitrine() {}
}
