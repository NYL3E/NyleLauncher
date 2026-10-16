package fr.nylerp.launcher.update.pretelechargement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Télécharge UN fichier du pack dans la réserve, sous son empreinte : reprise d'un {@code .part}
 * existant (en-tête Range), empreinte SHA-256 vérifiée avant de lui donner son nom final, débit
 * plafonné, arrêt propre à la demande.
 *
 * <p>Ce n'est pas {@code util.Downloader} : celui-ci ne reprend pas un fichier commencé, ne se
 * plafonne pas et ne s'interrompt pas — trois choses indispensables à un téléchargement d'un
 * gigaoctet qui tourne en fond pendant que le joueur utilise sa connexion.
 */
final class TelechargeurFichier {

    private static final Logger LOG = LoggerFactory.getLogger(TelechargeurFichier.class);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    /** Silence maximal du serveur pendant le corps avant d'abandonner la tentative. */
    static Duration silenceMax = Duration.ofSeconds(60);
    /** Pauses entre les tentatives réseau (la reprise garde ce qui est déjà arrivé). */
    static long[] attentesMs = {2_000, 5_000, 15_000};

    private static final ScheduledExecutorService VEILLEUR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "nyle-pretelechargement-veilleur");
        t.setDaemon(true);
        return t;
    });

    /** Arrêt demandé (annulation, pause) : le {@code .part} est gardé pour une reprise. */
    static final class Interrompu extends IOException {
        Interrompu() { super("pré-téléchargement interrompu"); }
    }

    interface Suivi {
        /** Octets de CE fichier déjà présents dans la réserve (reprise comprise). */
        void progression(long octetsDuFichier);

        /** Interroger souvent : vrai = s'arrêter maintenant (le partiel est gardé). */
        boolean doitArreter();
    }

    private TelechargeurFichier() { }

    static void telecharger(String url, String sha, long tailleAttendue, Limiteur limiteur, Suivi suivi)
            throws IOException, InterruptedException {
        if (!Reserve.valide(sha)) throw new IOException("empreinte invalide : " + sha);
        if (Reserve.contient(sha)) {
            suivi.progression(Math.max(0, tailleAttendue));
            return;
        }
        Path partiel = Reserve.partiel(sha);
        boolean depuisZeroDejaTente = false;
        int erreursReseau = 0;
        while (true) {
            if (suivi.doitArreter()) throw new Interrompu();
            try {
                unEssai(url, partiel, tailleAttendue, limiteur, suivi);
            } catch (Interrompu e) {
                throw e;
            } catch (IOException e) {
                if (erreursReseau >= attentesMs.length) throw e;
                long attente = attentesMs[erreursReseau++];
                LOG.info("Pré-téléchargement : {} — nouvel essai dans {} ms ({})", partiel.getFileName(), attente, e.toString());
                Thread.sleep(attente);
                continue;
            }
            // Corps reçu en entier : on vérifie l'empreinte AVANT de donner le nom final.
            String reel = empreinte(partiel);
            if (sha.equalsIgnoreCase(reel)) {
                Path fin = Reserve.fichier(sha);
                try {
                    Files.move(partiel, fin, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(partiel, fin, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            }
            Files.deleteIfExists(partiel);
            suivi.progression(0);
            if (depuisZeroDejaTente) {
                throw new IOException("empreinte incorrecte pour " + url + " (attendu " + sha + ", reçu " + reel + ")");
            }
            LOG.warn("Pré-téléchargement : empreinte incorrecte pour {}, nouvel essai depuis zéro", url);
            depuisZeroDejaTente = true;
        }
    }

    /**
     * Une requête : reprend {@code partiel} s'il existe (un serveur qui ignore la reprise renvoie
     * tout, et le partiel repart de zéro). Revient normalement quand le corps est arrivé en entier ;
     * c'est ensuite l'empreinte qui tranche.
     */
    private static void unEssai(String url, Path partiel, long tailleAttendue, Limiteur limiteur, Suivi suivi)
            throws IOException, InterruptedException {
        long deja = Files.exists(partiel) ? Files.size(partiel) : 0;
        if (tailleAttendue > 0 && deja > tailleAttendue) {
            Files.delete(partiel);
            deja = 0;
        }
        if (tailleAttendue > 0 && deja == tailleAttendue) {
            return;  // déjà tout là
        }
        suivi.progression(deja);
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "NyleLauncher/pretelechargement")
                .GET();
        if (deja > 0) req.header("Range", "bytes=" + deja + "-");
        HttpResponse<InputStream> rep = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofInputStream());
        int code = rep.statusCode();
        boolean ajout;
        if (code == 206 && deja > 0) {
            String plage = rep.headers().firstValue("Content-Range").orElse("");
            if (!plage.startsWith("bytes " + deja + "-")) {
                rep.body().close();
                throw new IOException("reprise refusée par le serveur (Content-Range « " + plage + " »)");
            }
            ajout = true;
        } else if (code == 200) {
            ajout = false;
            if (deja > 0) LOG.info("Pré-téléchargement : le serveur ignore la reprise, {} repart de zéro", partiel.getFileName());
            deja = 0;
            suivi.progression(0);
        } else if (code == 416 && deja > 0) {
            rep.body().close();
            return;  // le partiel couvre déjà tout le fichier
        } else {
            rep.body().close();
            throw new IOException("HTTP " + code + " pour " + url);
        }

        AtomicLong derniereLecture = new AtomicLong(System.nanoTime());
        InputStream corps = rep.body();
        ScheduledFuture<?> veille = VEILLEUR.scheduleAtFixedRate(() -> {
            if (System.nanoTime() - derniereLecture.get() > silenceMax.toNanos()) {
                try { corps.close(); } catch (IOException ignored) { }
            }
        }, 1, 1, TimeUnit.SECONDS);
        StandardOpenOption[] options = ajout
                ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND}
                : new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE};
        long recus = deja;
        try (InputStream in = corps; OutputStream out = Files.newOutputStream(partiel, options)) {
            byte[] tampon = new byte[64 * 1024];
            int n;
            while ((n = in.read(tampon)) > 0) {
                derniereLecture.set(System.nanoTime());
                out.write(tampon, 0, n);
                recus += n;
                suivi.progression(recus);
                if (suivi.doitArreter()) throw new Interrompu();
                limiteur.prendre(n);
            }
        } finally {
            veille.cancel(false);
        }
        if (tailleAttendue > 0 && recus < tailleAttendue) {
            throw new IOException("corps incomplet (" + recus + " / " + tailleAttendue + " octets)");
        }
    }

    private static String empreinte(Path f) throws IOException {
        try (InputStream in = Files.newInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] tampon = new byte[64 * 1024];
            int n;
            while ((n = in.read(tampon)) > 0) md.update(tampon, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
