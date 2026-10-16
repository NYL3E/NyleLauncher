package fr.nylerp.launcher.ui.coquille;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** La boutique s'ouvre sur le site de l'univers, dans le navigateur, une seule fois par clic (refonte 2026-10, lot L15). */
class LiensTest {

    private final List<String> ouvertes = new ArrayList<>();
    private Consumer<String> avant;

    @BeforeEach
    void installer() {
        avant = Liens.ouvreur;
        Liens.ouvreur = ouvertes::add;
        Liens.oublierDerniereBoutique();
    }

    @AfterEach
    void rendre() { Liens.ouvreur = avant; }

    @Test
    void chaqueUniversOuvreSaBoutiqueDuSite() throws Exception {
        Liens.boutique("pokenyle");
        assertEquals(List.of("https://pokerayou.academy/boutique/"), ouvertes);
        Liens.oublierDerniereBoutique();
        Liens.boutique("nylerp");
        assertEquals("https://www.nylerp.fr/boutique/", ouvertes.get(1));
    }

    @Test
    void unDoubleClicNOuvreQuUnSeulOnglet() {
        Liens.boutique("pokenyle");
        Liens.boutique("pokenyle");
        Liens.boutique("pokenyle");
        assertEquals(1, ouvertes.size());
    }

    @Test
    void unUniversInconnuRetombeSurNylerp() {
        assertEquals("https://www.nylerp.fr/boutique/", Liens.urlBoutique("autre"));
        assertEquals("https://www.nylerp.fr/boutique/", Liens.urlBoutique(null));
    }
}
