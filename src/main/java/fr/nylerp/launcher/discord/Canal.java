package fr.nylerp.launcher.discord;

import java.io.IOException;

/**
 * Le canal local vers l'application Discord : socket Unix (macOS, Linux) ou tube nommé (Windows).
 *
 * <p>Il n'est utilisé que par le fil de l'activité, qui l'ouvre, le lit, l'écrit et le ferme lui-même :
 * aucune lecture bloquante, donc aucune fermeture qui attendrait une lecture en cours.
 */
interface Canal extends AutoCloseable {

    /**
     * Lit ce qui est déjà arrivé, sans jamais attendre.
     *
     * @return le nombre d'octets lus, 0 si rien n'est arrivé
     * @throws java.io.EOFException si Discord a fermé le canal
     */
    int lire(byte[] b, int debut, int longueur) throws IOException;

    /** Écrit tout le tableau (une trame : quelques centaines d'octets). */
    void ecrire(byte[] b) throws IOException;

    /** Ferme le canal ; ne lève jamais d'exception, ne bloque pas. */
    @Override
    void close();

    /** Ouvre le canal désigné par {@code chemin} (tube Windows ou socket Unix selon sa forme). */
    static Canal ouvrir(String chemin) throws IOException {
        return CheminsIpc.estTubeWindows(chemin) ? CanalWindows.ouvrir(chemin) : CanalUnix.ouvrir(chemin);
    }
}
