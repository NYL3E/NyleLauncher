package fr.nylerp.launcher.ui.kit;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * La fenêtre du launcher sans bordure système : coins arrondis réels (scène transparente), ombre portée dessinée par
 * nous (les fenêtres transparentes n'en ont pas), barre du haut qui sert de poignée pour déplacer la fenêtre, et deux
 * commandes à droite — réduire et fermer — au dessin des icônes du site.
 *
 * <p>Fermer n'arrête PAS l'application : il appelle {@link #setSurFermer(Runnable)} (par défaut : cacher la fenêtre),
 * que la coquille branche sur la barre système (lots L1/L5). Cmd+W, Alt+F4 et la croix du système suivent le même
 * chemin.
 *
 * <pre>
 *   ChromeFenetre chrome = ChromeFenetre.installer(stage, ecran, 1180, 700);
 *   chrome.getBarre().getChildren().add(0, logo);        // à gauche de la poignée
 *   chrome.setSurFermer(() -> barreSysteme.cacherFenetre());
 *   stage.show();
 * </pre>
 */
public final class ChromeFenetre {

    /** Marge transparente autour de la carte, pour que l'ombre ne soit pas coupée. */
    public static final double MARGE = 26;

    private final Stage stage;
    private final StackPane carte = new StackPane();
    private final HBox barre = new HBox();
    private final Region poignee = new Region();
    private final BoutonIcone reduire = new BoutonIcone(BoutonIcone.REDUIRE);
    private final BoutonIcone fermer = new BoutonIcone(BoutonIcone.FERMER);
    private Runnable surFermer;
    private Runnable surReduire;
    private double dx, dy;

    private ChromeFenetre(Stage stage, Parent contenu, double largeur, double hauteur) {
        this.stage = stage;
        Polices.charger();
        surFermer = stage::hide;
        surReduire = () -> stage.setIconified(true);

        double rayon = Jetons.rem(0.9);
        carte.setPrefSize(largeur, hauteur);
        carte.setMinSize(largeur, hauteur);
        carte.setMaxSize(largeur, hauteur);
        carte.setBackground(new Background(new BackgroundFill(Jetons.CIEL, new CornerRadii(rayon), Insets.EMPTY)));
        Rectangle clip = new Rectangle(largeur, hauteur);
        clip.setArcWidth(2 * rayon);
        clip.setArcHeight(2 * rayon);
        carte.setClip(clip);
        carte.getStyleClass().add("kit-fenetre");

        // barre du haut : [contenu ajouté par la coquille] [poignée extensible] [réduire] [fermer]
        double hb = Jetons.rem(2.3);
        barre.setAlignment(Pos.CENTER_LEFT);
        barre.setSpacing(Jetons.rem(0.25));
        barre.setPadding(new Insets(0, Jetons.rem(0.55), 0, Jetons.rem(0.9)));
        barre.setPrefHeight(hb);
        barre.setMinHeight(hb);
        barre.setMaxHeight(hb);
        barre.setPickOnBounds(false);
        HBox.setHgrow(poignee, Priority.ALWAYS);
        poignee.setPrefHeight(hb);
        poignee.setCursor(Curseurs.fleche());
        reduire.setOnAction(e -> surReduire.run());
        fermer.setOnAction(e -> surFermer.run());
        barre.getChildren().addAll(poignee, reduire, fermer);
        barre.getStyleClass().add("kit-barre-fenetre");
        StackPane.setAlignment(barre, Pos.TOP_CENTER);

        if (contenu != null) carte.getChildren().add(contenu);
        carte.getChildren().add(barre);
        rendreDeplacable(poignee);

        StackPane racine = new StackPane(carte);
        racine.setPadding(new Insets(MARGE));
        racine.setBackground(Background.EMPTY);
        // l'ombre est portée par un fond de même forme posé sous la carte (la carte est détourée : son effet serait coupé)
        Region ombre = new Region();
        ombre.setPrefSize(largeur, hauteur);
        ombre.setMaxSize(largeur, hauteur);
        ombre.setBackground(new Background(new BackgroundFill(Jetons.CIEL, new CornerRadii(rayon), Insets.EMPTY)));
        DropShadow portee = new DropShadow(BlurType.GAUSSIAN, Color.rgb(20, 40, 90, 0.30), 26, 0, 0, 7);
        ombre.setEffect(portee);
        ombre.setMouseTransparent(true);
        racine.getChildren().add(0, ombre);

        Scene scene = new Scene(racine, largeur + 2 * MARGE, hauteur + 2 * MARGE, Color.TRANSPARENT);
        var css = ChromeFenetre.class.getResource("/css/kit.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        scene.setCursor(Curseurs.fleche());
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN), () -> surFermer.run());
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.setOnCloseRequest(e -> { e.consume(); surFermer.run(); });
    }

    /**
     * Habille une fenêtre AVANT son premier affichage (initStyle n'est plus possible ensuite).
     *
     * @param contenu l'écran (peut être remplacé ensuite par {@link #setContenu(Parent)})
     */
    public static ChromeFenetre installer(Stage stage, Parent contenu, double largeur, double hauteur) {
        return new ChromeFenetre(stage, contenu, largeur, hauteur);
    }

    /** Remplace l'écran affiché sous la barre (la barre reste au-dessus). */
    public void setContenu(Parent contenu) {
        carte.getChildren().removeIf(n -> n != barre);
        if (contenu != null) carte.getChildren().add(0, contenu);
    }

    /** Fait glisser la fenêtre depuis n'importe quel nœud (la poignée de la barre l'est déjà). */
    public void rendreDeplacable(javafx.scene.Node n) {
        n.setOnMousePressed(e -> { dx = stage.getX() - e.getScreenX(); dy = stage.getY() - e.getScreenY(); });
        n.setOnMouseDragged(e -> { stage.setX(e.getScreenX() + dx); stage.setY(e.getScreenY() + dy); });
    }

    /** Appelé par la croix, Cmd+W, Alt+F4 (par défaut : cacher la fenêtre). */
    public void setSurFermer(Runnable r) { surFermer = r == null ? stage::hide : r; }
    /** Appelé par le bouton réduire (par défaut : réduire la fenêtre). */
    public void setSurReduire(Runnable r) { surReduire = r == null ? () -> stage.setIconified(true) : r; }

    /** La barre du haut : la coquille y ajoute son logo, son compte… (index 0 = tout à gauche, avant la poignée). */
    public HBox getBarre() { return barre; }
    public Pane getCarte() { return carte; }
    public Stage getStage() { return stage; }
    public BoutonIcone getBoutonFermer() { return fermer; }
    public BoutonIcone getBoutonReduire() { return reduire; }
}
