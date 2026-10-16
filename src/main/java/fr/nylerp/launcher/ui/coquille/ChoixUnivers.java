package fr.nylerp.launcher.ui.coquille;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.config.ModeDeJeu;
import fr.nylerp.launcher.ui.kit.Curseurs;
import fr.nylerp.launcher.ui.kit.Jetons;
import fr.nylerp.launcher.ui.kit.Polices;
import fr.nylerp.launcher.ui.kit.TitreEclats;
import fr.nylerp.launcher.ui.univers.ImagesDistantes;
import fr.nylerp.launcher.ui.univers.UniversPokenyle;
import fr.nylerp.launcher.univers.Services;
import fr.nylerp.launcher.update.CheminsMedias;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * Le choix d'univers du premier lancement : deux grandes cartes, chacune avec son image, une phrase
 * et son état. Ensuite on arrive directement dans le dernier univers, et le segment de l'en-tête
 * permet de basculer.
 */
final class ChoixUnivers extends StackPane {

    private final Consumer<ModeDeJeu> surChoix;
    private boolean choisi;
    private Text etatNylerp;

    ChoixUnivers(double echelle, Account compte, Consumer<ModeDeJeu> surChoix) {
        this.surChoix = surChoix;
        double l = 1180 * echelle, h = 700 * echelle;
        setPrefSize(l, h);
        setMaxSize(l, h);
        setStyle("-fx-background-color: linear-gradient(to bottom, #6fb4ea, #cfe9fb);");

        ImageView fond = new ImageView();
        fond.setFitWidth(l * 1.06);
        fond.setFitHeight(h * 1.06);
        fond.setEffect(new GaussianBlur(Jetons.rem(0.9)));
        ImagesDistantes.charger(CheminsMedias.POKENYLE_FOND, "/images/univers/fond-pokenyle-1x.jpg", 0, 0, fond::setImage);
        Region voile = new Region();
        voile.setStyle("-fx-background-color: linear-gradient(to bottom, rgba(40,90,170,0.45), rgba(40,90,170,0.18) 60%, rgba(234,247,255,0.55));");

        TitreEclats titre = new TitreEclats("Choisis ton univers", TitreEclats.Style.SECONDAIRE, Jetons.rem(2.0), "univers");

        boolean ouvert = UniversPokenyle.ouvertPour(compte);
        String etatPkn = ouvert ? "Ouvert" : "Ouverture " + Services.etatPokenyle.libelleOuverture();
        StackPane cartePkn = carte(CheminsMedias.CARTE_POKENYLE_2X, "/images/univers/carte-pokenyle-1x.jpg",
etatPkn, ouvert ? "#22C55E" : "#10a7e6",
                ModeDeJeu.POKENYLE, null);
        StackPane carteNyl = carte(CheminsMedias.CARTE_NYLERP_2X, "/images/univers/carte-nylerp-1x.jpg",
"En ligne", "#22C55E", ModeDeJeu.NYLERP, t -> etatNylerp = t);
        fr.nylerp.launcher.ui.univers.JoueursEnLigne.abonner(n -> Platform.runLater(() -> {
            if (etatNylerp != null) etatNylerp.setText(n >= 0 ? "En ligne · " + n + (n > 1 ? " joueurs" : " joueur") : "Hors ligne");
        }));

        HBox cartes = new HBox(Jetons.rem(1.8), cartePkn, carteNyl);
        cartes.setAlignment(Pos.CENTER);
        VBox colonne = new VBox(Jetons.rem(1.1), titre, cartes);
        colonne.setAlignment(Pos.CENTER);
        colonne.setPadding(new Insets(Jetons.rem(1.6), 0, 0, 0));
        getChildren().addAll(fond, voile, colonne);
    }

