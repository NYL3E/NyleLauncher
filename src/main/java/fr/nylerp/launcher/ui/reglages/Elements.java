package fr.nylerp.launcher.ui.reglages;

import fr.nylerp.launcher.ui.kit.CarteVerre;
import fr.nylerp.launcher.ui.kit.Interrupteur;
import fr.nylerp.launcher.ui.kit.Jetons;
import fr.nylerp.launcher.ui.kit.Polices;
import fr.nylerp.launcher.ui.kit.TitreEclats;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.function.Consumer;

/**
 * Briques des réglages dans le design du site (refonte 2026-10, lot L6) : titres Northura au style
 * Aniimo posés sur le ciel, puis le contenu dans des cartes de verre claires où le texte est à
 * l'encre du site. Les tailles passent par {@link Jetons#rem} pour suivre l'échelle choisie par la
 * coquille ; les couleurs et rayons des contrôles standard sont dans {@link #CSS}.
 */
public final class Elements {

    /** Feuille propre aux réglages (ciel, rail de navigation, curseurs, boutons). */
    public static final String CSS = "/css/reglages.css";

    private Elements() {}

    // ── titres et textes ─────────────────────────────────────────────────────────────────────────────

    /** Titre de section : Northura blanc au style Aniimo, lisible parce qu'il est posé sur le ciel. */
    public static TitreEclats titreSection(String libelle) {
        return new TitreEclats(libelle, TitreEclats.Style.SECONDAIRE, Jetons.rem(1.4), null);
    }

    /** Intitulé d'un réglage, à l'encre du site (grisé quand le réglage est imposé). */
    public static Label titreLigne(String s, boolean desactive) {
        Label l = new Label(s);
        l.setFont(Polices.extra(Jetons.rem(0.8)));
        l.setTextFill(desactive ? Jetons.ENCRE_3 : Jetons.ENCRE);
        l.setWrapText(true);
        return l;
    }

    /** Explication sous un intitulé. */
    public static Label texteLigne(String s) {
        Label l = new Label(s);
        l.setFont(Polices.gras(Jetons.rem(0.66)));
        l.setTextFill(Jetons.ENCRE_2);
        l.setWrapText(true);
        l.setLineSpacing(Jetons.rem(0.08));
        return l;
    }

    /** Phrase d'introduction en tête d'une carte. */
    public static Label intro(String s) {
        Label l = texteLigne(s);
        l.setFont(Polices.gras(Jetons.rem(0.7)));
        return l;
    }

    /** Petite note discrète (statut d'une opération, date d'une capture…). */
    public static Label note(String s) {
        Label l = new Label(s);
        l.setFont(Polices.gras(Jetons.rem(0.58)));
        l.setTextFill(Jetons.ENCRE_3);
        l.setWrapText(true);
        return l;
    }

    // ── cartes et lignes ─────────────────────────────────────────────────────────────────────────────

    /** Carte de verre du site : une intro facultative puis des lignes séparées par un filet. */
    public static CarteVerre carte(String introduction, Node... lignes) {
        VBox v = new VBox();
        if (introduction != null) {
            Label i = intro(introduction);
            v.getChildren().add(i);
            VBox.setMargin(i, new Insets(0, 0, Jetons.rem(0.45), 0));
        }
        for (int k = 0; k < lignes.length; k++) {
            if (k > 0) v.getChildren().add(filet());
            v.getChildren().add(lignes[k]);
        }
        CarteVerre c = new CarteVerre(CarteVerre.Variante.CLAIRE, v);
        c.setPadding(new Insets(Jetons.rem(0.95), Jetons.rem(1.25), Jetons.rem(0.95), Jetons.rem(1.25)));
        c.setMaxWidth(Double.MAX_VALUE);
        return c;
    }

    /** Une ligne de réglage : intitulé et explication à gauche, contrôle à droite. */
    public static HBox ligne(String titre, String explication, Node controle, boolean desactive) {
        VBox info = new VBox(Jetons.rem(0.18), titreLigne(titre, desactive));
        if (explication != null) info.getChildren().add(texteLigne(explication));
        info.setMinWidth(0);
        info.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(info, Priority.ALWAYS);
        HBox h = new HBox(Jetons.rem(1.1), info);
        if (controle != null) h.getChildren().add(controle);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(Jetons.rem(0.6), 0, Jetons.rem(0.6), 0));
        return h;
    }

    /**
     * Interrupteur du kit relié à un réglage. Désactivé : il montre l'état imposé tel quel (Distant
     * Horizons coupé sur Mac, Iris activé sur pokényle) et n'appelle jamais {@code onChange}.
     */
    public static Interrupteur interrupteur(boolean initial, Consumer<Boolean> onChange, boolean desactive) {
        Interrupteur i = new Interrupteur(initial);
        if (desactive) i.setDisable(true);
        else i.actifProperty().addListener((o, a, b) -> onChange.accept(b));
        return i;
    }

    /** Filet très fin entre deux lignes d'une carte. */
    public static Region filet() {
        Region r = new Region();
        r.setMinHeight(1);
        r.setMaxHeight(1);
        r.setBackground(new javafx.scene.layout.Background(new javafx.scene.layout.BackgroundFill(
                Color.rgb(48, 56, 74, 0.08), null, null)));
        return r;
    }

    // ── boutons (classes de kit.css + reglages.css) ──────────────────────────────────────────────────

    /** Bouton clair du site (« Ouvrir le dossier », « Trier »…). */
    public static Button boutonClair(String libelle, Node icone) {
        Button b = new Button(libelle);
        b.getStyleClass().add("kit-sortir");
        if (icone != null) {
            b.setGraphic(icone);
            b.setGraphicTextGap(Jetons.rem(0.42));
        }
        return b;
    }

    /** Bouton plein ardoise du site (action principale d'une carte). */
    public static Button boutonSombre(String libelle) {
        Button b = new Button(libelle);
        b.getStyleClass().add("kit-valider");
        return b;
    }

    /** Bouton d'action irréversible (contour rouge ; classe « arme » = rouge plein). */
    public static Button boutonDanger(String libelle) {
        Button b = new Button(libelle);
        b.getStyleClass().add("reglages-danger");
        return b;
    }
}
