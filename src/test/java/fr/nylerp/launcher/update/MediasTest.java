package fr.nylerp.launcher.update;

import com.sun.net.httpserver.HttpServer;
import fr.nylerp.launcher.util.Hashing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La façade de production : démarrage HORS LIGNE puis retour du réseau sans redémarrer le
 * launcher (cas du mode --fond lancé à l'ouverture de session avant le Wi-Fi), et adaptateur
 * classpath des écrans existants.
 */
@Timeout(60)
class MediasTest {

    private String vraiHome;
    private HttpServer serveur;
    private long[] reprisesAvant;

    @BeforeEach
    void preparer() throws Exception {
        vraiHome = System.getProperty("user.home");
        System.setProperty("user.home", Files.createTempDirectory("nyle-medias-facade").toString());
        reprisesAvant = Medias.reprisesMs;
        Medias.reprisesMs = new long[]{300};
        Medias.reinitialiserPourTests();
    }

    @AfterEach
    void nettoyer() {
        Medias.reinitialiserPourTests();
        Medias.reprisesMs = reprisesAvant;
        System.clearProperty("nyle.medias.manifest");
        System.setProperty("user.home", vraiHome);
        if (serveur != null) serveur.stop(0);
    }

    @Test
    void demarreHorsLigneEtRecupereLesMediasQuandLeReseauRevient() throws Exception {
        byte[] musique = new byte[50_000];
        for (int i = 0; i < musique.length; i++) musique[i] = (byte) (i * 13);
        String sha = Hashing.sha256(musique);
        String asset = sha.substring(0, 12) + "-music.mp3";
        AtomicBoolean enLigne = new AtomicBoolean(false);

        serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + serveur.getAddress().getPort() + "/";
        String manifeste = "{\"format\":1,\"version\":\"1\",\"base\":\"" + base + "\",\"fichiers\":[{\"chemin\":\"nylerp/music.mp3\","
                + "\"sha256\":\"" + sha + "\",\"taille\":" + musique.length + ",\"asset\":\"" + asset + "\"}]}";
        serveur.createContext("/medias.json", ex -> {
            byte[] b = manifeste.getBytes(StandardCharsets.UTF_8);
            if (!enLigne.get()) { ex.sendResponseHeaders(503, -1); ex.close(); return; }
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream o = ex.getResponseBody()) { o.write(b); }
        });
        serveur.createContext("/" + asset, ex -> {
            ex.sendResponseHeaders(200, musique.length);
            try (OutputStream o = ex.getResponseBody()) { o.write(musique); }
        });
        serveur.start();
        System.setProperty("nyle.medias.manifest", base + "medias.json");

        CountDownLatch arrive = new CountDownLatch(1);
        Medias.quandDisponible(CheminsMedias.NYLERP_MUSIQUE, p -> arrive.countDown());   // démarre la façade
        assertNull(Medias.fichier(CheminsMedias.NYLERP_MUSIQUE), "hors ligne : rien");
        Thread.sleep(500);
        enLigne.set(true);                                                                // le réseau revient

        assertTrue(arrive.await(20, TimeUnit.SECONDS), "la reprise planifiée récupère le média sans redémarrage");
        Path p = Medias.fichier(CheminsMedias.NYLERP_MUSIQUE);
        assertNotNull(p);
        assertArrayEquals(musique, Files.readAllBytes(p));
        assertTrue(Medias.urlOuClasspath(CheminsMedias.NYLERP_MUSIQUE, "/media/music.mp3").startsWith("file:"));
    }

    @Test
    void adaptateurClasspath() {
        System.setProperty("nyle.medias.manifest", "http://127.0.0.1:9/medias.json");      // injoignable
        String video = Medias.urlOuClasspath(CheminsMedias.NYLERP_VIDEO_1, "/media/videolauncher1.mp4");
        assertNotNull(video, "sans paquet, la vidéo du jar est servie comme avant");
        assertTrue(video.contains("videolauncher1.mp4"));
        assertNull(Medias.urlOuClasspath(CheminsMedias.NYLERP_MUSIQUE, "/media/music.mp3"),
                "musique sortie du jar et pas encore téléchargée : null, l'écran reste muet sans planter");
        assertNull(Medias.image(CheminsMedias.POKENYLE_FOND_2X));
    }
}
