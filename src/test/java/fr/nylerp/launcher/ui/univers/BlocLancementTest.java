package fr.nylerp.launcher.ui.univers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Les libellés du lanceur (en capitales, hérités de l'ancien bouton) passent à la casse du site. */
class BlocLancementTest {

    @Test
    void libellesDuLanceur() {
        assertEquals("Jouer", BlocLancement.casse("JOUER"));
        assertEquals("Mettre à jour", BlocLancement.casse("METTRE À JOUR"));
        assertEquals("Mise à jour…", BlocLancement.casse("MISE À JOUR…"));
        assertEquals("En cours", BlocLancement.casse("EN COURS"));
        assertEquals("Réessayer", BlocLancement.casse("RÉESSAYER"));
        assertEquals("42%", BlocLancement.casse("42%"));
        assertEquals("Jouer", BlocLancement.casse(null));
    }
}
