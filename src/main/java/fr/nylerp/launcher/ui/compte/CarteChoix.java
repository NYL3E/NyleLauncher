package fr.nylerp.launcher.ui.compte;

import fr.nylerp.launcher.ui.kit.Calques;
import fr.nylerp.launcher.ui.kit.Curseurs;
import fr.nylerp.launcher.ui.kit.Etat;
import fr.nylerp.launcher.ui.kit.Jetons;
import fr.nylerp.launcher.ui.kit.Polices;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * Une des deux grandes cartes de l'écran de connexion : l'icône dans une pastille nacrée, un titre
 * et UNE ligne courte, rien d'autre (owner, 16/10 : « on comprend en une seconde et on a envie de
 * cliquer »). Toute la carte est le bouton (souris, Entrée, Espace). Au survol elle monte, son
 * ombre s'élargit, son liseré devient irisé et la pastille grossit un peu — courbes du site
 * ({@link Jetons#SORTIE}).
 */
public final class CarteChoix extends StackPane implements Etat.Apercu {

    private final DoubleProperty survol = new SimpleDoubleProperty(0);
    private final Region plaque = new Region();
    private final Region lisere = new Region();
    private final StackPane pastille;
    private final Label ligne;
    private final String libelleLigne;
    private final DropShadow ombre;
    private Timeline anim;
    private Runnable surChoix;
    private boolean occupe;

    /**
     * @param icone le dessin de la pastille (déjà dimensionné, ~2 rem)
     * @param titre ex. « J'ai acheté Minecraft »
     * @param ligne une seule ligne courte, ex. « Compte Microsoft »
     */
    public CarteChoix(Node icone, String titre, String ligne) {
        Polices.charger();
        this.libelleLigne = ligne;
        getStyleClass().add("compte-carte-choix");
        setFocusTraversable(true);
        setCursor(Curseurs.main());
        double l = Jetons.rem(16.2), h = Jetons.rem(14.2);
        setPrefSize(l, h);
        setMinSize(l, h);
        setMaxSize(l, h);

        CornerRadii r = new CornerRadii(Jetons.rem(1.3));
        plaque.setBackground(new Background(new BackgroundFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 255, 255, 0.92)), new Stop(1, Color.rgb(242, 249, 255, 0.86))), r, Insets.EMPTY)));
        plaque.setBorder(new Border(new BorderStroke(Color.rgb(255, 255, 255, 0.95), BorderStrokeStyle.SOLID, r, new BorderWidths(Jetons.rem(0.09)))));
        ombre = new DropShadow(BlurType.GAUSSIAN, Color.rgb(50, 110, 190, 0.22), Calques.flou(Jetons.rem(1.6)), 0, 0, Jetons.rem(0.6));
        plaque.setEffect(ombre);
        lisere.setBorder(new Border(new BorderStroke(Jetons.lisereIrise(), BorderStrokeStyle.SOLID, r, new BorderWidths(Jetons.rem(0.16)))));
        lisere.setOpacity(0);
        lisere.setMouseTransparent(true);

        double d = Jetons.rem(4.4);
        Circle disque = new Circle(d / 2);
        disque.setFill(Jetons.nacre());
        disque.setStroke(Jetons.lisereIrise());
        disque.setStrokeWidth(Jetons.rem(0.1));
        disque.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(60, 120, 200, 0.25), Calques.flou(Jetons.rem(0.8)), 0, 0, Jetons.rem(0.25)));
        pastille = new StackPane(disque, icone);
        pastille.setMaxSize(d, d);

        Label t = new Label(titre);
        t.setFont(Polices.noir(Jetons.rem(1.4)));
        t.setTextFill(Jetons.ENCRE);

        this.ligne = new Label(ligne);
        this.ligne.setFont(Polices.extra(Jetons.rem(0.9)));
        this.ligne.setTextFill(Jetons.ACTIF);

        VBox contenu = new VBox(Jetons.rem(0.45), pastille, espace(Jetons.rem(0.7)), t, this.ligne);
        contenu.setAlignment(Pos.CENTER);
        contenu.setMouseTransparent(true);

        getChildren().addAll(plaque, lisere, contenu);

        survol.addListener((o, a, b) -> appliquer(b.doubleValue()));
        hoverProperty().addListener((o, a, b) -> viser(b || isFocusVisible() ? 1 : 0));
        focusVisibleProperty().addListener((o, a, b) -> viser(b || isHover() ? 1 : 0));   // clavier seulement : pas de carte « survolée » au repos
        setOnMousePressed(e -> { setScaleX(0.985); setScaleY(0.985); });
        setOnMouseReleased(e -> { setScaleX(1); setScaleY(1); });
        setOnMouseClicked(e -> declencher());
        setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) { declencher(); e.consume(); } });
    }

    private static Region espace(double h) {
        Region r = new Region();
        r.setMinHeight(h);
        r.setPrefHeight(h);
        return r;
    }

    private void viser(double cible) {
        if (anim != null) anim.stop();
        anim = new Timeline(new KeyFrame(Duration.millis(cible > 0 ? 260 : 320), new KeyValue(survol, cible, Jetons.SORTIE)));
        anim.play();
    }

    private void appliquer(double k) {
        setTranslateY(-Jetons.rem(0.4) * k);
        lisere.setOpacity(k);
        pastille.setScaleX(1 + 0.06 * k);
        pastille.setScaleY(1 + 0.06 * k);
        ombre.setColor(Color.rgb(50, 110, 190, 0.22 + 0.14 * k));
        ombre.setRadius(Calques.flou(Jetons.rem(1.6 + 1.0 * k)));
        ombre.setOffsetY(Jetons.rem(0.6 + 0.4 * k));
    }

    private void declencher() {
        if (occupe || isDisabled() || surChoix == null) return;
        surChoix.run();
    }

    public void setSurChoix(Runnable r) { surChoix = r; }

    /** Même effet qu'un clic (clavier, essais automatisés). */
    public void choisir() { declencher(); }

    /** Pendant une connexion : la ligne devient {@code texte} et la carte ne répond plus aux clics. */
    public void setOccupe(boolean occupe, String texte) {
        this.occupe = occupe;
        ligne.setText(occupe && texte != null ? texte : libelleLigne);
        setCursor(occupe ? javafx.scene.Cursor.WAIT : Curseurs.main());
    }

    public boolean isOccupe() { return occupe; }

    @Override
    public void apercu(Etat etat) {
        if (anim != null) anim.stop();
        switch (etat) {
            case SURVOL -> survol.set(1);
            case PRESSE -> { survol.set(1); setScaleX(0.985); setScaleY(0.985); }
            case DESACTIVE -> { survol.set(0); setDisable(true); setOpacity(0.55); }
            default -> survol.set(0);
        }
        appliquer(survol.get());
    }
}
