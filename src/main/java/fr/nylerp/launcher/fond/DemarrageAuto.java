package fr.nylerp.launcher.fond;

import fr.nylerp.launcher.config.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;

/**
 * Lancement du launcher, en fond ({@link Fond#ARG_FOND}), à l'ouverture de session.
 *
 * <p>Une implémentation par OS : clé {@code Run} du registre (Windows), LaunchAgent (macOS),
 * fichier {@code .desktop} d'autostart (Linux). Toutes visent l'exécutable INSTALLÉ, celui que
 * jpackage expose dans {@code jpackage.app-path} ; un lancement de développement ({@code java}
 * nu), un DMG monté ou une app « translocalisée » par Gatekeeper n'inscrivent rien, puisque ce
 * chemin n'existera plus à la prochaine session.
 *
 * <p>Désinstallation : aucune des trois entrées ne dépend d'un code à nous pour échouer
 * proprement. Windows ignore une valeur {@code Run} dont l'exécutable a disparu, launchd journalise
 * l'échec de {@code open -a} sans rien afficher, et le bureau Linux saute un {@code Exec}
 * introuvable. « Tout supprimer » dans les réglages appelle {@link #desactiver()}.
 */
public abstract class DemarrageAuto {

    private static final Logger LOG = LoggerFactory.getLogger(DemarrageAuto.class);

    /** Identifiant de l'entrée, distinct par canal pour qu'un launcher DEV et un PROD cohabitent. */
    static String identifiant() {
        return Constants.DEV ? "fr.nylerp.launcher.dev" : "fr.nylerp.launcher";
    }

    /** L'entrée de démarrage existe-t-elle (quelle que soit sa cible) ? */
    public abstract boolean estActif();

    /** Crée ou met à jour l'entrée vers {@code executable}. N'écrit rien si elle est déjà juste. */
    public abstract void activer(Path executable) throws IOException;

    /** Retire l'entrée. Sans effet si elle n'existe pas. */
    public abstract void desactiver() throws IOException;

    /** L'implémentation du système courant. */
    public static DemarrageAuto pourCeSysteme() {
        String home = System.getProperty("user.home");
        if (Systeme.windows()) return new DemarrageAutoWindows(Constants.APP_NAME);
        if (Systeme.mac()) return new DemarrageAutoMac(Paths.get(home), identifiant());
        String xdg = System.getenv("XDG_CONFIG_HOME");
        Path config = (xdg != null && !xdg.isBlank()) ? Paths.get(xdg) : Paths.get(home, ".config");
        return new DemarrageAutoLinux(config, Constants.DEV ? "nylelauncher-dev" : "nylelauncher",
                Constants.APP_NAME);
    }

    /**
     * Aligne l'entrée sur le réglage, en tâche de fond (le registre ou le disque peuvent être lents).
     * Appelé au démarrage et à chaque changement de réglage.
     */
    public static void synchroniser(boolean voulu) {
        Thread t = new Thread(() -> {
            try {
                DemarrageAuto d = pourCeSysteme();
                if (!voulu) {
                    if (d.estActif()) {
                        d.desactiver();
                        LOG.info("Fond : démarrage automatique retiré");
                    }
                    return;
                }
                Path exe = executableInstalle();
                if (exe == null) {
                    LOG.info("Fond : pas d'exécutable installé (lancement de développement ou DMG) — démarrage automatique non inscrit");
                    return;
                }
                d.activer(exe);
            } catch (Throwable e) {
                LOG.warn("Fond : synchronisation du démarrage automatique impossible : {}", e.toString());
            }
        }, "nyle-demarrage-auto");
        t.setDaemon(true);
        t.start();
    }

    /** Chemin de l'exécutable installé, ou {@code null} s'il ne doit pas être inscrit. */
    static Path executableInstalle() {
        String jp = System.getProperty("jpackage.app-path");
        Path candidat = null;
        if (jp != null && !jp.isBlank()) {
            candidat = Paths.get(jp);
        } else {
            Optional<String> cmd = ProcessHandle.current().info().command();
            if (cmd.isPresent()) {
                Path p = Paths.get(cmd.get());
                String nom = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!(nom.equals("java") || nom.equals("java.exe")
                        || nom.equals("javaw") || nom.equals("javaw.exe"))) {
                    candidat = p;
                }
            }
        }
        if (candidat == null || !cheminInscriptible(candidat)) return null;
        return Files.isRegularFile(candidat) ? candidat.toAbsolutePath() : null;
    }

    /** Refuse les emplacements qui n'existeront plus à la prochaine session. */
    static boolean cheminInscriptible(Path exe) {
        String s = exe.toAbsolutePath().toString().replace('\\', '/');
        return !(s.startsWith("/Volumes/") || s.contains("/AppTranslocation/"));
    }
}
