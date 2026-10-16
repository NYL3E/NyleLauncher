package fr.nylerp.launcher.fond;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.function.Consumer;

/**
 * Un seul launcher par session et par canal.
 *
 * <p>Le premier processus verrouille {@code instance.lock} (verrou du système, libéré même si le
 * processus est tué) et écoute sur {@code 127.0.0.1:<port libre>} ; le port et un jeton aléatoire
 * sont écrits dans {@code instance.port}. Un second lancement (double-clic, démarrage automatique
 * en double) trouve le verrou pris, envoie son message au premier, qui réveille sa fenêtre, puis
 * s'arrête sans jamais ouvrir d'interface.
 *
 * <p>Si le premier ne répond pas (processus figé), le second démarre quand même, sans garde : un
 * launcher en double vaut mieux qu'un launcher qui refuse de s'ouvrir.
 */
final class InstanceUnique {

    private static final Logger LOG = LoggerFactory.getLogger(InstanceUnique.class);
    private static final int DELAI_MS = 2500;

    enum Resultat {
        /** Ce processus est le launcher de la session ; il reçoit les messages des suivants. */
        PREMIERE,
        /** Un launcher tournait déjà et a reçu le message : ce processus doit s'arrêter. */
        RELAYEE,
        /** Impossible de trancher : on démarre normalement, sans garde. */
        SANS_GARDE
    }

    private final Path dossier;
    private FileChannel canal;
    private FileLock verrou;
    private volatile ServerSocket serveur;
    private Resultat resultat = Resultat.SANS_GARDE;

    InstanceUnique(Path dossier) {
        this.dossier = dossier;
    }

    Resultat resultat() { return resultat; }

    /**
     * @param message   ce que ce lancement veut dire au premier (« OUVRIR », « FOND »…)
     * @param recepteur appelé sur un fil réseau pour chaque message reçu, si on est le premier
     */
    Resultat acquerir(String message, Consumer<String> recepteur) {
        Path lock = dossier.resolve("instance.lock");
        Path port = dossier.resolve("instance.port");
        try {
            Files.createDirectories(dossier);
            canal = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                verrou = canal.tryLock();
            } catch (OverlappingFileLockException deja) {
                verrou = null; // tenu par ce même processus (tests) : se comporter comme un second
            }
            if (verrou != null) {
                ecouter(port, recepteur);
                return resultat = Resultat.PREMIERE;
            }
            fermerCanal();
            return resultat = (envoyer(port, message) ? Resultat.RELAYEE : Resultat.SANS_GARDE);
        } catch (Throwable t) {
            LOG.warn("Fond : garde d'instance unique indisponible ({}) — démarrage normal", t.toString());
            fermer();
            return resultat = Resultat.SANS_GARDE;
        }
    }

    private void ecouter(Path port, Consumer<String> recepteur) throws IOException {
        serveur = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        String jeton = HexFormat.of().formatHex(b);
        Path tmp = port.resolveSibling("instance.port.tmp");
        Files.writeString(tmp, serveur.getLocalPort() + " " + jeton, StandardCharsets.UTF_8);
        Files.move(tmp, port, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Thread t = new Thread(() -> boucle(jeton, recepteur), "nyle-instance-unique");
        t.setDaemon(true);
        t.start();
    }

    private void boucle(String jeton, Consumer<String> recepteur) {
        while (serveur != null && !serveur.isClosed()) {
            try (Socket s = serveur.accept()) {
                s.setSoTimeout(DELAI_MS);
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                String ligne = in.readLine();
                if (ligne == null || !ligne.startsWith(jeton + " ")) continue;
                OutputStream out = s.getOutputStream();
                out.write("OK\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                String msg = ligne.substring(jeton.length() + 1);
                try { recepteur.accept(msg); } catch (Throwable e) { LOG.warn("Fond : message « {} » : {}", msg, e.toString()); }
            } catch (IOException e) {
                if (serveur == null || serveur.isClosed()) return;
            }
        }
    }

    private static boolean envoyer(Path port, String message) {
        try {
            String[] p = Files.readString(port, StandardCharsets.UTF_8).trim().split(" ", 2);
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), Integer.parseInt(p[0])), DELAI_MS);
                s.setSoTimeout(DELAI_MS);
                s.getOutputStream().write((p[1] + " " + message + "\n").getBytes(StandardCharsets.UTF_8));
                s.getOutputStream().flush();
                String r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)).readLine();
                return "OK".equals(r);
            }
        } catch (Throwable t) {
            LOG.warn("Fond : le launcher déjà ouvert ne répond pas ({}) — démarrage sans garde", t.toString());
            return false;
        }
    }

    private void fermerCanal() {
        try { if (canal != null) canal.close(); } catch (IOException ignored) { }
        canal = null;
    }

    void fermer() {
        try { if (serveur != null) serveur.close(); } catch (IOException ignored) { }
        serveur = null;
        try { if (verrou != null) verrou.release(); } catch (IOException ignored) { }
        verrou = null;
        fermerCanal();
    }
}
