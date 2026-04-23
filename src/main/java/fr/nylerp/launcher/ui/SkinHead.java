package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.auth.Account;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Round avatar showing the Minecraft skin head.
 * - Microsoft account → real skin via mc-heads.net (UUID)
 * - Offline account    → local embedded Steve head (works without network)
 * Remote fetch is async; while it loads (or if it fails), the local Steve PNG is shown.
 */
public class SkinHead extends StackPane {

    private static final Logger LOG = LoggerFactory.getLogger(SkinHead.class);

    public SkinHead(Account account, double size) {
        setMinSize(size, size);
        setMaxSize(size, size);
        setPrefSize(size, size);

        // Clip to circle
        Circle clip = new Circle(size / 2, size / 2, size / 2);
        setClip(clip);

        // Background placeholder — dark disc in case nothing loads
        Rectangle bg = new Rectangle(size, size, Color.web("#1B1B21"));
        getChildren().add(bg);

        // Local Steve as base (always visible)
        ImageView local = new ImageView();
        try {
            Image stevePng = new Image(
                    getClass().getResourceAsStream("/images/steve_head.png"),
                    size, size, true, false);
            local.setImage(stevePng);
            local.setFitWidth(size);
            local.setFitHeight(size);
            local.setPreserveRatio(true);
            local.setSmooth(false);
            getChildren().add(local);
        } catch (Exception e) {
            LOG.warn("Local steve head missing: {}", e.toString());
        }

        // For Microsoft accounts, try to load the real skin asynchronously on top
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
                // Only overlay once fully loaded
                remote.progressProperty().addListener((o, a, b) -> {
                    if (b != null && b.doubleValue() >= 1.0 && !remote.isError()) {
                        getChildren().add(remoteView);
                    }
                });
                remote.errorProperty().addListener((o, a, b) -> {
                    if (Boolean.TRUE.equals(b)) {
                        LOG.warn("Remote skin failed, keeping Steve: {}", url);
                    }
                });
            } catch (Exception e) {
                LOG.warn("Could not start remote skin load: {}", e.toString());
            }
        }
    }
}
