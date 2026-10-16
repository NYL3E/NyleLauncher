package fr.nylerp.launcher.ui.compte;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VerifPseudoTest {

    @Test
    void pseudoConnuDeMojangEstAchete() {
        assertEquals(VerifPseudo.Resultat.ACHETE,
                VerifPseudo.interpreter(200, "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}"));
    }

    @Test
    void pseudoInconnuEstLibre() {
        assertEquals(VerifPseudo.Resultat.LIBRE, VerifPseudo.interpreter(404, "{\"errorMessage\":\"Couldn't find any profile\"}"));
        assertEquals(VerifPseudo.Resultat.LIBRE, VerifPseudo.interpreter(204, ""));
    }

    @Test
    void panneOuLimiteNeBloquentJamais() {
        assertEquals(VerifPseudo.Resultat.INCONNU, VerifPseudo.interpreter(429, ""));
        assertEquals(VerifPseudo.Resultat.INCONNU, VerifPseudo.interpreter(503, "oops"));
        assertEquals(VerifPseudo.Resultat.INCONNU, VerifPseudo.interpreter(200, "page html inattendue"));
    }
}
