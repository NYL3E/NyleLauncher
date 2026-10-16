package fr.nylerp.launcher.ui.coquille;

import fr.nylerp.launcher.ui.univers.UniversNylerp;

import java.util.function.Consumer;

/** Liens externes de l'en-tête et des annonces : toujours le navigateur du système (jamais une fenêtre du launcher). */
public final class Liens {

    /** Boutique du site de chaque univers. */
    static final String BOUTIQUE_NYLERP = "https://www.nylerp.fr/boutique/";
    static final String BOUTIQUE_POKENYLE = "https://pokerayou.academy/boutique/";

    /** Deux ouvertures de la boutique à moins de 1,5 s d'écart (double-clic) n'en font qu'une : un clic = un onglet. */
    static final long ANTI_REBOND_NS = 1_500_000_000L;

    /** Ouvre une adresse dans le navigateur. Remplaçable par les essais, qui ne doivent pas ouvrir un vrai onglet. */
    static volatile Consumer<String> ouvreur = UniversNylerp::ouvrirNavigateur;
    private static long derniereBoutique = System.nanoTime() - 10 * ANTI_REBOND_NS;

    private Liens() { }

    static void ouvrir(String url) { ouvreur.accept(url); }

    /** Adresse de la boutique du site de l'univers (« pokenyle » ou, sinon, « nylerp »). */
    public static String urlBoutique(String univers) { return "pokenyle".equals(univers) ? BOUTIQUE_POKENYLE : BOUTIQUE_NYLERP; }

    /** Ouvre la boutique du site de l'univers dans le navigateur : un clic = un onglet. */
    public static synchronized void boutique(String univers) {
        long maintenant = System.nanoTime();
        if (maintenant - derniereBoutique < ANTI_REBOND_NS) return;
        derniereBoutique = maintenant;
        ouvrir(urlBoutique(univers));
    }

    /** Pour les essais : remet l'anti-rebond à zéro. */
    static synchronized void oublierDerniereBoutique() { derniereBoutique = System.nanoTime() - 10 * ANTI_REBOND_NS; }
}
