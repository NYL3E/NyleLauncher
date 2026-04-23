package fr.nylerp.launcher.ui;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/** Inline SVG icons — thick, solid, unambiguous. */
public final class Icons {

    // Bootstrap gear-fill (16x16 viewBox, 8 teeth) — crystal clear as a gear
    private static final String GEAR_PATH =
            "M9.405 1.05c-.413-1.4-2.397-1.4-2.81 0l-.1.34a1.464 1.464 0 0 1-2.105.872l-.31-.17c-1.283-.698-2.686.705-1.987 1.987l.169.311c.446.82.023 1.841-.872 2.105l-.34.1c-1.4.413-1.4 2.397 0 2.81l.34.1a1.464 1.464 0 0 1 .872 2.105l-.17.31c-.698 1.283.705 2.686 1.987 1.987l.311-.169a1.464 1.464 0 0 1 2.105.872l.1.34c.413 1.4 2.397 1.4 2.81 0l.1-.34a1.464 1.464 0 0 1 2.105-.872l.31.17c1.283.698 2.686-.705 1.987-1.987l-.169-.311a1.464 1.464 0 0 1 .872-2.105l.34-.1c1.4-.413 1.4-2.397 0-2.81l-.34-.1a1.464 1.464 0 0 1-.872-2.105l.17-.31c.698-1.283-.705-2.686-1.987-1.987l-.311.169a1.464 1.464 0 0 1-2.105-.872l-.1-.34zM8 10.93a2.929 2.929 0 1 1 0-5.86 2.929 2.929 0 0 1 0 5.858z";
    private static final double GEAR_VB = 16.0;

    // Heroicons 24-solid arrow-left
    private static final String ARROW_LEFT_PATH =
            "M11.03 3.97a.75.75 0 0 1 0 1.06l-6.22 6.22H21a.75.75 0 0 1 0 1.5H4.81l6.22 6.22a.75.75 0 1 1-1.06 1.06l-7.5-7.5a.75.75 0 0 1 0-1.06l7.5-7.5a.75.75 0 0 1 1.06 0Z";
    private static final double ARROW_VB = 24.0;

    // Heroicons 24-solid chevron-down
    private static final String CHEVRON_DOWN_PATH =
            "M12.53 16.28a.75.75 0 0 1-1.06 0l-7.5-7.5a.75.75 0 0 1 1.06-1.06L12 14.69l6.97-6.97a.75.75 0 1 1 1.06 1.06l-7.5 7.5Z";
    private static final double CHEVRON_VB = 24.0;

    public static Group gear(double size, Color color) {
        return solid(GEAR_PATH, size, GEAR_VB, color);
    }
    public static Group arrowLeft(double size, Color color) {
        return solid(ARROW_LEFT_PATH, size, ARROW_VB, color);
    }
    public static Group chevronDown(double size, Color color) {
        return solid(CHEVRON_DOWN_PATH, size, CHEVRON_VB, color);
    }

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

    private static Group solid(String path, double size, double viewBox, Color color) {
        SVGPath p = new SVGPath();
        p.setContent(path);
        p.setFill(color);
        double scale = size / viewBox;
        p.setScaleX(scale);
        p.setScaleY(scale);
        return new Group(p);
    }

    private Icons() {}
}
