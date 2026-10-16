package fr.nylerp.launcher.ui.kit;

import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.image.Image;

/**
 * Les curseurs du site (fournis par l'owner, 30 × 30) : flèche partout, main sur ce qui se clique. Pointes : (3, 2)
 * pour la flèche, (4, 2) pour l'index de la main — comme {@code --curseur} et {@code --curseur-main} du site.
 * Repli sur les curseurs du système si l'image manque.
 */
public final class Curseurs {

    private static Cursor fleche, main;

    public static synchronized Cursor fleche() {
        if (fleche == null) fleche = charger("/images/kit/curseur-fleche.png", 3, 2, Cursor.DEFAULT);
        return fleche;
    }

    public static synchronized Cursor main() {
        if (main == null) main = charger("/images/kit/curseur-main.png", 4, 2, Cursor.HAND);
        return main;
    }

    private static Cursor charger(String chemin, double x, double y, Cursor repli) {
        try (var in = Curseurs.class.getResourceAsStream(chemin)) {
            if (in == null) return repli;
            Image img = new Image(in);
            return img.isError() ? repli : new ImageCursor(img, x, y);
        } catch (Exception e) {
            return repli;
        }
    }

    private Curseurs() {}
}
