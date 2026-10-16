package fr.nylerp.launcher.ui.coquille;

import fr.nylerp.launcher.auth.Account;
import javafx.application.Platform;
import javafx.scene.image.Image;

import java.util.function.Consumer;

/**
 * Tête du skin d'un compte, avec la même chaîne de secours que {@code SkinHead} (cravatar, mineatar,
 * mc-heads, minotar) ; Steve tout de suite, puis la vraie tête dès qu'un service répond. Un compte
 * crack garde Steve (pas de skin Mojang à son nom).
 */
final class Tetes {

    private Tetes() { }

    static void charger(Account compte, Consumer<Image> surImage) {
        try {
            Image steve = new Image(Tetes.class.getResourceAsStream("/images/steve_head.png"), 64, 64, true, false);
            surImage.accept(steve);
        } catch (Throwable ignored) { }
        if (compte == null || compte.isOffline()) return;
        String brut = compte.uuid() != null ? compte.uuid().replace("-", "") : null;
        String uuid = compte.uuid();
        String nom = compte.username();
        String[] urls = brut != null
                ? new String[]{
                        "https://cravatar.eu/helmavatar/" + uuid + "/64.png",
                        "https://api.mineatar.io/face/" + uuid + "?scale=8&overlay=true",
                        "https://mc-heads.net/avatar/" + brut + "/64",
                        "https://minotar.net/helm/" + nom + "/64.png"}
                : new String[]{"https://minotar.net/helm/" + nom + "/64.png"};
        essayer(urls, 0, surImage);
    }

    private static void essayer(String[] urls, int i, Consumer<Image> surImage) {
        if (i >= urls.length) return;
        try {
            Image img = new Image(urls[i], 64, 64, true, false, true);
            img.errorProperty().addListener((o, a, b) -> { if (b) essayer(urls, i + 1, surImage); });
            img.progressProperty().addListener((o, a, b) -> {
                if (b.doubleValue() >= 1.0 && !img.isError()) Platform.runLater(() -> surImage.accept(img));
            });
        } catch (Throwable t) {
            essayer(urls, i + 1, surImage);
        }
    }
}
