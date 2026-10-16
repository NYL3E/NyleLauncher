package fr.nylerp.launcher.univers;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.auth.AccountStore;
import fr.nylerp.launcher.ui.SelecteurMode;

/**
 * Règle unique « ce compte peut-il aller sur Pokényle maintenant ? », partagée par l'interface (« Jouer » ou « Me
 * prévenir ») et par le pré-téléchargement (qui n'est réservé qu'à ceux qui peuvent déjà jouer).
 *
 * <p>Vrai pour : l'ouverture générale (config distante), un testeur de la liste distante, ou un testeur historique
 * de {@link SelecteurMode}. Faux pour tous les autres : ils sont seulement prévenus de l'ouverture.
 *
 * <p>C'est un blocage d'interface et de comportement du launcher, pas un secret : le pack de Pokényle est sur une
 * release publique.
 */
public final class AccesPokenyle {

    private AccesPokenyle() { }

    public static boolean autorise(Account compte) {
        if (compte == null || compte.username() == null) return false;
        return Services.etatPokenyle.ouvertPour(compte.username()) || SelecteurMode.estAutorise(compte);
    }

    /** Le compte ACTIF du launcher (celui qu'on a choisi dans le menu des comptes). */
    public static boolean compteActifAutorise() {
        return autorise(AccountStore.active());
    }
}
