package fr.nylerp.launcher.ui;

import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * Thin golden divider — gradient line transparent → gold → transparent.
 * No glow (removed per user request).
 */
public class GoldDivider extends StackPane {

    public GoldDivider() {
        setPrefHeight(1);
        setMinHeight(1);
        setMaxHeight(1);
        setMouseTransparent(true);

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

        getChildren().add(line);
    }
}
