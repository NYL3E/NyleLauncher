package fr.nylerp.launcher.ui;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/** Inline SVG icons — solid fill, sized consistently. */
public final class Icons {

    // Heroicons 24-solid cog-6-tooth — real gear with 6 teeth.
    private static final String GEAR_PATH =
            "M11.078 2.25c-.917 0-1.699.663-1.85 1.567L9.05 4.889c-.02.12-.115.26-.297.348-.338.162-.67.348-.986.57-.166.115-.334.126-.45.083L6.3 5.508a1.875 1.875 0 0 0-2.282.819l-.922 1.597a1.875 1.875 0 0 0 .432 2.385l.84.692c.095.078.17.229.154.43a7.598 7.598 0 0 0 0 1.139c.015.2-.059.352-.153.43l-.841.692a1.875 1.875 0 0 0-.432 2.385l.922 1.597a1.875 1.875 0 0 0 2.282.818l1.019-.382c.115-.043.283-.031.45.082.312.214.641.405.985.57.182.088.277.228.297.35l.178 1.071c.151.904.933 1.567 1.85 1.567h1.844c.916 0 1.699-.663 1.85-1.567l.178-1.072c.02-.12.114-.26.297-.349.344-.165.673-.356.985-.57.167-.114.335-.125.45-.082l1.02.382a1.875 1.875 0 0 0 2.28-.819l.923-1.597a1.875 1.875 0 0 0-.432-2.385l-.84-.692c-.095-.078-.17-.229-.154-.43a7.614 7.614 0 0 0 0-1.139c-.016-.2.059-.352.153-.43l.84-.692c.708-.582.891-1.59.433-2.385l-.922-1.597a1.875 1.875 0 0 0-2.282-.818l-1.02.382c-.114.043-.282.031-.449-.083a7.49 7.49 0 0 0-.985-.57c-.183-.087-.277-.227-.297-.348l-.179-1.072a1.875 1.875 0 0 0-1.85-1.567h-1.843ZM12 15.75a3.75 3.75 0 1 0 0-7.5 3.75 3.75 0 0 0 0 7.5Z";

    // Heroicons 24-solid arrow-left
    private static final String ARROW_LEFT_PATH =
            "M11.03 3.97a.75.75 0 0 1 0 1.06l-6.22 6.22H21a.75.75 0 0 1 0 1.5H4.81l6.22 6.22a.75.75 0 1 1-1.06 1.06l-7.5-7.5a.75.75 0 0 1 0-1.06l7.5-7.5a.75.75 0 0 1 1.06 0Z";

    // Heroicons 24-solid chevron-down
    private static final String CHEVRON_DOWN_PATH =
            "M12.53 16.28a.75.75 0 0 1-1.06 0l-7.5-7.5a.75.75 0 0 1 1.06-1.06L12 14.69l6.97-6.97a.75.75 0 1 1 1.06 1.06l-7.5 7.5Z";

    public static Group gear(double size, Color color) { return solid(GEAR_PATH, size, color); }
    public static Group arrowLeft(double size, Color color) { return solid(ARROW_LEFT_PATH, size, color); }
    public static Group chevronDown(double size, Color color) { return solid(CHEVRON_DOWN_PATH, size, color); }

    public static Group microsoftLogo(double size) {
        Group g = new Group();
        double half = size / 2;
        double gap  = size * 0.08;
        double tile = (size - gap) / 2;
        g.getChildren().addAll(
                rect(0, 0, tile, tile, "#F25022"),
                rect(half + gap/2, 0, tile, tile, "#7FBA00"),
                rect(0, half + gap/2, tile, tile, "#00A4EF"),
                rect(half + gap/2, half + gap/2, tile, tile, "#FFB900")
        );
        return g;
    }

    private static javafx.scene.shape.Rectangle rect(double x, double y, double w, double h, String fill) {
        javafx.scene.shape.Rectangle r = new javafx.scene.shape.Rectangle(x, y, w, h);
        r.setFill(Color.web(fill));
        return r;
    }

    private static Group solid(String path, double size, Color color) {
        SVGPath p = new SVGPath();
        p.setContent(path);
        p.setFill(color);
        double scale = size / 24.0;
        p.setScaleX(scale);
        p.setScaleY(scale);
        return new Group(p);
    }

    private Icons() {}
}
