package fr.nylerp.launcher.update.pretelechargement;

import fr.nylerp.launcher.config.AppPaths;
import fr.nylerp.launcher.update.ModpackUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Quand le pré-téléchargement AUTOMATIQUE doit se taire : le jeu tourne (on ne vole pas la bande
 * passante d'une partie en cours), une synchronisation normale est en route (elle fait déjà le
 * travail), ou la connexion est facturée au volume (partage de connexion d'un téléphone).
 */
final class Conditions {

    private static final Logger LOG = LoggerFactory.getLogger(Conditions.class);

    final BooleanSupplier jeuEnCours;
    final BooleanSupplier synchroEnCours;
    final BooleanSupplier connexionFacturee;

    Conditions(BooleanSupplier jeuEnCours, BooleanSupplier synchroEnCours, BooleanSupplier connexionFacturee) {
        this.jeuEnCours = jeuEnCours;
        this.synchroEnCours = synchroEnCours;
        this.connexionFacturee = connexionFacturee;
    }

    static Conditions systeme() {
        return new Conditions(memoriser(Conditions::jeuLanceParLeLauncher, 2_000),
                ModpackUpdater::syncEnCours,
                memoriser(Conditions::connexionFactureeLinux, 10 * 60_000));
    }

    static Conditions jamais() {
        return new Conditions(() -> false, () -> false, () -> false);
    }

    boolean doitAttendre() {
        return jeuEnCours.getAsBoolean() || synchroEnCours.getAsBoolean() || connexionFacturee.getAsBoolean();
    }

    /**
     * Un Minecraft lancé par le launcher tourne-t-il ? Le jeu s'exécute toujours avec le Java que le
     * launcher installe sous sa racine ({@code runtime/jdk-21}, voir {@code JavaRuntime}) : il suffit
     * de chercher un processus dont l'exécutable est là. {@code ProcessHandle.Info.command()} est
     * disponible sur Windows, macOS et Linux pour les processus de l'utilisateur.
     */
    static boolean jeuLanceParLeLauncher() {
        try {
            String runtime = AppPaths.rootDir().resolve("runtime").toAbsolutePath().normalize().toString();
            return ProcessHandle.allProcesses()
                    .anyMatch(p -> p.info().command().map(c -> Path.of(c).toAbsolutePath().normalize().toString()
                            .startsWith(runtime)).orElse(false));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Connexion facturée : détectée seulement là où c'est simple et sans fenêtre qui clignote —
     * NetworkManager sous Linux. Sous Windows, il faudrait PowerShell (une console qui s'ouvre en
     * fond à chaque vérification) ; macOS n'expose rien en Java. Inconnu = pas facturée.
     */
    static boolean connexionFactureeLinux() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) return false;
        String sortie = executer("busctl", "get-property", "org.freedesktop.NetworkManager",
                "/org/freedesktop/NetworkManager", "org.freedesktop.NetworkManager", "Metered");
        if (sortie != null) {
            // « u 1 » = oui, « u 3 » = probablement oui (NMMetered)
            String v = sortie.trim();
            return v.equals("u 1") || v.equals("u 3");
        }
        sortie = executer("nmcli", "-t", "-f", "GENERAL.METERED", "dev", "show");
        return sortie != null && sortie.toLowerCase(Locale.ROOT).contains("general.metered:yes");
    }

    private static String executer(String... commande) {
        try {
            Process p = new ProcessBuilder(commande).redirectErrorStream(true).start();
            // On attend D'ABORD (sortie de quelques lignes, bien sous la taille du tube) : lire
            // avant bloquerait indéfiniment sur une commande qui ne rend jamais la main.
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) sb.append(l).append('\n');
            }
            return p.exitValue() == 0 ? sb.toString() : null;
        } catch (Exception e) {
            LOG.debug("{} indisponible : {}", commande[0], e.toString());
            return null;
        }
    }

    /** Évite d'interroger le système à chaque bloc de 64 Ko. */
    private static BooleanSupplier memoriser(BooleanSupplier source, long dureeMs) {
        return new BooleanSupplier() {
            private long jusqua;
            private boolean valeur;

            @Override
            public synchronized boolean getAsBoolean() {
                long maintenant = System.currentTimeMillis();
                if (maintenant >= jusqua) {
                    valeur = source.getAsBoolean();
                    jusqua = maintenant + dureeMs;
                }
                return valeur;
            }
        };
    }
}
