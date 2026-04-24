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

        // Real skin for Microsoft accounts — try a chain of skin services so
        // we still show the right face when the primary one is down (saw
        // mc-heads.net return 503 in prod). We keep the first URL that loads
        // without error.
        if (account != null && !account.isOffline()) {
            String rawUuid = account.uuid() != null ? account.uuid().replace("-", "") : null;
            String dashedUuid = account.uuid();
            String name = account.username();
            int px = Math.max(16, (int) (size * 2));
            String[] urls = rawUuid != null
                    ? new String[]{
                            "https://cravatar.eu/helmavatar/" + dashedUuid + "/" + px + ".png",
                            "https://api.mineatar.io/face/" + dashedUuid + "?scale=8&overlay=true",
                            "https://mc-heads.net/avatar/" + rawUuid + "/" + px,
                            "https://minotar.net/helm/" + name + "/" + px + ".png"}
                    : new String[]{
                            "https://minotar.net/helm/" + name + "/" + px + ".png"};
            loadSkinFallback(urls, 0, size);
        }
    }

    private void loadSkinFallback(String[] urls, int idx, double size) {
        if (idx >= urls.length) return;
        try {
            Image remote = new Image(urls[idx], size, size, true, false, true);
            ImageView view = new ImageView(remote);
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setPreserveRatio(true);
            view.setSmooth(false);
            remote.errorProperty().addListener((o, a, b) -> {
                if (Boolean.TRUE.equals(b)) {
                    LOG.debug("Skin source {} failed, trying next", urls[idx]);
                    loadSkinFallback(urls, idx + 1, size);
                }
            });
            remote.progressProperty().addListener((o, a, b) -> {
                if (b != null && b.doubleValue() >= 1.0 && !remote.isError()
                        && !getChildren().contains(view)) {
                    getChildren().add(view);
                }
            });
        } catch (Exception e) {
            LOG.warn("Skin source {} threw: {}", urls[idx], e.toString());
            loadSkinFallback(urls, idx + 1, size);
        }
    }
}
