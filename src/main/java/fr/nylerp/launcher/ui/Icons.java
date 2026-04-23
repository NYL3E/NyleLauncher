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

    // Discord logo (simplified, official shape)
    private static final String DISCORD_PATH =
            "M20.317 4.3698a19.7913 19.7913 0 0 0-4.8851-1.5152.0741.0741 0 0 0-.0785.0371c-.211.3753-.4447.8648-.6083 1.2495-1.8447-.2762-3.68-.2762-5.4868 0-.1636-.3933-.4058-.8742-.6177-1.2495a.077.077 0 0 0-.0785-.037 19.7363 19.7363 0 0 0-4.8852 1.515.0699.0699 0 0 0-.0321.0277C.5334 9.0458-.319 13.5799.0992 18.0578a.0824.0824 0 0 0 .0312.0561c2.0528 1.5076 4.0413 2.4228 5.9929 3.0294a.0777.0777 0 0 0 .0842-.0276c.4616-.6304.8731-1.2952 1.226-1.9942a.076.076 0 0 0-.0416-.1057c-.6528-.2476-1.2743-.5495-1.8722-.8923a.077.077 0 0 1-.0076-.1277c.1258-.0943.2517-.1923.3718-.2914a.0743.0743 0 0 1 .0776-.0105c3.9278 1.7933 8.18 1.7933 12.0614 0a.0739.0739 0 0 1 .0785.0095c.1202.099.246.1981.3728.2924a.077.077 0 0 1-.0066.1276 12.2986 12.2986 0 0 1-1.873.8914.0766.0766 0 0 0-.0407.1067c.3604.698.7719 1.3628 1.225 1.9932a.076.076 0 0 0 .0842.0286c1.961-.6067 3.9495-1.5219 6.0023-3.0294a.077.077 0 0 0 .0313-.0552c.5004-5.177-.8382-9.6739-3.5485-13.6604a.061.061 0 0 0-.0312-.0286ZM8.02 15.3312c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9555-2.4189 2.157-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.9555 2.4189-2.1569 2.4189Zm7.9748 0c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9554-2.4189 2.1569-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.946 2.4189-2.1568 2.4189Z";
    private static final double DISCORD_VB = 24.0;

    // Heroicons 24-solid shopping-cart
    private static final String CART_PATH =
            "M2.25 3a.75.75 0 0 0 0 1.5h1.386c.17 0 .318.114.362.278l2.558 9.592a3.752 3.752 0 0 0-2.806 3.63c0 .414.336.75.75.75h15.75a.75.75 0 0 0 0-1.5H5.378A2.25 2.25 0 0 1 7.5 15.75h11.218a.75.75 0 0 0 .674-.421 60.358 60.358 0 0 0 2.96-7.228.75.75 0 0 0-.525-.965A60.864 60.864 0 0 0 5.68 5.26l-.232-.867A1.875 1.875 0 0 0 3.636 3H2.25ZM3.75 21a1.5 1.5 0 1 1 3 0 1.5 1.5 0 0 1-3 0ZM16.5 21a1.5 1.5 0 1 1 3 0 1.5 1.5 0 0 1-3 0Z";
    private static final double CART_VB = 24.0;

    public static Group gear(double size, Color color) {
        return solid(GEAR_PATH, size, GEAR_VB, color);
    }
    public static Group arrowLeft(double size, Color color) {
        return solid(ARROW_LEFT_PATH, size, ARROW_VB, color);
    }
    public static Group chevronDown(double size, Color color) {
        return solid(CHEVRON_DOWN_PATH, size, CHEVRON_VB, color);
    }
    public static Group discord(double size, Color color) {
        return solid(DISCORD_PATH, size, DISCORD_VB, color);
    }
    public static Group cart(double size, Color color) {
        return solid(CART_PATH, size, CART_VB, color);
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
