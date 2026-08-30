package fr.nylerp.launcher.config;

import fr.nylerp.launcher.auth.Account;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LE SÉLECTEUR DE MODE, sous ses deux angles critiques.
 *
 * <p>Le premier est une question de discrétion : le bouton est un outil de test personnel, et
 * un joueur ordinaire ne doit pas même soupçonner son existence. Le second est une question de
 * dégâts : deux univers qui partageraient un dossier de mods se détruiraient l'un l'autre, et
 * un joueur qui reçoit cette mise à jour sans jamais toucher au sélecteur doit retrouver son
 * instance NyleRP exactement là où elle était — pas un octet à retélécharger.
 */
class ModeDeJeuTest {

    private String vraiHome;
    private Path homeTemporaire;

    @BeforeEach
    void avant() throws Exception {
        vraiHome = System.getProperty("user.home");
        homeTemporaire = Files.createTempDirectory("nyle-mode-test");
        System.setProperty("user.home", homeTemporaire.toString());
        Settings.get().modeDeJeu = "nylerp";
    }

    @AfterEach
    void apres() {
        System.setProperty("user.home", vraiHome);
        Settings.get().modeDeJeu = "nylerp";
    }

    // ── Discrétion ────────────────────────────────────────────────────────────────────────

    @Test
    void leSelecteurNExistePasPourUnJoueurOrdinaire() {
        for (String pseudo : new String[]{"Valucks", "Kqzah_", "Darkousse", "nyl3e_", "NYL3EE", ""}) {
            Account compte = new Account(Account.Type.MICROSOFT, pseudo, "u", null, null);
            assertFalse(fr.nylerp.launcher.ui.SelecteurMode.estAutorise(compte),
                    "« " + pseudo + " » ne doit pas avoir accès au sélecteur");
            assertNull(fr.nylerp.launcher.ui.SelecteurMode.pour(compte, () -> {}),
                    "aucun composant ne doit être construit pour « " + pseudo + " »");
        }
    }

    @Test
    void leSelecteurNExistePasSansCompte() {
        assertFalse(fr.nylerp.launcher.ui.SelecteurMode.estAutorise(null));
        assertFalse(fr.nylerp.launcher.ui.SelecteurMode.estAutorise(
                new Account(Account.Type.OFFLINE, null, "u", null, null)));
        assertNull(fr.nylerp.launcher.ui.SelecteurMode.pour(null, () -> {}));
    }

    @Test
    void leSelecteurEstAutorisePourLOwner() {
        // La casse et les espaces ne doivent pas décider : le pseudo est comparé proprement.
        // On éprouve la RÈGLE et non la construction du composant — dessiner exigerait un
        // toolkit graphique, dont l'absence prouve déjà, dans les tests ci-dessus, qu'un
        // joueur non autorisé ne fait rien instancier du tout.
        for (String pseudo : new String[]{"NYL3E", "nyl3e", " NYL3E "}) {
            assertTrue(fr.nylerp.launcher.ui.SelecteurMode.estAutorise(
                            new Account(Account.Type.MICROSOFT, pseudo, "u", null, null)),
                    "le sélecteur doit être autorisé pour « " + pseudo + " »");
        }
    }

    // ── Dégâts ────────────────────────────────────────────────────────────────────────────

    @Test
    void parDefautOnEstSurNylerpEtDansLeDossierHistorique() {
        assertEquals(ModeDeJeu.NYLERP, ModeDeJeu.courant());
        assertEquals("game", ModeDeJeu.NYLERP.dossier,
                "l'instance NyleRP doit rester dans game/ : c'est celle de tous les launchers déjà installés");
        assertTrue(AppPaths.gameDir().endsWith("game"));
        assertTrue(AppPaths.manifestCache().endsWith("manifest.json"),
                "le manifeste NyleRP garde son nom historique");
    }

    @Test
    void unModeInconnuRamenneSurNylerp() {
        Settings.get().modeDeJeu = "un-mode-qui-nexiste-pas";
        assertEquals(ModeDeJeu.NYLERP, ModeDeJeu.courant());
        Settings.get().modeDeJeu = null;
        assertEquals(ModeDeJeu.NYLERP, ModeDeJeu.courant());
    }

    @Test
    void lesDeuxUniversNePartagentNiDossierNiManifesteNiPack() {
        Path jeuNylerp = AppPaths.gameDir();
        Path manifNylerp = AppPaths.manifestCache();
        String packNylerp = Constants.packTag();

        ModeDeJeu.definir(ModeDeJeu.POKENYLE);
        assertEquals(ModeDeJeu.POKENYLE, ModeDeJeu.courant());

        assertNotEquals(jeuNylerp, AppPaths.gameDir(),
                "pokényle doit avoir SON dossier de jeu, sinon les deux packs s'écrasent");
        assertNotEquals(manifNylerp, AppPaths.manifestCache(),
                "un manifeste partagé ferait croire l'instance à jour après une bascule");
        assertNotEquals(packNylerp, Constants.packTag(),
                "chaque mode tire son propre pack");
        assertTrue(Constants.manifestUrl().contains("pack-pokenyle"));

        // …et le retour en arrière ramène EXACTEMENT là où on était.
        ModeDeJeu.definir(ModeDeJeu.NYLERP);
        assertEquals(jeuNylerp, AppPaths.gameDir());
        assertEquals(manifNylerp, AppPaths.manifestCache());
        assertEquals(packNylerp, Constants.packTag());
    }

    @Test
    void leChoixEstEcritSurLeDisque() throws Exception {
        ModeDeJeu.definir(ModeDeJeu.POKENYLE);
        // On lit le FICHIER, pas l'objet en mémoire : c'est lui que le launcher relira au
        // prochain démarrage, et c'est donc lui qui prouve que le choix survit à la fermeture.
        String json = Files.readString(AppPaths.settingsFile());
        assertTrue(json.contains("\"modeDeJeu\""), "le champ doit être sérialisé");
        assertTrue(json.contains("pokenyle"), "le mode choisi doit être celui écrit");
    }
}
