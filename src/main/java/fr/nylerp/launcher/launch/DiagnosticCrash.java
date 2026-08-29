package fr.nylerp.launcher.launch;

import java.util.List;
import java.util.Locale;

/**
 * Traduire un plantage du jeu en phrase qu'un joueur peut suivre.
 *
 * <h2>Pourquoi</h2>
 * Quand Minecraft s'arrête dans les premières secondes, le launcher n'avait à offrir que le
 * journal brut : soixante lignes de pile d'appels dans une barre d'état. Personne ne peut rien
 * en faire — ni la joueuse qui veut jouer, ni celui qui l'aide à distance et n'a qu'une photo
 * d'écran. Or les pannes de démarrage se comptent sur les doigts d'une main : mémoire, pilote
 * graphique, java abîmé, mod incompatible, dossier verrouillé.
 *
 * <p>On les reconnaît donc à la source et l'on dit QUOI FAIRE. Le journal complet reste écrit
 * sur le disque et joint au rapport : rien n'est perdu, on cesse simplement de le jeter au
 * visage de quelqu'un qui voulait cliquer sur JOUER.
 */
public final class DiagnosticCrash {

    private DiagnosticCrash() {}

    /** Un motif reconnu : ce qu'on cherche dans le journal, ce qu'on répond. */
    private record Panne(List<String> indices, String conseil) {}

    /**
     * L'ordre COMPTE : du plus précis au plus général. Un plantage de pilote graphique mentionne
     * souvent « OpenGL » ET « EXCEPTION_ACCESS_VIOLATION » ; la cause utile est la première.
     */
    private static final List<Panne> PANNES = List.of(
            new Panne(List.of("could not reserve enough space", "unable to allocate",
                    "failed to allocate memory", "insufficient memory"),
                    "Pas assez de mémoire pour lancer le jeu. Ouvre les réglages du launcher et "
                    + "baisse la RAM allouée (4 Go suffisent), puis referme les autres "
                    + "applications avant de relancer."),
            new Panne(List.of("outofmemoryerror", "java heap space", "metaspace"),
                    "Le jeu a manqué de mémoire. Augmente la RAM dans les réglages du launcher "
                    + "si ton PC le permet, sinon baisse la distance d'affichage en jeu."),
            new Panne(List.of("no lwjgl", "unsatisfiedlinkerror", "failed to load library",
                    "glfw", "failed to create window", "opengl", "pixel format",
                    "driver does not appear to support opengl"),
                    "Ta carte graphique a refusé de démarrer le jeu. Mets à jour ton pilote "
                    + "graphique (NVIDIA, AMD ou Intel), puis relance — c'est la cause la plus "
                    + "fréquente de ce message."),
            new Panne(List.of("exception_access_violation", "a fatal error has been detected",
                    "sigsegv", "hs_err_pid"),
                    "Le jeu s'est arrêté brutalement, presque toujours à cause du pilote "
                    + "graphique. Mets-le à jour, et si tu utilises un logiciel d'overlay "
                    + "(Discord, MSI Afterburner, RivaTuner), désactive-le le temps d'un essai."),
            new Panne(List.of("unsupportedclassversionerror", "has been compiled by a more recent",
                    "error: could not find or load main class", "invalid or corrupt jarfile"),
                    "L'installation du jeu est abîmée. Ferme le launcher, relance-le : il "
                    + "retéléchargera les fichiers manquants tout seul."),
            new Panne(List.of("access is denied", "accès refusé", "acces refuse",
                    "the process cannot access the file", "used by another process",
                    "processus ne peut pas accéder"),
                    "Un fichier du jeu est bloqué — antivirus, ou une partie déjà ouverte. "
                    + "Ferme toutes les fenêtres de Minecraft, autorise le dossier NyleLauncher "
                    + "dans ton antivirus, puis relance."),
            new Panne(List.of("mixin apply failed", "incompatible mod set", "mod resolution",
                    "requires version", "duplicate mod", "modresolutionexception"),
                    "Un mod ne s'entend pas avec les autres. Relance le launcher : il remet le "
                    + "pack en état. Si le message revient, préviens le staff sur Discord."),
            new Panne(List.of("could not create the java virtual machine",
                    "unrecognized option", "error occurred during initialization of vm"),
                    "Le moteur Java a refusé les réglages. Remets la RAM sur une valeur "
                    + "standard dans les réglages du launcher (4 ou 6 Go) et relance."));

    /**
     * Le conseil qui correspond à ce journal, ou {@code null} si rien n'est reconnu.
     *
     * <p>Seules les premières lignes comptent : c'est là que le jeu dit pourquoi il s'arrête, et
     * une pile d'appels de trois cents lignes plus bas ne parle que des conséquences.
     */
    public static String conseil(String journal, int code) {
        if (journal == null || journal.isBlank()) {
            return "Le jeu s'est fermé aussitôt (code " + code + ") sans rien écrire. "
                    + "Relance le launcher ; si ça recommence, envoie un rapport au staff.";
        }
        String bas = journal.toLowerCase(Locale.ROOT);
        for (Panne p : PANNES) {
            for (String indice : p.indices()) {
                if (bas.contains(indice)) return p.conseil();
            }
        }
        return null;
    }

    /**
     * La ligne la plus parlante du journal — pour le staff, quand aucun motif ne colle.
     *
     * <p>On cherche une exception ou une erreur, et l'on rend UNE ligne : de quoi reconnaître la
     * panne sur une photo d'écran, sans noyer la barre d'état.
     */
    public static String ligneParlante(String journal) {
        if (journal == null) return "";
        String meilleure = "";
        for (String ligne : journal.split("\r?\n")) {
            String l = ligne.trim();
            if (l.isEmpty() || l.startsWith("at ")) continue;
            String bas = l.toLowerCase(Locale.ROOT);
            if (bas.contains("exception") || bas.contains("error") || bas.contains("caused by")
                    || bas.contains("fatal")) {
                meilleure = l.length() > 160 ? l.substring(0, 160) + "…" : l;
                break;
            }
        }
        return meilleure;
    }
}
