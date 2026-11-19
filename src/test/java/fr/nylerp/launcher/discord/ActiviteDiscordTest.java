package fr.nylerp.launcher.discord;

import fr.nylerp.launcher.univers.Services;
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
        SourceEtatPokenyle avant = Services.etatPokenyle;
        try {
            Services.etatPokenyle = source("987654321098765432");
            assertEquals("987654321098765432", ActiviteDiscord.identifiant());
            Services.etatPokenyle = source("n'importe quoi");
            assertEquals(ActiviteDiscord.ID_APPLICATION, ActiviteDiscord.identifiant(), "invalide : la constante");
            Services.etatPokenyle = SourceEtatPokenyle.PAR_DEFAUT;
            assertEquals(ActiviteDiscord.ID_APPLICATION, ActiviteDiscord.identifiant());
        } finally {
            Services.etatPokenyle = avant;
        }
    }

    private static SourceEtatPokenyle source(String id) {
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
        };
    }
}
