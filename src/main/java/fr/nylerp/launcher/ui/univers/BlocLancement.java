package fr.nylerp.launcher.ui.univers;

import fr.nylerp.launcher.jeu.LanceurJeu;
import fr.nylerp.launcher.ui.kit.BoutonJouer;
import fr.nylerp.launcher.ui.kit.Jetons;
import fr.nylerp.launcher.ui.kit.Polices;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.Locale;

/**
 * Le gros bouton « Jouer » du kit, branché sur le {@link LanceurJeu} : libellé (« Jouer »,
 * « Mettre à jour », « 42% »…), désactivation pendant un travail, ligne d'état et barre d'avancement.
 * Les deux univers en ont chacun un ; ils montrent le même lancement.
 */
public final class BlocLancement extends VBox {

    private final BoutonJouer bouton;
    private final Text etat = new Text();
    private final Region piste = new Region();
    private final Region rempli = new Region();
    private final StackPane barre = new StackPane();
    private final Arc roue = new Arc();
    private final RotateTransition rotation;
    private Timeline indetermine;

    /**
     * @param surFondClair texte foncé (ciel de Pokényle) plutôt que blanc (vidéo de NyleRP)
     * @param filigrane    mot discret dans la pilule (« POKÉNYLE »…), ou {@code null}
     */
    public BlocLancement(LanceurJeu lanceur, boolean surFondClair, String filigrane, Pos alignement) {
        setSpacing(Jetons.rem(0.35));
        setAlignment(alignement);
        setPickOnBounds(false);
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);

        bouton = new BoutonJouer("Jouer");
        if (filigrane != null) bouton.setFiligrane(filigrane);
        bouton.setParticules(true);
        bouton.setOnAction(e -> lanceur.jouer());
        bouton.disableProperty().bind(lanceur.desactiveProperty());
        lanceur.libelle().textProperty().addListener((o, a, b) -> bouton.setTexte(casse(b)));
        bouton.setTexte(casse(lanceur.libelle().getText()));

        // ligne d'état : petite roue (seulement pendant un travail) + texte
        roue.setRadiusX(Jetons.rem(0.32));
        roue.setRadiusY(Jetons.rem(0.32));
        roue.setStartAngle(90);
        roue.setLength(250);
        roue.setType(ArcType.OPEN);
        roue.setFill(null);
        roue.setStroke(surFondClair ? Jetons.ACTIF : Color.WHITE);
        roue.setStrokeWidth(Jetons.rem(0.11));
        roue.setStrokeLineCap(StrokeLineCap.ROUND);
        roue.setVisible(false);
        roue.setManaged(false);
        rotation = new RotateTransition(Duration.millis(900), roue);
        rotation.setByAngle(360);
        rotation.setInterpolator(Interpolator.LINEAR);
        rotation.setCycleCount(Animation.INDEFINITE);
        lanceur.occupeProperty().addListener((o, a, occupe) -> {
            roue.setVisible(occupe);
            roue.setManaged(occupe);
            if (occupe) rotation.play(); else rotation.stop();
        });

        etat.setFont(Polices.extra(Jetons.rem(0.62)));
        etat.setFill(surFondClair ? Jetons.ENCRE : Color.WHITE);
        etat.setEffect(new DropShadow(BlurType.GAUSSIAN, surFondClair ? Color.rgb(255, 255, 255, 0.9) : Color.rgb(0, 0, 0, 0.65),
                Jetons.rem(0.35), 0.35, 0, 0));
        etat.textProperty().bind(lanceur.etat().textProperty());
        etat.wrappingWidthProperty().set(Jetons.rem(19));
        HBox ligne = new HBox(Jetons.rem(0.35), roue, etat);
        ligne.setAlignment(alignement == Pos.CENTER || alignement == Pos.TOP_CENTER ? Pos.CENTER : Pos.CENTER_LEFT);
        ligne.setPadding(new javafx.geometry.Insets(0, 0, 0, alignement == Pos.CENTER || alignement == Pos.TOP_CENTER ? 0 : Jetons.rem(0.7)));

        // barre d'avancement : piste nacrée + remplissage bleu du site
        double lb = Jetons.rem(15), hb = Jetons.rem(0.26);
        piste.setPrefSize(lb, hb);
        piste.setMaxSize(lb, hb);
        piste.setStyle("-fx-background-color: rgba(255,255,255,0.45); -fx-background-radius: 99;"
                + "-fx-border-color: rgba(255,255,255,0.8); -fx-border-radius: 99; -fx-border-width: 0.6;");
        rempli.setPrefHeight(hb);
        rempli.setMaxHeight(hb);
        rempli.setStyle("-fx-background-color: linear-gradient(to right, #73dbff, #10a7e6); -fx-background-radius: 99;");
        Pane couche = new Pane(rempli);
        couche.setPrefSize(lb, hb);
        couche.setMaxSize(lb, hb);
        Rectangle clip = new Rectangle(lb, hb);
        clip.setArcWidth(hb);
        clip.setArcHeight(hb);
        couche.setClip(clip);
        barre.getChildren().addAll(piste, couche);
        barre.setMaxSize(lb, hb);
        lanceur.avancement().progressProperty().addListener((o, a, b) -> avancer(b.doubleValue(), lb));
        avancer(lanceur.avancement().getProgress(), lb);
        VBox.setMargin(barre, new javafx.geometry.Insets(0, 0, 0,
                alignement == Pos.CENTER || alignement == Pos.TOP_CENTER ? 0 : Jetons.rem(0.7)));

        getChildren().addAll(bouton, ligne, barre);
    }

    public BoutonJouer bouton() { return bouton; }

    private void avancer(double p, double largeur) {
        boolean visible = p != 0;
        barre.setVisible(visible);
        if (p < 0) {                       // indéterminé : un segment qui balaie la piste
            rempli.setPrefWidth(largeur * 0.28);
            if (indetermine == null) {
                indetermine = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(rempli.translateXProperty(), -largeur * 0.3)),
                        new KeyFrame(Duration.millis(1300), new KeyValue(rempli.translateXProperty(), largeur, Jetons.DOUX)));
                indetermine.setCycleCount(Animation.INDEFINITE);
            }
            indetermine.play();
        } else {
            if (indetermine != null) indetermine.stop();
            rempli.setTranslateX(0);
            rempli.setPrefWidth(largeur * Math.min(1, p));
        }
    }

    /** « METTRE À JOUR » → « Mettre à jour » : la casse du site, quel que soit le libellé reçu. */
    static String casse(String brut) {
        if (brut == null || brut.isBlank()) return "Jouer";
        String b = brut.trim().toLowerCase(Locale.FRENCH);
        return Character.toUpperCase(b.charAt(0)) + b.substring(1);
    }
}
