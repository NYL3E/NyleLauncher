package fr.nylerp.launcher.update;

import fr.nylerp.launcher.config.AppPaths;
import fr.nylerp.launcher.config.Constants;
import fr.nylerp.launcher.config.ModeDeJeu;
import fr.nylerp.launcher.config.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EN MODE POKÉNYLE, LA LISTE MULTIJOUEUR DOIT PORTER LA BONNE ADRESSE.
 *
 * <p>La connexion automatique au lancement suffit tant que tout va bien. Mais si elle échoue —
 * serveur en cours de redémarrage, réseau qui hoquette — le joueur retombe sur le menu
 * multijoueur, et celui-ci doit lui offrir une entrée cliquable plutôt qu'une liste vide où il
 * n'aurait plus qu'à retaper une adresse IP de mémoire.
 */
class ListeServeursModeTest {

    private String vraiHome;

    @BeforeEach
    void avant() throws Exception {
        vraiHome = System.getProperty("user.home");
        System.setProperty("user.home", Files.createTempDirectory("nyle-liste-test").toString());
    }

    @AfterEach
    void apres() {
        System.setProperty("user.home", vraiHome);
        Settings.get().modeDeJeu = "nylerp";
    }

    @Test
    void enPokenyleLaListeContientLAdresseDuServeur() throws Exception {
        ModeDeJeu.definir(ModeDeJeu.POKENYLE);
        ServerListSanitizer.sweep();

        Path liste = AppPaths.gameDir().resolve("servers.dat");
        assertTrue(Files.exists(liste), "servers.dat doit être écrit en mode pokényle");

        String brut = new String(Files.readAllBytes(liste), StandardCharsets.UTF_8);
        String attendu = Constants.serverHost() + ":" + Constants.serverPort();
        assertTrue(brut.contains(attendu),
                "la liste doit porter l'adresse du serveur pokényle (" + attendu + ")");
        assertTrue(brut.contains(ModeDeJeu.POKENYLE.titre),
                "l'entrée doit être nommée pour qu'on la reconnaisse dans le menu");
        assertFalse(brut.contains("play.nylerp.fr"),
                "pokényle ne passe pas par le proxy : l'adresse du RP n'a rien à faire ici");
    }

    @Test
    void enNylerpOnNeTouchePasALaListeDuJoueur() throws Exception {
        Settings.get().modeDeJeu = "nylerp";
        // Le marqueur de reset est posé : on simule une install déjà nettoyée, donc le balayage
        // ne doit RIEN écrire — la liste de NyleRP vient du pack, pas d'ici.
        Files.writeString(AppPaths.launcherState().resolve("serverlist_reset_v2"), "done");
        Path liste = AppPaths.gameDir().resolve("servers.dat");
        Files.deleteIfExists(liste);

        ServerListSanitizer.sweep();

        assertFalse(Files.exists(liste),
                "en NyleRP, le balayage ne fabrique pas de liste : elle est livrée par le pack");
    }
}
