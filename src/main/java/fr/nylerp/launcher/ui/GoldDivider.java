package fr.nylerp.launcher.ui;

import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;

/**
 * Golden divider with a soft diffuse glow — ports the supplied SVG into JavaFX.
 *
 * Structure (bottom-to-top):
 *   1) Large blurred gold ellipse below the baseline (the 50%-opacity glow)
 *   2) 1-pixel horizontal gradient line (transparent → gold → transparent)
 *
 * The block is 40px tall. The line sits at the bottom; the ellipse extends above
 * into the container so the glow reads as coming from the line itself.
 */
public class GoldDivider extends StackPane {

    public GoldDivider() {
        setPrefHeight(36);
        setMinHeight(36);
        setMaxHeight(36);
        setMouseTransparent(true);

        // Blurred gold ellipse — diffused halo
        Ellipse glow = new Ellipse();
        glow.setFill(Color.web("#FBB552", 0.45));
        glow.radiusXProperty().bind(widthProperty().multiply(0.34));
        glow.radiusYProperty().bind(heightProperty().multiply(2.6));
        glow.setEffect(new GaussianBlur(40));
        glow.setManaged(false);
        // Center horizontally, position vertically so the ellipse's top edge kisses the line
        glow.layoutXProperty().bind(widthProperty().divide(2));
        glow.layoutYProperty().bind(heightProperty().subtract(0));

        Pane glowPane = new Pane(glow);
        glowPane.setMouseTransparent(true);

        // The 1px gold gradient line
        Region line = new Region();
        line.setPrefHeight(1);
        line.setMinHeight(1);
        line.setMaxHeight(1);
        line.setStyle(
                "-fx-background-color: linear-gradient(to right, " +
                "rgba(255,160,27,0) 0%, " +
                "rgba(255,184,85,1) 50%, " +
                "rgba(255,160,27,0) 100%);");
        line.setMaxWidth(Double.MAX_VALUE);
        StackPane.setAlignment(line, javafx.geometry.Pos.BOTTOM_CENTER);

        getChildren().addAll(glowPane, line);
    }
}
