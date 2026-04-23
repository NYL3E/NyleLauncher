package fr.nylerp.launcher.ui;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.util.Duration;

/** Tiny helpers for reusable, opinionated animations. */
public final class Animations {

    /** Fade + small rise — classic "stagger-in on mount". */
    public static void enter(Node n, double startDy, Duration delay, Duration dur) {
        n.setOpacity(0);
        n.setTranslateY(startDy);
        FadeTransition fade = new FadeTransition(dur, n);
        fade.setFromValue(0); fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(dur, n);
        slide.setFromY(startDy); slide.setToY(0);
        ParallelTransition par = new ParallelTransition(fade, slide);
        par.setDelay(delay);
        par.setInterpolator(Interpolator.SPLINE(0.16, 1, 0.3, 1)); // smooth apple curve
        par.play();
    }

    public static void enter(Node n, Duration delay) {
        enter(n, 14, delay, Duration.millis(650));
    }

    /** Scale-in for the logo / hero. */
    public static void pop(Node n, Duration delay) {
        n.setOpacity(0);
        n.setScaleX(0.88);
        n.setScaleY(0.88);
        FadeTransition fade = new FadeTransition(Duration.millis(700), n);
        fade.setFromValue(0); fade.setToValue(1);
        ScaleTransition sc = new ScaleTransition(Duration.millis(900), n);
        sc.setFromX(0.88); sc.setFromY(0.88);
        sc.setToX(1); sc.setToY(1);
        ParallelTransition par = new ParallelTransition(fade, sc);
        par.setDelay(delay);
        // JavaFX SPLINE requires all 4 control points in [0,1]. This one is a smooth fast-in / slow-out.
        par.setInterpolator(Interpolator.SPLINE(0.34, 0.92, 0.64, 1.0));
        par.play();
    }

    /** Gentle, continuous breathing for idle CTAs. */
    public static Timeline breathe(Node n, double fromScale, double toScale, Duration period) {
        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(n.scaleXProperty(), fromScale, Interpolator.EASE_BOTH),
                        new KeyValue(n.scaleYProperty(), fromScale, Interpolator.EASE_BOTH)),
                new KeyFrame(period.divide(2),
                        new KeyValue(n.scaleXProperty(), toScale, Interpolator.EASE_BOTH),
                        new KeyValue(n.scaleYProperty(), toScale, Interpolator.EASE_BOTH)),
                new KeyFrame(period,
                        new KeyValue(n.scaleXProperty(), fromScale, Interpolator.EASE_BOTH),
                        new KeyValue(n.scaleYProperty(), fromScale, Interpolator.EASE_BOTH))
        );
        tl.setCycleCount(Animation.INDEFINITE);
        tl.play();
        return tl;
    }

    /** Apply lift+glow on hover for a clickable node. */
    public static void hoverLift(Node n, double upBy) {
        n.setOnMouseEntered(e -> {
            TranslateTransition t = new TranslateTransition(Duration.millis(200), n);
            t.setToY(-upBy);
            t.setInterpolator(Interpolator.EASE_OUT);
            t.play();
        });
        n.setOnMouseExited(e -> {
            TranslateTransition t = new TranslateTransition(Duration.millis(220), n);
            t.setToY(0);
            t.setInterpolator(Interpolator.EASE_OUT);
            t.play();
        });
    }

    private Animations() {}
}
