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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

/**
 * Une des deux grandes cartes de l'écran de connexion (« J'ai acheté Minecraft » / « Je joue en
 * crack ») : verre clair du site, pastille d'icône nacrée, titre, sous-titre, deux lignes
 * d'explication et un appel à l'action en bas. Toute la carte est cliquable (souris, Entrée,
 * Espace). Au survol : la carte monte, son ombre s'élargit, le lisere devient irisé et la flèche de
 * l'appel glisse — mêmes courbes que le site ({@link Jetons#SORTIE}).
 */
public final class CarteChoix extends StackPane implements Etat.Apercu {

    private final DoubleProperty survol = new SimpleDoubleProperty(0);
    private final Region plaque = new Region();
    private final Region lisere = new Region();
    private final Label appel;
    private final Node fleche;
    private final DropShadow ombre;
    private Timeline anim;
    private Runnable surChoix;
    private boolean occupe;
    private final String libelleAppel;

    /**
     * @param icone   le dessin de la pastille (déjà dimensionné, ~1,5 rem)
     * @param titre   ex. « J'ai acheté Minecraft »
     * @param soustitre ex. « Compte Microsoft, premium »
     * @param lignes  une ou deux phrases courtes d'explication
     * @param appel   libellé du bas, ex. « Se connecter avec Microsoft »
     */
    public CarteChoix(Node icone, String titre, String soustitre, String[] lignes, String appel) {
        Polices.charger();
        this.libelleAppel = appel;
        getStyleClass().add("compte-carte-choix");
        setFocusTraversable(true);
        setCursor(Curseurs.main());
        double l = Jetons.rem(17.6), h = Jetons.rem(19.2);
        double utile = l - Jetons.rem(2.4);
        setPrefSize(l, h);
        setMinSize(l, h);
        setMaxSize(l, h);

        CornerRadii r = new CornerRadii(Jetons.rem(1.15));
        plaque.setBackground(new Background(new BackgroundFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 255, 255, 0.86)), new Stop(1, Color.rgb(240, 248, 255, 0.78))), r, Insets.EMPTY)));
        plaque.setBorder(new Border(new BorderStroke(Color.rgb(255, 255, 255, 0.95), BorderStrokeStyle.SOLID, r, new BorderWidths(Jetons.rem(0.09)))));
        ombre = new DropShadow(BlurType.GAUSSIAN, Color.rgb(50, 110, 190, 0.22), Calques.flou(Jetons.rem(1.6)), 0, 0, Jetons.rem(0.6));
        plaque.setEffect(ombre);
        // lisere irisé, visible au survol seulement
        lisere.setBorder(new Border(new BorderStroke(Jetons.lisereIrise(), BorderStrokeStyle.SOLID, r, new BorderWidths(Jetons.rem(0.14)))));
        lisere.setOpacity(0);
        lisere.setMouseTransparent(true);

        // pastille d'icône : disque nacré, lisere irisé, ombre douce
        double d = Jetons.rem(3.6);
        Circle disque = new Circle(d / 2);
        disque.setFill(Jetons.nacre());
        disque.setStroke(Jetons.lisereIrise());
        disque.setStrokeWidth(Jetons.rem(0.1));
        disque.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(60, 120, 200, 0.25), Calques.flou(Jetons.rem(0.8)), 0, 0, Jetons.rem(0.25)));
        StackPane pastille = new StackPane(disque, icone);
        pastille.setMaxSize(d, d);

        Label t = new Label(titre);
        t.setFont(Polices.noir(Jetons.rem(1.32)));
        t.setTextFill(Jetons.ENCRE);
        t.setWrapText(true);
        t.setAlignment(Pos.CENTER);
        t.setStyle("-fx-text-alignment: center;");
        t.setMaxWidth(utile);
        t.setMinHeight(Region.USE_PREF_SIZE);

        Label st = new Label(soustitre);
        st.setFont(Polices.extra(Jetons.rem(0.86)));
        st.setTextFill(Jetons.ACTIF);
        st.setWrapText(true);
        st.setStyle("-fx-text-alignment: center;");
        st.setAlignment(Pos.CENTER);
        st.setMaxWidth(utile);
        st.setMinHeight(Region.USE_PREF_SIZE);

        VBox explications = new VBox(Jetons.rem(0.35));
        explications.setAlignment(Pos.TOP_CENTER);
        for (String s : lignes) {
            Label li = new Label(s);
            li.setFont(Polices.gras(Jetons.rem(0.74)));
            li.setTextFill(Jetons.ENCRE_2);
            li.setWrapText(true);
            li.setAlignment(Pos.CENTER);
            li.setStyle("-fx-text-alignment: center; -fx-line-spacing: 1px;");
            li.setMaxWidth(utile);
            li.setMinHeight(Region.USE_PREF_SIZE);
            explications.getChildren().add(li);
        }

        // appel du bas : pilule ardoise (le bouton plein de la fenêtre de compte du site), flèche qui glisse
        this.appel = new Label(appel);
        this.appel.setFont(Polices.extra(Jetons.rem(0.8)));
        this.appel.setTextFill(Color.WHITE);
        SVGPath f = new SVGPath();
        f.setContent("M1 6h9.2L6.6 2.4 8 1l6 6-6 6-1.4-1.4L10.2 8H1z");
        f.setFill(Color.WHITE);
        f.setScaleX(Jetons.rem(0.62) / 14);
        f.setScaleY(Jetons.rem(0.62) / 14);
        fleche = new StackPane(f);
        HBox pilule = new HBox(Jetons.rem(0.45), this.appel, fleche);
        pilule.setAlignment(Pos.CENTER);
        pilule.setPadding(new Insets(Jetons.rem(0.55), Jetons.rem(1.1), Jetons.rem(0.55), Jetons.rem(1.1)));
        pilule.setBackground(new Background(new BackgroundFill(Color.web("#3c3c50"), new CornerRadii(Jetons.rem(0.5)), Insets.EMPTY)));
        pilule.setMaxWidth(Region.USE_PREF_SIZE);

        Region ressort = new Region();
        VBox.setVgrow(ressort, Priority.ALWAYS);
        VBox contenu = new VBox(Jetons.rem(0.5), pastille, espace(Jetons.rem(0.2)), t, st, espace(Jetons.rem(0.3)), explications, ressort, pilule);
        contenu.setAlignment(Pos.TOP_CENTER);
        contenu.setPadding(new Insets(Jetons.rem(1.6), Jetons.rem(1.2), Jetons.rem(1.35), Jetons.rem(1.2)));
        contenu.setMouseTransparent(true);

        getChildren().addAll(plaque, lisere, contenu);

        survol.addListener((o, a, b) -> appliquer(b.doubleValue()));
        hoverProperty().addListener((o, a, b) -> viser(b ? 1 : 0));
        focusedProperty().addListener((o, a, b) -> viser(b || isHover() ? 1 : 0));
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
        setTranslateY(-Jetons.rem(0.32) * k);
        lisere.setOpacity(k);
        ombre.setColor(Color.rgb(50, 110, 190, 0.22 + 0.14 * k));
        ombre.setRadius(Calques.flou(Jetons.rem(1.6 + 1.0 * k)));
        ombre.setOffsetY(Jetons.rem(0.6 + 0.4 * k));
        fleche.setTranslateX(Jetons.rem(0.22) * k);
    }

    private void declencher() {
        if (occupe || isDisabled() || surChoix == null) return;
        surChoix.run();
    }

    public void setSurChoix(Runnable r) { surChoix = r; }

    /** Même effet qu'un clic (clavier, essais automatisés). */
    public void choisir() { declencher(); }

    /** Pendant une connexion : l'appel devient {@code texte} et la carte ne répond plus aux clics. */
    public void setOccupe(boolean occupe, String texte) {
        this.occupe = occupe;
        appel.setText(occupe && texte != null ? texte : libelleAppel);
        fleche.setVisible(!occupe);
        fleche.setManaged(!occupe);
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
