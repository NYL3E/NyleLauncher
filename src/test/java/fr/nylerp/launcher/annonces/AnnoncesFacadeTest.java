package fr.nylerp.launcher.annonces;

import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.univers.Services;
import fr.nylerp.launcher.univers.SourceEtatPokenyle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Façade : branchement de {@link Services#etatPokenyle}, cycle de démarrage, univers joués. */
@Timeout(60)
class AnnoncesFacadeTest {

    private String vraiHome;
    private FauxServeur serveur;
    private String universVuAvant;
    private String modeAvant;

    @BeforeEach
    void preparer() throws Exception {
        vraiHome = System.getProperty("user.home");
        System.setProperty("user.home", Files.createTempDirectory("nyle-annonces-facade").toString());
        serveur = new FauxServeur();
        System.setProperty("nyle.annonces.base", serveur.base());
        universVuAvant = Settings.get().universVu;
        modeAvant = Settings.get().modeDeJeu;
    }

    @AfterEach
    void nettoyer() {
        Annonces.arreter();
        Services.etatPokenyle = SourceEtatPokenyle.PAR_DEFAUT;
        System.clearProperty("nyle.annonces.base");
        System.setProperty("user.home", vraiHome);
        Settings.get().universVu = universVuAvant;
        Settings.get().modeDeJeu = modeAvant;
        serveur.close();
    }

    @Test
    void demarrerBrancheLaConfigDistanteEtLitLesDeuxPointsDAcces() throws Exception {
        serveur.pour("config").corps = "{\"v\":1,\"config\":{\"discordActif\":true,\"pokenyleOuvertureLibelle\":\"le 14 décembre\"}}";
        serveur.pour("config").date = ZonedDateTime.now(ZoneOffset.UTC);
        serveur.pour("annonces").corps = "{\"v\":1,\"annonces\":[]}";
        serveur.pour("annonces").date = ZonedDateTime.now(ZoneOffset.UTC);
        Annonces.demarrer();
        assertInstanceOf(ConfigDistante.class, Services.etatPokenyle, "branché dès le retour de demarrer()");
        Annonces.demarrer(); // second appel sans effet
        long fin = System.currentTimeMillis() + 10_000;
        while (!Services.etatPokenyle.discordActif() && System.currentTimeMillis() < fin) Thread.sleep(20);
        assertTrue(Services.etatPokenyle.discordActif());
        assertEquals("le 14 décembre", Services.etatPokenyle.libelleOuverture());
        while (serveur.requetes("annonces") == 0 && System.currentTimeMillis() < fin) Thread.sleep(20);
        assertEquals(1, serveur.requetes("config"));
        assertEquals(1, serveur.requetes("annonces"), "les deux points d'accès au même cycle");
        assertTrue(Annonces.maintenantServeur().isPresent(), "heure du serveur relevée sur l'en-tête Date");
    }

    @Test
    void universJouesReunitLesQuatreSignaux() throws Exception {
        Path racine = Files.createTempDirectory("nyle-racine");
        Settings.get().universVu = "";
        Settings.get().modeDeJeu = "nylerp";
        UniversJoues u = new UniversJoues(racine, racine.resolve("state/univers-joues.json"));
        assertEquals(Set.of(), u.lire(), "launcher neuf : le mode par défaut ne prouve rien");
        Files.createDirectories(racine.resolve("game/mods"));
        assertEquals(Set.of("nylerp"), u.lire(), "instance NyleRP d'avant la refonte");
        u.marquer("Pokenyle");
        assertEquals(Set.of("nylerp", "pokenyle"), new UniversJoues(racine, racine.resolve("state/univers-joues.json")).lire());
        Files.delete(racine.resolve("state/univers-joues.json"));
        Settings.get().modeDeJeu = "pokenyle";
        assertTrue(u.lire().contains("pokenyle"), "mode choisi autre que le défaut");
        Settings.get().modeDeJeu = "nylerp";
        Settings.get().universVu = "pokenyle";
        assertTrue(u.lire().contains("pokenyle"), "dernier univers ouvert dans la nouvelle interface");
    }

    @Test
    void liensOuvrablesHttpsSeulement() {
        assertTrue(Annonces.estUrl("https://pokerayou.academy/"));
        assertFalse(Annonces.estUrl("http://pokerayou.academy/"));
        assertFalse(Annonces.estUrl("file:///etc/passwd"));
        assertFalse(Annonces.estUrl("pokenyle"));
        assertFalse(Annonces.estUrl(null));
    }
}
