package fr.nylerp.launcher.ui;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * 8-cube ring loading indicator for the Play button. Cubes sit on the
 * 8 outer cells of a 3×3 grid (centre is empty), and a phase-offset
 * Gaussian wave runs around the ring scaling each cube up/down so the
 * motion reads as a fluid pulse traveling clockwise — a clear visual
 * "something is happening" while Minecraft launches or runs.
 *
 * <p>Implementation: a single {@link Timeline} drives a 0→1 progress
 * property over {@value #CYCLE_MS} ms. Every change ticks all 8 cubes
 * through {@code scale = 1 + amplitude · gaussian(phase_offset)}. The
 * Gaussian peaks at exactly one cube at a time and fades smoothly to
 * the neighbours, which keeps the highlight reading as a moving point
 * rather than a flicker.
 *
 * <p>Costs nothing when {@link #stop()} is called — Timeline runs only
 * while {@code start()} is active and the listener is short-circuited
 * by JavaFX when the property doesn't change.
 */
public final class CubeSpinner extends Pane {

    private static final int    CUBES         = 8;
    private static final double CYCLE_MS      = 1100.0;   // one full lap
    private static final double SCALE_PEAK    = 2.10;     // max scale at peak
    private static final double SIGMA         = 0.090;    // Gaussian width

    /** Clockwise layout of cubes around the 3×3 grid (origin top-left).
     *  Indices map to the cells:
     *  <pre>
     *  0 1 2
     *  7 . 3
     *  6 5 4
     *  </pre>
     *  This produces an aesthetically pleasing clockwise wave starting
     *  from the top-left cell. */
    private static final int[][] CELLS = {
        {0,0}, {1,0}, {2,0},   // top row L→R
        {2,1},                   // right middle
        {2,2}, {1,2}, {0,2},   // bottom row R→L
        {0,1}                    // left middle
    };

    private final Rectangle[] cubes = new Rectangle[CUBES];
    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private final Timeline timeline;

    /**
     * @param sizePx total width/height of the spinner control. Cubes are
     *               sized so they fit comfortably within a 3-cell grid
     *               of {@code sizePx/3} cells with a small inner gap.
     */
    public CubeSpinner(double sizePx) {
        setPrefSize(sizePx, sizePx);
        setMinSize(sizePx, sizePx);
        setMaxSize(sizePx, sizePx);

        final double cellSize  = sizePx / 3.0;
        final double cubeSide  = cellSize * 0.45;        // base side
        final double half      = cubeSide / 2.0;

        for (int i = 0; i < CUBES; i++) {
            int col = CELLS[i][0];
            int row = CELLS[i][1];
            double cx = col * cellSize + cellSize / 2.0;
            double cy = row * cellSize + cellSize / 2.0;
            Rectangle r = new Rectangle(cubeSide, cubeSide, Color.WHITE);
            r.setArcWidth(2);
            r.setArcHeight(2);
            // Pin to the cube centre so scaleX/Y grows symmetrically.
            r.setLayoutX(cx - half);
            r.setLayoutY(cy - half);
            cubes[i] = r;
            getChildren().add(r);
        }

        // Each cube animates as a phase-shifted Gaussian over [0, 1).
        // At any progress p, the cube at phase φ has scale
        //   1 + (peak-1) · exp(- ((p-φ) mod 1)² / (2σ²))
        // The (mod 1) wrap means the wave doesn't pop when progress
        // crosses the 1→0 boundary; it just continues around the ring.
        progress.addListener((obs, oldV, newV) -> {
            double p = newV.doubleValue();
            for (int i = 0; i < CUBES; i++) {
                double phase = (double) i / CUBES;
                double delta = wrapShortest(p - phase);
                double bump  = Math.exp(-(delta * delta) / (2.0 * SIGMA * SIGMA));
                double scale = 1.0 + (SCALE_PEAK - 1.0) * bump;
                cubes[i].setScaleX(scale);
                cubes[i].setScaleY(scale);
            }
        });

        timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(progress, 0.0, Interpolator.LINEAR)),
                new KeyFrame(Duration.millis(CYCLE_MS),
                        new KeyValue(progress, 1.0, Interpolator.LINEAR)));
        timeline.setCycleCount(Animation.INDEFINITE);
        setVisible(false);
        setManaged(false);
    }

    /** Returns the shortest signed distance between {@code v} and 0 on
     *  the unit circle. Maps {@code v ∈ ℝ} into {@code [-0.5, 0.5]} so
     *  the Gaussian peak straddles the 1→0 wrap cleanly. */
    private static double wrapShortest(double v) {
        double w = v - Math.floor(v);     // [0, 1)
        return w > 0.5 ? w - 1.0 : w;     // [-0.5, 0.5]
    }

    public void start() {
        setVisible(true);
        setManaged(true);
        if (timeline.getStatus() != Animation.Status.RUNNING) timeline.play();
    }

    public void stop() {
        if (timeline.getStatus() == Animation.Status.RUNNING) timeline.stop();
        setVisible(false);
        setManaged(false);
        // Reset cube scale so re-show starts from base.
        for (Rectangle r : cubes) { r.setScaleX(1.0); r.setScaleY(1.0); }
    }
}
