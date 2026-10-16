package fr.nylerp.launcher.fond;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

import java.nio.file.Path;

/**
 * Valeur {@code HKCU\Software\Microsoft\Windows\CurrentVersion\Run\<nom>} = {@code "<exe>" --fond}.
 *
 * <p>Pas de raccourci dans le dossier Démarrage : après une désinstallation, un raccourci orphelin
 * ouvre une boîte d'erreur, alors qu'une valeur {@code Run} dont l'exécutable manque est ignorée
 * en silence. Si le joueur désactive l'entrée dans le Gestionnaire des tâches (onglet Démarrage),
 * Windows l'écrit sous {@code StartupApproved}, que l'on ne touche jamais : son choix est respecté.
 */
final class DemarrageAutoWindows extends DemarrageAuto {

    static final String CLE = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";

    private final String nom;

    DemarrageAutoWindows(String nom) {
        this.nom = nom;
    }

    @Override
    public boolean estActif() {
        return Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, CLE, nom);
    }

    @Override
    public void activer(Path executable) {
        String valeur = commande(executable);
        if (estActif() && valeur.equals(Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, CLE, nom))) {
            return;
        }
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, CLE, nom, valeur);
    }

    @Override
    public void desactiver() {
        if (estActif()) Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, CLE, nom);
    }

    static String commande(Path executable) {
        return "\"" + executable.toAbsolutePath() + "\" " + Fond.ARG_FOND;
    }
}
