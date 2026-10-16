package fr.nylerp.launcher.ui.kit;

/**
 * États visuels d'un composant du kit. En usage normal ils suivent la souris (survol, appui) et {@code disable} ; la
 * vitrine les FIGE avec {@link Apercu#apercu(Etat)} pour comparer chaque état côte à côte avec le site.
 */
public enum Etat {
    NORMAL, SURVOL, PRESSE, DESACTIVE;

    /** Composant dont on peut figer l'état (vitrine, captures). */
    public interface Apercu {
        void apercu(Etat etat);
    }
}
