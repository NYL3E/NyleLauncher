package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.ui.univers.UniversNylerp;

/**
 * Ancien écran d'accueil — refonte 2026-10 (lot L5) : l'écran est remplacé par la coquille
 * ({@code ui/coquille}) et les univers ({@code ui/univers}), le lancement vit dans
 * {@code jeu/LanceurJeu}. Il ne reste ici que les points d'entrée statiques que d'autres écrans
 * appellent encore (les curseurs de volume des paramètres), pour ne rien casser.
 */
public final class MainView {

    private MainView() { }

    /** Volume d'ambiance en direct (curseur des paramètres) — aussi l'audio du clip secret. */
    public static void setLiveAmbientVolume(double v) { UniversNylerp.setLiveAmbientVolume(v); }

    /** Volume de la musique en direct (curseur des paramètres). */
    public static void setLiveMusicVolume(double v) { UniversNylerp.setLiveMusicVolume(v); }
}
