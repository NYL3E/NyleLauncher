package fr.nylerp.launcher.ui;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/**
 * The NyleRP angular "N" logo.
 * White by default on dark backgrounds; no shadows or glow.
 * Source path supplied by the user.
 */
public class NyleLogo extends Group {

    private static final String PATH =
            "M106.792 4.56L77.224 66H61.864L50.856 25.04L27.432 73.68L4.648 89.04L37.928 19.92H30.248L22.312 4.56H60.712L71.72 45.52L91.432 4.56H106.792Z";
    private static final double VB_W = 113;
    private static final double VB_H = 92;

    public NyleLogo(double targetHeight) {
        this(targetHeight, Color.WHITE);
    }

    public NyleLogo(double targetHeight, Color color) {
        SVGPath path = new SVGPath();
        path.setContent(PATH);
        path.setFill(color);
        double scale = targetHeight / VB_H;
        path.setScaleX(scale);
        path.setScaleY(scale);
        getChildren().add(path);
    }
}
