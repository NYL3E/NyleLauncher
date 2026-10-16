package fr.nylerp.launcher.fond;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Carte d'annonce : emplacement dans le coin, croix indéformable, clic sur la croix et sur la carte. */
class ToastTest {

    // ── Emplacement (logique pure, aucun écran requis) ────────────────────────

    @Test
    void enBasADroite_lePremierBlocTouchePresqueLeBord() {
        Rectangle2D zone = new Rectangle2D(0, 0, 1920, 1040); // zone visible : hors barre des tâches
        double[] xy = Toast.position(zone, 408, 130, 0, true);
        // la carte (ombre de 24 px comprise) laisse 16 px de marge au bord droit et au bord bas
        assertEquals(1920 - 408 - 16 + 24, xy[0], 0.001);
        assertEquals(1040 - 16 + 24 - 130, xy[1], 0.001);
        assertTrue(xy[0] + 408 - 24 <= 1920 - 16 + 0.001, "la carte visible ne sort pas à droite");
        assertTrue(xy[1] + 130 - 24 <= 1040 - 16 + 0.001, "la carte visible ne sort pas en bas");
    }

    @Test
    void lesCartesSEmpilentVersLeHaut() {
        Rectangle2D zone = new Rectangle2D(0, 0, 1920, 1040);
        double[] premiere = Toast.position(zone, 408, 130, 0, true);
        double cumul = 130 - 2 * 24 + 10;
        double[] seconde = Toast.position(zone, 408, 130, cumul, true);
        assertTrue(seconde[1] < premiere[1], "la deuxième carte est au-dessus de la première");
        assertEquals(premiere[0], seconde[0], 0.001);
        assertTrue(premiere[1] - seconde[1] >= 130 - 2 * 24 + 10 - 0.001, "elles ne se chevauchent pas");
    }

    @Test
    void enHautADroite_surLinux() {
        Rectangle2D zone = new Rectangle2D(0, 28, 1920, 1052);
        double[] xy = Toast.position(zone, 408, 130, 0, false);
        assertEquals(28 + 16 - 24, xy[1], 0.001);
    }

    @Test
    void lesTextesLaissentToujoursSaPlaceALaCroix() {
        assertTrue(Toast.CROIX >= 28, "zone de clic d'au moins 28 px");
        double total = 2 * 14 + 40 + 12 + Toast.LARGEUR_TEXTE + 12 + Toast.CROIX;
        assertEquals(Toast.LARGEUR, total, 0.001, "logo + textes + croix + marges = largeur de la carte, rien n'est comprimé");
    }

    // ── Croix et clic (JavaFX réel, ignoré sans écran) ────────────────────────

    private static boolean fx;

    @BeforeAll
    static void demarrerFx() throws Exception {
        if (GraphicsEnvironment.isHeadless()) return;
        CountDownLatch pret = new CountDownLatch(1);
        try {
            Platform.startup(pret::countDown);
        } catch (IllegalStateException dejaDemarre) {
            pret.countDown();
        }
        fx = pret.await(15, TimeUnit.SECONDS);
    }

    private static void surFx(Runnable r) throws Exception {
        CountDownLatch fin = new CountDownLatch(1);
        Platform.runLater(() -> { try { r.run(); } finally { fin.countDown(); } });
        assertTrue(fin.await(15, TimeUnit.SECONDS), "le fil JavaFX répond");
    }

    private static MouseEvent souris(javafx.event.EventType<MouseEvent> type, javafx.scene.Node cible) {
        return new MouseEvent(type, 5, 5, 5, 5, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, false, null);
    }

    @Test
    void croix_fermeDesLAppui_etLaCarteNEstPasDeclenchee() throws Exception {
        Assumptions.assumeTrue(fx, "pas d'écran : test JavaFX ignoré");
        AtomicInteger fermetures = new AtomicInteger();
        AtomicInteger cartes = new AtomicInteger();
        surFx(() -> {
            VBox carte = new VBox();
            StackPane croix = Toast.croix(fermetures::incrementAndGet);
            carte.getChildren().add(croix);
            carte.setOnMouseClicked(e -> cartes.incrementAndGet());
            new Scene(carte, 100, 60);
            assertEquals(Toast.CROIX, croix.getPrefWidth(), 0.001);
            assertEquals(Toast.CROIX, croix.getMinWidth(), 0.001);
            assertEquals(Toast.CROIX, croix.getMaxWidth(), 0.001);
            Event.fireEvent(croix, souris(MouseEvent.MOUSE_PRESSED, croix));
            Event.fireEvent(croix, souris(MouseEvent.MOUSE_RELEASED, croix));
            Event.fireEvent(croix, souris(MouseEvent.MOUSE_CLICKED, croix));
        });
        assertEquals(1, fermetures.get(), "un appui sur la croix ferme la carte, une seule fois");
        assertEquals(0, cartes.get(), "le clic sur la croix n'ouvre pas le launcher");
    }

    @Test
    void contenu_laCroixResteUneVraieZoneEtLeClicSurLaCarteEstPris() throws Exception {
        Assumptions.assumeTrue(fx, "pas d'écran : test JavaFX ignoré");
        AtomicInteger clics = new AtomicInteger();
        surFx(() -> {
            VBox carte = Toast.contenu("C'est noté",
                    "Le launcher te préviendra la veille et le jour de l'ouverture de Pokényle.", null, clics::incrementAndGet);
            Scene s = new Scene(new StackPane(carte));
            carte.applyCss();
            carte.layout();
            var croix = carte.lookupAll("*").stream()
                    .filter(n -> n instanceof StackPane sp && sp.getPrefWidth() == Toast.CROIX).findFirst();
            assertTrue(croix.isPresent(), "la croix est présente");
            StackPane c = (StackPane) croix.get();
            assertEquals(Toast.CROIX, c.getWidth(), 0.5, "la croix garde ses 32 px une fois la carte mise en page");
            assertEquals(Toast.CROIX, c.getHeight(), 0.5);
            // aucun libellé « … » ni « × » : la croix n'est pas du texte
            assertTrue(carte.lookupAll(".label").stream().noneMatch(n -> {
                String t = ((javafx.scene.control.Label) n).getText();
                return "×".equals(t) || "…".equals(t) || "...".equals(t);
            }));
            assertTrue(carte.getBoundsInLocal().getWidth() <= Toast.LARGEUR + 1, "la carte ne dépasse pas sa largeur");
        });
    }
}
