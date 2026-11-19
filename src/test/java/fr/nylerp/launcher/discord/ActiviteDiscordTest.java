package fr.nylerp.launcher.discord;

import fr.nylerp.launcher.univers.SourceEtatPokenyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Ce qui est propre au launcher : identifiant venu de la config distante, silence quand le mod du jeu est là. */
class ActiviteDiscordTest {

    @Test
    void modDuJeuDetecteDansLeDossierMods(@TempDir Path mods) throws Exception {
        assertFalse(ActiviteDiscord.modPresenceDans(mods), "dossier vide : le launcher garde « En jeu »");
        Files.writeString(mods.resolve("nylepresence-0.1.0.jar.disabled"), "");
        Files.writeString(mods.resolve("nyleconnexion-0.1.1.jar"), "");
        assertFalse(ActiviteDiscord.modPresenceDans(mods), "seul un jar nylepresence-*.jar compte");
        Files.writeString(mods.resolve("nylepresence-0.1.0.jar"), "");
        assertTrue(ActiviteDiscord.modPresenceDans(mods), "le mod du jeu tient l'activité");
        assertFalse(ActiviteDiscord.modPresenceDans(mods.resolve("absent")), "dossier absent : faux, sans exception");
    }

    @Test
    void identifiantDeLaConfigDistantePrioritaire() {
        assertEquals("987654321098765432", ActiviteDiscord.identifiantPour(source("987654321098765432", true, null), "x"));
        String constante = ClientPresence.idValide(ActiviteDiscord.ID_APPLICATION) == null ? "" : ActiviteDiscord.ID_APPLICATION;
        assertEquals(constante, ActiviteDiscord.identifiantPour(source("n'importe quoi", true, null), "x"),
                "identifiant distant invalide : la constante du launcher (vide = inerte)");
        assertEquals("", ActiviteDiscord.identifiantPour(SourceEtatPokenyle.PAR_DEFAUT, "quelquun"),
                "sans config : ni identifiant distant, ni testeur");
    }

    @Test
    void activationProgressiveTesteursPuisTous() {
        String id = "987654321098765432";
        SourceEtatPokenyle testeurs = source(id, false, "nyl3e");
        assertEquals(id, ActiviteDiscord.identifiantPour(testeurs, "NYL3E"), "un testeur : connecté");
        assertEquals("", ActiviteDiscord.identifiantPour(testeurs, "joueur"), "un autre joueur : aucune connexion");
        assertEquals("", ActiviteDiscord.identifiantPour(testeurs, null), "sans compte : aucune connexion");
        SourceEtatPokenyle tous = source(id, true, "nyl3e");
        assertEquals(id, ActiviteDiscord.identifiantPour(tous, "joueur"), "discordPour = tous : tout le monde");
        assertEquals(id, ActiviteDiscord.identifiantPour(tous, null));
    }

    private static SourceEtatPokenyle source(String id, boolean pourTous, String testeur) {
        return new SourceEtatPokenyle() {
            @Override public boolean ouvertPour(String pseudo) { return false; }
            @Override public Optional<ZonedDateTime> ouverture() { return Optional.empty(); }
            @Override public String libelleOuverture() { return ""; }
            @Override public boolean crackAutorise() { return false; }
            @Override public String messageCrack() { return ""; }
            @Override public boolean discordActif() { return false; }
            @Override public String discordUrl() { return ""; }
            @Override public void surChangement(Runnable rappel) { }
            @Override public String discordAppId() { return id; }
            @Override public boolean discordPourTous() { return pourTous; }
            @Override public boolean estTesteur(String pseudo) { return pseudo != null && pseudo.equalsIgnoreCase(testeur); }
        };
    }
}
