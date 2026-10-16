package fr.nylerp.launcher.ui.coquille;

import fr.nylerp.launcher.ui.univers.UniversNylerp;

/** Liens externes de l'en-tête (navigateur du système). */
final class Liens {

    /** Boutique du site de l'univers affiché (repli tant que la boutique intégrée n'est pas branchée). */
    static final String BOUTIQUE_NYLERP = "https://www.nylerp.fr";
    static final String BOUTIQUE_POKENYLE = "https://pokerayou.academy/boutique/";

    private Liens() { }

    static void ouvrir(String url) { UniversNylerp.ouvrirNavigateur(url); }

    static void boutique(String univers) { ouvrir("pokenyle".equals(univers) ? BOUTIQUE_POKENYLE : BOUTIQUE_NYLERP); }
}
