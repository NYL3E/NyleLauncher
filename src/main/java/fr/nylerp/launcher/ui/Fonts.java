package fr.nylerp.launcher.ui;

import javafx.scene.text.Font;

/**
 * Centralised Montserrat font accessors. Each TTF registers itself with its own
 * JavaFX family name — that's why naive `-fx-font-family: "Montserrat"` only
 * matched the Regular variant. Use these helpers to pick the right weight file.
 */
public final class Fonts {
    public static Font regular(double size) { return Font.font("Montserrat",           size); }
    public static Font medium (double size) { return Font.font("Montserrat Medium",    size); }
    public static Font semi   (double size) { return Font.font("Montserrat SemiBold",  size); }
    public static Font bold   (double size) { return Font.font("Montserrat Bold",      size); }
    public static Font black  (double size) { return Font.font("Montserrat Black",     size); }

    private Fonts() {}
}