    private StackPane carte(String chemin, String repli, String etat, String couleurEtat, ModeDeJeu mode,
                            Consumer<Text> surTexteEtat) {
        double lc = Jetons.rem(12.6), hc = lc * 1240 / 1040.0;
        ImageView image = new ImageView();
        image.setFitWidth(lc);
        image.setFitHeight(hc);
        image.setSmooth(true);
        ImagesDistantes.charger(chemin, repli, lc * 2, hc * 2, image::setImage);
        Rectangle clip = new Rectangle(lc, hc);
        clip.setArcWidth(Jetons.rem(2.1));
        clip.setArcHeight(Jetons.rem(2.1));
        StackPane cadre = new StackPane(image);
        cadre.setClip(clip);

        // bas de carte : capsule d'état + phrase, sur un dégradé blanc
        Circle point = new Circle(Jetons.rem(0.2), Color.web(couleurEtat));
        Text tEtat = new Text(etat);
        tEtat.setFont(Polices.noir(Jetons.rem(0.56)));
        tEtat.setFill(Jetons.ENCRE);
        if (surTexteEtat != null) surTexteEtat.accept(tEtat);
        HBox capsule = new HBox(Jetons.rem(0.3), point, tEtat);
        capsule.setAlignment(Pos.CENTER);
        capsule.getStyleClass().add("kit-capsule");
        capsule.setMaxWidth(Region.USE_PREF_SIZE);
        VBox bas = new VBox(Jetons.rem(0.35), capsule);
        bas.setAlignment(Pos.CENTER);
        bas.setPadding(new Insets(Jetons.rem(1.6), Jetons.rem(0.6), Jetons.rem(0.9), Jetons.rem(0.6)));
        bas.setStyle("-fx-background-color: linear-gradient(to bottom, rgba(255,255,255,0), rgba(255,255,255,0.7) 60%, rgba(255,255,255,0.85));");
        bas.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(bas, Pos.BOTTOM_CENTER);
        cadre.getChildren().add(bas);

        StackPane carte = new StackPane(cadre);
        carte.setMaxSize(lc, hc);
        carte.setStyle("-fx-border-color: white; -fx-border-width: " + Jetons.rem(0.12) + "; -fx-border-radius: " + Jetons.rem(1.05) + ";");
        DropShadow ombre = new DropShadow(BlurType.GAUSSIAN, Color.rgb(30, 70, 140, 0.30), Jetons.rem(1.6), 0, 0, Jetons.rem(0.5));
        carte.setEffect(ombre);
        carte.setCursor(Curseurs.main());
        carte.hoverProperty().addListener((o, a, v) -> {
            new Timeline(new KeyFrame(Duration.millis(320),
                    new KeyValue(carte.translateYProperty(), v ? -Jetons.rem(0.4) : 0, Jetons.REBOND),
                    new KeyValue(carte.scaleXProperty(), v ? 1.035 : 1, Jetons.REBOND),
                    new KeyValue(carte.scaleYProperty(), v ? 1.035 : 1, Jetons.REBOND),
                    new KeyValue(ombre.colorProperty(), v ? Color.rgb(16, 167, 230, 0.55) : Color.rgb(30, 70, 140, 0.30)),
                    new KeyValue(ombre.radiusProperty(), v ? Jetons.rem(2.4) : Jetons.rem(1.6)))).play();
        });
        carte.setOnMouseClicked(e -> choisir(carte, mode));
        return carte;
    }

    private void choisir(StackPane carte, ModeDeJeu mode) {
        if (choisi) return;
        choisi = true;
        ScaleTransition zoom = new ScaleTransition(Duration.millis(380), carte);
        zoom.setToX(1.12);
        zoom.setToY(1.12);
        FadeTransition fondu = new FadeTransition(Duration.millis(380), this);
        fondu.setToValue(0);
        TranslateTransition monte = new TranslateTransition(Duration.millis(380), carte);
        monte.setToY(-Jetons.rem(0.8));
        ParallelTransition p = new ParallelTransition(zoom, fondu, monte);
        p.setInterpolator(Jetons.SORTIE);
        surChoix.accept(mode);          // l'univers se construit DERRIÈRE pendant le fondu
        p.play();
        p.setOnFinished(e -> { if (getParent() instanceof javafx.scene.layout.Pane pp) pp.getChildren().remove(this); });
    }
}
