package fr.nylerp.launcher.ui.univers;

import fr.nylerp.launcher.update.Medias;
import javafx.application.Platform;
import javafx.scene.image.Image;

import java.net.URL;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Images du paquet de médias distant, avec un repli embarqué.
 *
 * <p>Au tout premier démarrage après la mise à jour, le paquet n'est pas encore téléchargé : l'écran
 * reçoit d'abord la petite version embarquée dans le jar (si elle existe), puis la version 2x dès
 * qu'elle arrive en cache. Le rappel est donc appelé UNE OU DEUX fois, toujours sur le fil JavaFX, et
 * jamais avec une image en erreur.
 */
public final class ImagesDistantes {

    private ImagesDistantes() { }

    /**
     * @param chemin        chemin logique du média ({@code CheminsMedias})
     * @param repliClasspath ressource du jar affichée en attendant (ou {@code null})
     * @param largeur       largeur de décodage voulue (0 = taille d'origine)
     * @param hauteur       hauteur de décodage voulue (0 = taille d'origine)
     */
    public static void charger(String chemin, String repliClasspath, double largeur, double hauteur, Consumer<Image> surImage) {
        Path p = Medias.fichier(chemin);
        if (p != null) {
            decoder(p.toUri().toString(), largeur, hauteur, surImage);
            return;
        }
        if (repliClasspath != null) {
            URL r = ImagesDistantes.class.getResource(repliClasspath);
            if (r != null) decoder(r.toExternalForm(), largeur, hauteur, surImage);
        }
        Medias.quandDisponible(chemin, arrive -> Platform.runLater(
                () -> decoder(arrive.toUri().toString(), largeur, hauteur, surImage)));
    }

    /** Image du jar seule, décodée tout de suite (petites images vitales : logos de l'en-tête). */
    public static Image classpath(String ressource, double largeur, double hauteur) {
        URL r = ImagesDistantes.class.getResource(ressource);
        if (r == null) return null;
        Image i = new Image(r.toExternalForm(), largeur, hauteur, true, true, false);
        return i.isError() ? null : i;
    }

    private static void decoder(String url, double largeur, double hauteur, Consumer<Image> surImage) {
        try {
            Image img = new Image(url, Math.max(0, largeur), Math.max(0, hauteur), true, true, true);
            Runnable livrer = () -> { if (!img.isError()) surImage.accept(img); };
            if (img.getProgress() >= 1.0) {
                if (Platform.isFxApplicationThread()) livrer.run(); else Platform.runLater(livrer);
            } else {
                img.progressProperty().addListener((o, a, b) -> {
                    if (b.doubleValue() >= 1.0) Platform.runLater(livrer);
                });
            }
        } catch (Throwable t) {
            System.err.println("[ImagesDistantes] " + url + " : " + t);
        }
    }
}
