package fr.nylerp.launcher.univers;

import fr.nylerp.launcher.auth.Account;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La règle « qui peut aller sur Pokényle » : ouverture générale, testeur distant, testeur historique, et personne d'autre. */
class AccesPokenyleTest {

    private static Account compte(String pseudo) {
        return new Account(Account.Type.OFFLINE, pseudo, "uuid-" + pseudo, null, null);
    }

    private static SourceEtatPokenyle source(boolean ouvert, Set<String> testeurs) {
        return new SourceEtatPokenyle() {
            @Override public boolean ouvertPour(String pseudo) {
                return ouvert || (pseudo != null && testeurs.contains(pseudo.toLowerCase()));
            }
            @Override public Optional<ZonedDateTime> ouverture() { return Optional.empty(); }
            @Override public String libelleOuverture() { return "mi-décembre"; }
            @Override public boolean crackAutorise() { return false; }
            @Override public String messageCrack() { return ""; }
            @Override public boolean discordActif() { return false; }
            @Override public String discordUrl() { return ""; }
            @Override public void surChangement(Runnable rappel) { }
        };
    }

    @AfterEach
    void rendre() {
        Services.etatPokenyle = SourceEtatPokenyle.PAR_DEFAUT;
    }

    @Test
    void personneNeEstAutoriseAvantLOuvertureSaufLesTesteurs() {
        Services.etatPokenyle = source(false, Set.of("testeur1"));
        assertFalse(AccesPokenyle.autorise(compte("Joueur")), "un joueur ordinaire est seulement prévenu");
        assertFalse(AccesPokenyle.autorise(compte("NYLERPTEST5")), "crack ou premium : même règle");
        assertTrue(AccesPokenyle.autorise(compte("Testeur1")), "testeur distant, casse ignorée");
        assertFalse(AccesPokenyle.autorise(null));
    }

    @Test
    void ouvertureGeneraleOuvreATous() {
        Services.etatPokenyle = source(true, Set.of());
        assertTrue(AccesPokenyle.autorise(compte("Joueur")));
        assertTrue(AccesPokenyle.autorise(compte("NYLERPTEST5")));
    }

    @Test
    void testeurHistoriqueGardeLAcces() {
        Services.etatPokenyle = source(false, Set.of());
        assertTrue(AccesPokenyle.autorise(compte("NYL3E")));
    }
}
