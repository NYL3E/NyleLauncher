package fr.nylerp.launcher.ui;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/** Inline SVG icons sized + colored for the UI. */
public final class Icons {

    public static Group gear(double size, Color color) {
        return stroke(size, color,
                "M12 9.5 A 2.5 2.5 0 1 1 12 14.5 A 2.5 2.5 0 1 1 12 9.5 Z",
                "M12 2 L12 5 M12 19 L12 22",
                "M4.22 4.22 L6.34 6.34 M17.66 17.66 L19.78 19.78",
                "M2 12 L5 12 M19 12 L22 12",
                "M4.22 19.78 L6.34 17.66 M17.66 6.34 L19.78 4.22"
        );
    }

    public static Group arrowLeft(double size, Color color) {
        return stroke(size, color,
                "M19 12 L5 12",
                "M12 19 L5 12 L12 5"
        );
    }

    public static Group chevronDown(double size, Color color) {
        return stroke(size, color, "M6 9 L12 15 L18 9");
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

    private static Group stroke(double size, Color color, String... paths) {
        Group g = new Group();
        double scale = size / 24.0;
        for (String d : paths) {
            SVGPath p = new SVGPath();
            p.setContent(d);
            p.setStroke(color);
            p.setStrokeWidth(1.6);
            p.setStrokeLineCap(StrokeLineCap.ROUND);
            p.setStrokeLineJoin(StrokeLineJoin.ROUND);
            p.setFill(null);
            p.setScaleX(scale);
            p.setScaleY(scale);
            g.getChildren().add(p);
        }
        return g;
    }

    private Icons() {}
}
