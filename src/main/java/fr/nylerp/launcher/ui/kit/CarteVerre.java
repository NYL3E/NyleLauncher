package fr.nylerp.launcher.ui.kit;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;

/**
 * Le cadre givré du site (le grand plateau de l'accueil, les panneaux, les fenêtres) : blanc translucide de haut en bas
 * (72 % → 62 % sur un bleu très pâle), liseré intérieur blanc de 0,09 rem, ombre bleutée douce
 * {@code 0 0.6rem 1.6rem rgb(70 130 200 / 0.18)}, rayon 1,05 rem. Variantes :
 * <ul>
 *   <li>{@link Variante#CLAIRE} : le plateau de l'accueil ;</li>
 *   <li>{@link Variante#OPAQUE} : la fenêtre de compte du site (fond blanc plein, ombre plus marquée) ;</li>
 * </ul>
 * Contenu libre (un seul enfant, ou {@link #getChildren()}). La tuile cliquable des plateformes, elle, est {@link Tuile}.
 */
public class CarteVerre extends StackPane {

    public enum Variante { CLAIRE, OPAQUE }

    private final Variante variante;

    public CarteVerre() { this(Variante.CLAIRE); }

    public CarteVerre(Variante variante, Node... enfants) {
        super(enfants);
        this.variante = variante;
        getStyleClass().add("kit-carte-verre");
        appliquer();
    }

    private void appliquer() {
        switch (variante) {
            case CLAIRE -> {
                CornerRadii r = new CornerRadii(Jetons.rem(1.05));
                setBackground(new Background(new BackgroundFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.rgb(255, 255, 255, 0.72)), new Stop(1, Color.rgb(240, 248, 255, 0.62))), r, Insets.EMPTY)));
                setBorder(new Border(new BorderStroke(Color.rgb(255, 255, 255, 0.95), BorderStrokeStyle.SOLID, r, new BorderWidths(Jetons.rem(0.09)))));
                setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(70, 130, 200, 0.18), Calques.flou(Jetons.rem(1.6)), 0, 0, Jetons.rem(0.6)));
                setPadding(new Insets(Jetons.rem(1.2)));
            }
            case OPAQUE -> {
                CornerRadii r = new CornerRadii(Jetons.rem(0.9));
                setBackground(new Background(new BackgroundFill(Color.WHITE, r, Insets.EMPTY)));
                setBorder(null);
                setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(20, 30, 60, 0.3), Calques.flou(Jetons.rem(3)), 0, 0, Jetons.rem(1)));
                setPadding(new Insets(Jetons.rem(1.6), Jetons.rem(2), Jetons.rem(1.6), Jetons.rem(2)));
            }
        }
    }

    public Variante getVariante() { return variante; }
}
