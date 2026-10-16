package fr.nylerp.launcher.ui.compte;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.Region;

/**
 * Conteneur qui centre son unique enfant à sa taille préférée et le RÉDUIT (jamais ne l'agrandit)
 * s'il ne tient pas dans la place disponible, marges comprises. Les écrans de compte restent ainsi
 * entiers dans la fenêtre actuelle (1000 × 620) comme dans celle de la refonte (1180 × 700), quelle
 * que soit l'échelle {@code rem} choisie par la coquille, même quand un message d'erreur allonge
 * l'étape « Ton pseudo ».
 */
public final class Ajuste extends Region {

    private final Node enfant;
    private final Insets marges;

    public Ajuste(Node enfant, Insets marges) {
        this.enfant = enfant;
        this.marges = marges;
        getChildren().add(enfant);
        setPickOnBounds(false);
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth(), h = getHeight();
        double pw = enfant.prefWidth(-1), ph = enfant.prefHeight(pw);
        double dispoL = Math.max(1, w - marges.getLeft() - marges.getRight());
        double dispoH = Math.max(1, h - marges.getTop() - marges.getBottom());
        double k = Math.min(1, Math.min(dispoL / pw, dispoH / ph));
        enfant.resize(pw, ph);
        // centré dans la zone utile ; la réduction se fait autour du centre du nœud
        double cx = marges.getLeft() + dispoL / 2, cy = marges.getTop() + dispoH / 2;
        enfant.relocate(cx - pw / 2, cy - ph / 2);
        enfant.setScaleX(k);
        enfant.setScaleY(k);
    }
}
