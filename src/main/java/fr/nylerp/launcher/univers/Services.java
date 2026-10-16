package fr.nylerp.launcher.univers;

/**
 * Point de branchement unique des contrats de la refonte : l'intégration (lot L9) y pose les vraies
 * implémentations au démarrage ; l'interface lit toujours ces champs, jamais une implémentation.
 */
public final class Services {

    public static volatile SourceEtatPokenyle etatPokenyle = SourceEtatPokenyle.PAR_DEFAUT;
    public static volatile PreTelechargeur preTelechargeur = PreTelechargeur.AUCUN;

    private Services() { }
}
