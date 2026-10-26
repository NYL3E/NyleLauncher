package fr.nylerp.launcher.ui.coquille;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.auth.AccountStore;
import fr.nylerp.launcher.config.Constants;
import fr.nylerp.launcher.ui.kit.BoutonCompte;
import fr.nylerp.launcher.ui.kit.BoutonPilule;
import fr.nylerp.launcher.ui.kit.Curseurs;
import fr.nylerp.launcher.ui.kit.Jetons;
import fr.nylerp.launcher.ui.kit.Polices;
import fr.nylerp.launcher.ui.kit.Segment;
import fr.nylerp.launcher.ui.univers.ImagesDistantes;
import fr.nylerp.launcher.ui.univers.Pilules;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.stage.Popup;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * L'en-tête de la coquille, posé DANS la barre de la fenêtre (la poignée de déplacement sépare les
 * deux groupes) : à gauche le logo de l'univers (la tête de Pikachu pour Pokényle, le coffre pour NyleRP), le choix
 * d'univers et le badge DEV ; à droite Discord, la
 * boutique et le compte (menu des comptes : changer, ajouter, retirer,
 * se déconnecter — repris de l'ancien menu de la capsule).
 */
final class EnTete {

    static final String SVG_PANIER = "M3.5 4.5h2.1l2.2 10a1.6 1.6 0 0 0 1.6 1.3h7.9a1.6 1.6 0 0 0 1.5-1.1l1.9-6.2H6.6M9.6 20.2h.01M17 20.2h.01";

    private final HBox gauche = new HBox();
    private final HBox droite = new HBox();
    private final Segment segment;
    private final ImageView coffre = new ImageView();
    private final Image logoPokenyle, coffreNylerp;
    private final BoutonCompte compteBouton;

    private IntConsumer surUnivers = i -> { };
    private Runnable surBoutique = () -> { }, surDiscord = () -> { };
    private Runnable surAjouterCompte = () -> { }, surDeconnexion = () -> { };
    private Consumer<Account> surChangerCompte = a -> { };
    private boolean ignorerSegment;

    EnTete(Account compte) {
        double h = Jetons.rem(1.15);
        logoPokenyle = ImagesDistantes.classpath("/images/univers/logo-pokenyle-pikachu-44-2x.png", 0, 0);
        coffreNylerp = ImagesDistantes.classpath("/images/univers/logo-nylerp-coffre-44-2x.png", 0, 0);
        coffre.setFitHeight(Jetons.rem(1.75));
        coffre.setPreserveRatio(true);
        coffre.setSmooth(true);
        coffre.setImage(fr.nylerp.launcher.config.ModeDeJeu.courant() == fr.nylerp.launcher.config.ModeDeJeu.POKENYLE
                ? logoPokenyle : coffreNylerp);

        ImageView lp = new ImageView(logoPokenyle), ln = new ImageView(coffreNylerp);
        for (ImageView v : new ImageView[]{lp, ln}) { v.setFitHeight(h); v.setPreserveRatio(true); v.setSmooth(true); }
        segment = new Segment(new String[]{"Pokényle", "NyleRP"}, new Node[]{lp, ln});
        segment.choixProperty().addListener((o, a, b) -> { if (!ignorerSegment) surUnivers.accept(b.intValue()); });
        Tooltip.install(segment, new Tooltip("Choisis ton univers"));

        gauche.setAlignment(Pos.CENTER_LEFT);
        gauche.setSpacing(Jetons.rem(0.6));
        gauche.setPickOnBounds(false);
        gauche.getChildren().addAll(coffre, segment);
        if (Constants.DEV) {
            Text dev = new Text("DEV");
            dev.setFont(Polices.noir(Jetons.rem(0.45)));
            dev.setFill(Color.WHITE);
            StackPane badge = new StackPane(dev);
            badge.setPadding(new Insets(Jetons.rem(0.1), Jetons.rem(0.35), Jetons.rem(0.1), Jetons.rem(0.35)));
            badge.setStyle("-fx-background-color: #3d3d50; -fx-background-radius: 99;");
            badge.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            Tooltip.install(badge, new Tooltip("Canal de développement"));
            gauche.getChildren().add(badge);
        }

        BoutonPilule discord = new BoutonPilule("Discord", BoutonPilule.Variante.DISCORD);
        discord.setOnAction(e -> surDiscord.run());
        HBox boutique = Pilules.claire("Boutique", SVG_PANIER);
        boutique.setOnMouseClicked(e -> surBoutique.run());

        compteBouton = new BoutonCompte(compte == null ? null : compte.username());
        Tetes.charger(compte, compteBouton::setImage);
        compteBouton.setOnAction(e -> montrerMenuComptes(compteBouton, compte));

        droite.setAlignment(Pos.CENTER_RIGHT);
        droite.setSpacing(Jetons.rem(0.35));
        droite.setPickOnBounds(false);
        droite.setPadding(new Insets(0, Jetons.rem(0.5), 0, 0));
        droite.getChildren().addAll(discord, boutique, compteBouton);
    }

    HBox gauche() { return gauche; }
    HBox droite() { return droite; }

    void setSurUnivers(IntConsumer r) { surUnivers = r; }
    void setSurBoutique(Runnable r) { surBoutique = r; }
    void setSurDiscord(Runnable r) { surDiscord = r; }
    void setComptes(Consumer<Account> changer, Runnable ajouter, Runnable deconnexion) {
        surChangerCompte = changer; surAjouterCompte = ajouter; surDeconnexion = deconnexion;
    }

    /** Positionne le segment sans déclencher de bascule (au démarrage, ou après un refus). */
    void montrerUnivers(int i) {
        ignorerSegment = true;
        segment.setChoix(i);
        ignorerSegment = false;
        coffre.setImage(i == 0 ? logoPokenyle : coffreNylerp);
    }

    /** Pendant un lancement ou un téléchargement, on ne change pas d'univers sous les pieds du jeu. */
    void verrouillerUnivers(boolean v) {
        segment.setDisable(v);
        segment.setOpacity(v ? 0.55 : 1);
    }

    /** Le choix d'univers plein écran est affiché : le segment ferait doublon. */
    void masquerSegment(boolean m) {
        segment.setVisible(!m);
        segment.setManaged(!m);
    }

    // ── Menu des comptes (logique de l'ancien showAccountMenu, au style du kit) ────────────────

    private void montrerMenuComptes(Node ancre, Account courant) {
        List<Account> roster = AccountStore.list();
        VBox boite = new VBox(Jetons.rem(0.1));
        boite.setPadding(new Insets(Jetons.rem(0.4)));
        boite.setStyle("-fx-background-color: rgba(255,255,255,0.97); -fx-background-radius: " + Jetons.rem(0.8)
                + "; -fx-border-color: white; -fx-border-radius: " + Jetons.rem(0.8) + "; -fx-border-width: 1.5;");
        boite.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(30, 60, 120, 0.28), 28, 0.1, 0, 8));
        Popup popup = new Popup();
        popup.setAutoHide(true);

        for (Account acc : roster) {
            boolean estCourant = courant != null && acc.type() == courant.type()
                    && (acc.isOffline()
                        ? acc.username().equalsIgnoreCase(courant.username())
                        : acc.uuid() != null && acc.uuid().equalsIgnoreCase(courant.uuid()));
            Text n = new Text(acc.username());
            n.setFont(Polices.extra(Jetons.rem(0.62)));
            n.setFill(Jetons.ENCRE);
            Text t = new Text(acc.isOffline() ? "Compte crack" : "Compte Microsoft");
            t.setFont(Polices.gras(Jetons.rem(0.48)));
            t.setFill(Color.web("#8fa1c2"));
            VBox col = new VBox(0, n, t);
            col.setAlignment(Pos.CENTER_LEFT);
            Circle actif = new Circle(Jetons.rem(0.16), estCourant ? Jetons.ACTIF : Color.TRANSPARENT);
            Region pousse = new Region();
            HBox.setHgrow(pousse, Priority.ALWAYS);

            Text retirer = new Text("Retirer");
            retirer.setFont(Polices.extra(Jetons.rem(0.5)));
            retirer.setFill(Color.web("#8fa1c2"));
            StackPane bRetirer = new StackPane(retirer);
            bRetirer.setPadding(new Insets(2, 8, 2, 8));
            bRetirer.setCursor(Curseurs.main());
            String base = "-fx-background-radius: 8; -fx-border-color: #d9e3f2; -fx-border-radius: 8; -fx-border-width: 1;";
            bRetirer.setStyle(base);
            final boolean[] confirme = {false};
            bRetirer.setOnMouseClicked(e -> {
                e.consume();
                if (!confirme[0]) {
                    confirme[0] = true;
                    retirer.setText("Confirmer");
                    retirer.setFill(Color.web("#e5484d"));
                    bRetirer.setStyle("-fx-background-radius: 8; -fx-background-color: rgba(229,72,77,0.10);"
                            + "-fx-border-color: rgba(229,72,77,0.55); -fx-border-radius: 8; -fx-border-width: 1;");
                } else {
                    boolean etaitActif = AccountStore.remove(acc);
                    popup.hide();
                    if (etaitActif) {
                        Account suivant = AccountStore.active();
                        if (suivant != null) surChangerCompte.accept(suivant);
                        else surDeconnexion.run();
                    } else {
                        montrerMenuComptes(ancre, courant);
                    }
                }
            });

            ImageView tete = new ImageView();
            tete.setFitWidth(Jetons.rem(1.1));
            tete.setFitHeight(Jetons.rem(1.1));
            tete.setSmooth(false);
            Tetes.charger(acc, tete::setImage);
            HBox ligne = new HBox(Jetons.rem(0.45), tete, col, pousse, actif, bRetirer);
            ligne.setAlignment(Pos.CENTER_LEFT);
            ligne.setPadding(new Insets(Jetons.rem(0.3), Jetons.rem(0.5), Jetons.rem(0.3), Jetons.rem(0.35)));
            ligne.setMinWidth(Jetons.rem(11.5));
            String fondLigne = "-fx-background-radius: 10;";
            ligne.setStyle(fondLigne);
            if (!estCourant) {
                ligne.setCursor(Curseurs.main());
                ligne.setOnMouseEntered(e -> ligne.setStyle(fondLigne + "-fx-background-color: #eef6ff;"));
                ligne.setOnMouseExited(e -> ligne.setStyle(fondLigne));
                ligne.setOnMouseClicked(e -> { popup.hide(); surChangerCompte.accept(acc); });
            }
            boite.getChildren().add(ligne);
        }

        Region sep = new Region();
        sep.setMinHeight(1);
        sep.setMaxHeight(1);
        sep.setStyle("-fx-background-color: #e3ebf6;");
        VBox.setMargin(sep, new Insets(4));
        boite.getChildren().add(sep);

        boolean plein = roster.size() >= AccountStore.MAX_ACCOUNTS;
        boite.getChildren().add(ligneAction(plein ? "Comptes au maximum (3/3)" : "+  Ajouter un compte (" + roster.size() + "/3)",
                plein ? null : () -> { popup.hide(); surAjouterCompte.run(); }, Jetons.ACTIF));
        boite.getChildren().add(ligneAction("Se déconnecter (retire ce compte)", () -> { popup.hide(); surDeconnexion.run(); },
                Color.web("#8fa1c2")));

        popup.getContent().add(boite);
        Bounds b = ancre.localToScreen(ancre.getBoundsInLocal());
        popup.show(ancre.getScene().getWindow(), b.getMaxX() - Jetons.rem(12), b.getMaxY() + 8);
    }

    private static HBox ligneAction(String libelle, Runnable action, Color couleur) {
        Text t = new Text(libelle);
        t.setFont(Polices.extra(Jetons.rem(0.56)));
        t.setFill(action == null ? Color.web("#b7c3d6") : couleur);
        HBox h = new HBox(t);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(Jetons.rem(0.3), Jetons.rem(0.5), Jetons.rem(0.3), Jetons.rem(0.45)));
        String f = "-fx-background-radius: 10;";
        h.setStyle(f);
        if (action != null) {
            h.setCursor(Curseurs.main());
            h.setOnMouseEntered(e -> h.setStyle(f + "-fx-background-color: #eef6ff;"));
            h.setOnMouseExited(e -> h.setStyle(f));
            h.setOnMouseClicked(e -> action.run());
        }
        return h;
    }
}
