package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.auth.Account;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Square (not round) avatar of the Minecraft skin head.
 * - Local steve_head.png baked in as an immediate, always-working fallback.
 * - For Microsoft accounts, the real skin loads async from mc-heads.net and overlays
 *   once fully fetched.
 */
public class SkinHead extends StackPane {

    private static final Logger LOG = LoggerFactory.getLogger(SkinHead.class);

    public SkinHead(Account account, double size) {
        setMinSize(size, size);
        setMaxSize(size, size);
        setPrefSize(size, size);

        // Square bg (slight rounding for a touch of refinement, not a circle)
        Rectangle bg = new Rectangle(size, size, Color.web("#1B1B21"));
        bg.setArcWidth(6);
        bg.setArcHeight(6);
        getChildren().add(bg);

        // Local Steve — always visible
        try {
            Image stevePng = new Image(
                    getClass().getResourceAsStream("/images/steve_head.png"),
                    size, size, true, false);
            ImageView local = new ImageView(stevePng);
            local.setFitWidth(size);
            local.setFitHeight(size);
            local.setPreserveRatio(true);
            local.setSmooth(false);
            getChildren().add(local);
        } catch (Exception e) {
            LOG.warn("Local steve head missing: {}", e.toString());
        }

        // Real skin for Microsoft accounts (async overlay)
        if (account != null && !account.isOffline()) {
            try {
                String id = account.uuid() != null ? account.uuid().replace("-", "") : account.username();
                String url = "https://mc-heads.net/avatar/" + id + "/" + (int) (size * 2);
                Image remote = new Image(url, size, size, true, false, true);
                ImageView remoteView = new ImageView(remote);
                remoteView.setFitWidth(size);
                remoteView.setFitHeight(size);
                remoteView.setPreserveRatio(true);
                remoteView.setSmooth(false);
                remote.progressProperty().addListener((o, a, b) -> {
                    if (b != null && b.doubleValue() >= 1.0 && !remote.isError()) {
                        getChildren().add(remoteView);
                    }
                });
            } catch (Exception e) {
                LOG.warn("Could not start remote skin load: {}", e.toString());
            }
        }
    }
}
