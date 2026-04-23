package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.auth.Account;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * Round avatar showing the player's Minecraft skin head.
 * - Microsoft account → by UUID → real skin from Mojang (via mc-heads.net)
 * - Offline account    → MHF_Steve (the vanilla default)
 */
public class SkinHead extends StackPane {

    public SkinHead(Account account, double size) {
        setMinSize(size, size);
        setMaxSize(size, size);
        setPrefSize(size, size);

        String url;
        if (account == null || account.isOffline()) {
            url = "https://mc-heads.net/avatar/MHF_Steve/" + (int) (size * 2);
        } else {
            // Mojang UUID (dashed or raw both work with mc-heads)
            String id = account.uuid() != null ? account.uuid().replace("-", "") : account.username();
            url = "https://mc-heads.net/avatar/" + id + "/" + (int) (size * 2);
        }

        try {
            Image img = new Image(url, size, size, true, false, true);  // async, smooth off (pixelated)
            ImageView view = new ImageView(img);
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setPreserveRatio(true);
            view.setSmooth(false); // preserve pixel-art look

            Circle clip = new Circle(size / 2, size / 2, size / 2);
            view.setClip(clip);

            Circle bg = new Circle(size / 2);
            bg.setFill(Color.web("#1B1B21"));

            getChildren().addAll(bg, view);
        } catch (Exception e) {
            // Fallback: solid orange disc
            Circle fallback = new Circle(size / 2);
            fallback.setFill(Color.web("#FF6A1A"));
            getChildren().add(fallback);
        }
    }
}
