package fr.nylerp.launcher.util;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de résilience réseau du Downloader (owner 2026-07-11 : « le launcher doit être extrêmement
 * fluide et n'avoir aucun bug — chaque correction doit répondre à des tests précis »).
 *
 * <p>Reproduit les DEUX pannes réelles du CDN GitHub Releases des 2026-07-10/11 :
 * <ul>
 *   <li><b>CDN qui goutte</b> : en-têtes rapides puis corps à quelques octets/s — l'ancien code
 *       restait bloqué À L'INFINI (« Téléchargement du manifest… » en boucle) car
 *       {@code HttpRequest.timeout} ne borne pas la lecture du corps ;</li>
 *   <li><b>CDN mort</b> : connexion refusée/timeout — l'échec doit être rapide et propre.</li>
 * </ul>
 */
class DownloaderResilienceTest {

    private static HttpServer server;
    private static int port;
    private static int deadPort;

    @BeforeAll
    static void up() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "test-http");
            t.setDaemon(true);
            return t;
        }));

        // CDN sain : 350 Ko instantanés.
        byte[] healthy = "x".repeat(350_000).getBytes(StandardCharsets.UTF_8);
        server.createContext("/healthy", ex -> {
            ex.sendResponseHeaders(200, healthy.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(healthy); }
        });

        // CDN qui goutte : en-têtes immédiats, puis 64 octets toutes les 500 ms « pour toujours »
        // (annonce 10 Mo — à ce débit il faudrait ~22 h : sans deadline murale, blocage infini).
        server.createContext("/trickle", ex -> {
            ex.sendResponseHeaders(200, 10_000_000);
            try (OutputStream os = ex.getResponseBody()) {
                byte[] chunk = new byte[64];
                while (true) {
                    os.write(chunk);
                    os.flush();
                    Thread.sleep(500);
                }
            } catch (Exception ignored) {
                // le client a coupé (deadline atteinte) — fin normale du test
            }
        });

        server.createContext("/missing", ex -> {
            ex.sendResponseHeaders(404, -1);
            ex.close();
        });

        server.start();

        // Un port fermé garanti : on l'ouvre pour connaître un port libre, puis on le ferme.
        try (ServerSocket s = new ServerSocket(0)) {
            deadPort = s.getLocalPort();
        }
    }

    @AfterAll
    static void down() {
        if (server != null) server.stop(0);
    }

    @Test
    @Timeout(10)
    void toStringQuick_cdnSain_retourneLeContenu() throws Exception {
        String body = Downloader.toStringQuick("http://127.0.0.1:" + port + "/healthy");
        assertEquals(350_000, body.length());
    }

    /** LE test de la panne réelle : le corps goutte → échec en ≤ 15 s, jamais un blocage infini. */
    @Test
    @Timeout(20)
    void toStringQuick_cdnQuiGoutte_echoueSousQuinzeSecondes() {
        long t0 = System.currentTimeMillis();
        assertThrows(IOException.class,
                () -> Downloader.toStringQuick("http://127.0.0.1:" + port + "/trickle"));
        long elapsed = System.currentTimeMillis() - t0;
        assertTrue(elapsed >= 11_000 && elapsed <= 15_000,
                "la deadline de 12 s doit couper le corps qui goutte (mesuré: " + elapsed + " ms)");
    }

    @Test
    @Timeout(10)
    void toStringQuick_404_echoueVite() {
        long t0 = System.currentTimeMillis();
        assertThrows(IOException.class,
                () -> Downloader.toStringQuick("http://127.0.0.1:" + port + "/missing"));
        assertTrue(System.currentTimeMillis() - t0 < 5_000, "un 404 doit échouer immédiatement");
    }

    @Test
    @Timeout(10)
    void toStringQuick_portFerme_echoueVite() {
        long t0 = System.currentTimeMillis();
        assertThrows(IOException.class,
                () -> Downloader.toStringQuick("http://127.0.0.1:" + deadPort + "/x"));
        assertTrue(System.currentTimeMillis() - t0 < 8_000, "connexion refusée = échec rapide");
    }

    @Test
    @Timeout(15)
    void toFile_cdnSain_ecritLeFichierComplet() throws Exception {
        Path dest = Files.createTempDirectory("dl-test").resolve("sub/healthy.bin");
        Downloader.toFile("http://127.0.0.1:" + port + "/healthy", dest, null);
        assertEquals(350_000, Files.size(dest));
    }
}
