package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.config.ModeDeJeu;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * L'icône de la FENÊTRE du launcher (barre des tâches de Windows, sélecteur de fenêtres de Linux) suit l'univers : la
 * tête de Pikachu en Pokényle, le coffre de NyleRP sinon.
 *
 * <p>Ce que ce n'est PAS : l'icône de l'APPLICATION installée (Dock de macOS, raccourci et installateur Windows) vit dans
 * le paquet d'installation, qui est le MÊME pour les deux univers ; elle ne change pas avec cette classe.
 */
public final class IconeFenetre {

    private static final Logger LOG = LoggerFactory.getLogger(IconeFenetre.class);

    static final String RESSOURCE_NYLERP = "/images/app_icon.png";
    /** Pokényle : plusieurs tailles, chacune tirée de la source (le système choisit la plus proche : 16 et 32 pour la barre des
     *  tâches, 48 et 64 pour Alt-Tab, 256 pour les grands écrans). */
    static final int[] TAILLES_POKENYLE = {16, 32, 48, 64, 256};

    private IconeFenetre() { }

    static String ressourcePokenyle(int taille) {
        return "/images/univers/icone-fenetre-pokenyle-" + taille + ".png";
    }

    /** Les ressources d'icône de l'univers. */
    static List<String> ressources(ModeDeJeu mode) {
        if (mode != ModeDeJeu.POKENYLE) return List.of(RESSOURCE_NYLERP);
        List<String> l = new ArrayList<>();
        for (int t : TAILLES_POKENYLE) l.add(ressourcePokenyle(t));
        return l;
    }

    /** Pose l'icône de l'univers courant, puis la change à chaque bascule d'univers. */
    public static void suivre(Stage stage) {
        appliquer(stage, ModeDeJeu.courant());
        ModeDeJeu.surChangement(mode -> Platform.runLater(() -> appliquer(stage, mode)));
    }

    private static void appliquer(Stage stage, ModeDeJeu mode) {
        List<Image> images = new ArrayList<>();
        for (String r : ressources(mode)) {
            try (InputStream in = IconeFenetre.class.getResourceAsStream(r)) {
                if (in == null) {
                    LOG.warn("Icône de fenêtre introuvable : {}", r);
                    continue;
                }
                images.add(new Image(in));
            } catch (Exception e) {
                LOG.warn("Icône de fenêtre impossible ({}) : {}", r, e.toString());
            }
        }
        if (!images.isEmpty()) stage.getIcons().setAll(images);
    }
}
