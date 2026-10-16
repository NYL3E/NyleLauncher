package fr.nylerp.launcher.fond;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;

/**
 * Aperçu hors launcher d'une carte d'annonce, rendue en PNG (×2) sur fond clair puis sombre :
 * {@code java -cp <payload.jar>:build/classes/java/test fr.nylerp.launcher.fond.ApercuToast\$Lanceur sortie.png}.
 * Ce n'est pas un test automatique : c'est l'outil qui permet de juger la carte sans attendre une annonce.
 */
public class ApercuToast extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        for (String f : new String[] { "Montserrat-Regular.ttf", "Montserrat-Bold.ttf" }) {
            Font.loadFont(ApercuToast.class.getResourceAsStream("/fonts/" + f), 14);
        }
        String sortie = getParameters().getRaw().get(0);
        String[][] fonds = { { "clair", "#EAF7FF" }, { "sombre", "#1E2230" } };
        for (String[] fond : fonds) {
            StackPane racine = new StackPane(Toast.contenu("Pokényle ouvre demain à 18 h",
                    "Le pack est déjà téléchargé : un clic et tu es en jeu dès l'ouverture.", stage, null));
            racine.setPadding(new Insets(24));
            racine.setStyle("-fx-background-color: " + fond[1] + ";");
            Scene s = new Scene(racine);
            s.setFill(Color.web(fond[1]));
            stage.setScene(s);
            racine.applyCss();
            racine.layout();
            SnapshotParameters p = new SnapshotParameters();
            p.setTransform(new Scale(2, 2));
            var img = racine.snapshot(p, null);
            ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", new File(sortie.replace(".png", "-" + fond[0] + ".png")));
        }
        Platform.exit();
    }

    /** Point d'entrée hors sous-classe d'Application (le lanceur java refuse sinon un JavaFX hors module). */
    public static final class Lanceur {
        public static void main(String[] args) {
            Application.launch(ApercuToast.class, args);
        }
    }
}
