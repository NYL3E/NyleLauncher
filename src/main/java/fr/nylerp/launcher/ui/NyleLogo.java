package fr.nylerp.launcher.ui;

import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.Group;

/**
 * The angular "N" logo, sized-to-fit, in the brand orange.
 * Source path supplied by the user.
 */
public class NyleLogo extends Group {

    private static final String PATH =
            "M106.792 4.56L77.224 66H61.864L50.856 25.04L27.432 73.68L4.648 89.04L37.928 19.92H30.248L22.312 4.56H60.712L71.72 45.52L91.432 4.56H106.792Z";
    private static final double VB_W = 113;
    private static final double VB_H = 92;

    public NyleLogo(double targetHeight) {
        this(targetHeight, Color.web("#FF7A1A"), true);
    }

    public NyleLogo(double targetHeight, Color color, boolean glow) {
        SVGPath path = new SVGPath();
        path.setContent(PATH);
        path.setFill(color);

        double scale = targetHeight / VB_H;
        path.setScaleX(scale);
        path.setScaleY(scale);

        if (glow) {
            DropShadow g = new DropShadow();
            g.setColor(Color.web("#FF7A1A", 0.45));
            g.setRadius(targetHeight * 0.5);
            g.setSpread(0.08);
            path.setEffect(g);
        }

        getChildren().add(path);
    }
}
