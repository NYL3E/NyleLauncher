package fr.nylerp.launcher.univers;

/**
 * Contrat partagé de la refonte 2026-10 : pré-téléchargement du pack d'un univers (« pokenyle »,
 * « nylerp ») pour que « Jouer » soit immédiat. Implémenté par le lot L8, lu par l'interface (L5).
 */
public interface PreTelechargeur {

    enum Etat { AUCUN, EN_COURS, PRET, ERREUR }

    Etat etat(String univers);

    /** Avancement entre 0 et 1 (0 si inconnu). */
    double progression(String univers);

    /** Taille totale à télécharger en octets, ou -1 si inconnue. */
    long octetsTotal(String univers);

    void demarrer(String univers);

    void annuler(String univers);

    /** Rappel appelé (sur n'importe quel fil) quand l'état ou l'avancement change. */
    void surChangement(Runnable rappel);

    PreTelechargeur AUCUN = new PreTelechargeur() {
        @Override public Etat etat(String univers) { return Etat.AUCUN; }
        @Override public double progression(String univers) { return 0; }
        @Override public long octetsTotal(String univers) { return -1; }
        @Override public void demarrer(String univers) { }
        @Override public void annuler(String univers) { }
        @Override public void surChangement(Runnable rappel) { }
    };
}
