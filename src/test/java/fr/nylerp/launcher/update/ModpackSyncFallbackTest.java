package fr.nylerp.launcher.update;

import com.google.gson.Gson;
import fr.nylerp.launcher.config.AppPaths;
import fr.nylerp.launcher.config.Constants;
import fr.nylerp.launcher.util.Hashing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LE test du scénario de la panne CDN GitHub du 2026-07-10/11 : un joueur DÉJÀ À JOUR clique
 * « Jouer » pendant que le serveur de mise à jour est injoignable. Attendu : repli sur le manifest
 * en cache, vérification locale, sync RÉUSSI, zéro téléchargement — le jeu se lance. L'ancien code
 * bouclait à l'infini sur « Téléchargement du manifest… ».
 *
 * <p>Isolation : {@code user.home} est redirigé vers un dossier temporaire (AppPaths le lit à
 * chaque appel), et l'URL du manifest pointe sur un port local fermé (échec de connexion immédiat
 * — le pire cas « CDN mort »).
 */
class ModpackSyncFallbackTest {

    private static final Gson GSON = new Gson();

    private String realHome;
    private Path tempHome;
    private int deadPort;

    @BeforeEach
    void setup() throws Exception {
        realHome = System.getProperty("user.home");
        tempHome = Files.createTempDirectory("nyle-launcher-test-home");
        System.setProperty("user.home", tempHome.toString());
        try (ServerSocket s = new ServerSocket(0)) {
            deadPort = s.getLocalPort();
        }
        ModpackUpdater.manifestBaseUrl = "http://127.0.0.1:" + deadPort + "/manifest.json";
    }

    @AfterEach
    void teardown() {
        System.setProperty("user.home", realHome);
        // null = comportement de production : l'URL est redemandée à chaque appel, puisqu'elle
        // dépend du mode de jeu choisi (voir ModpackUpdater.manifestBaseUrl).
        ModpackUpdater.manifestBaseUrl = null;
    }

    private record Captured(List<String> statuses) implements ModpackUpdater.Listener {
        @Override public void onStatus(String line) { statuses.add(line); }
        @Override public void onProgress(int done, int total, long bytesDone, long bytesTotal) {}
    }

    /** Écrit un cache manifest + le fichier du pack correspondant (SHA conforme). */
    private void primeUpToDateInstall() throws IOException {
        byte[] content = "contenu-du-pack".getBytes(StandardCharsets.UTF_8);
        Path file = AppPaths.gameDir().resolve("config/test.txt");
        Files.createDirectories(file.getParent());
        Files.write(file, content);
        String sha = Hashing.sha256(file);
        Map<String, Object> manifest = Map.of(
                "version", "test-2026.07.11",
                "mcVersion", "1.21.1",
                "files", List.of(Map.of(
                        "path", "config/test.txt",
                        "sha256", sha,
                        "size", content.length,
                        "url", "http://127.0.0.1:" + deadPort + "/jamais-telecharge")));
        Files.writeString(AppPaths.manifestCache(), GSON.toJson(manifest));
    }

    @Test
    @Timeout(30)
    void joueurAJour_cdnMort_lancementOfflineReussi() throws Exception {
        primeUpToDateInstall();
        Captured listener = new Captured(new CopyOnWriteArrayList<>());

        // Ne doit PAS lever : fetch échoue → repli cache → SHA conformes → sync OK.
        new ModpackUpdater(listener).sync();

        assertTrue(listener.statuses().stream().anyMatch(s -> s.contains("vérification locale")),
                "le repli sur le manifest en cache doit être annoncé — statuts: " + listener.statuses());
        assertTrue(listener.statuses().stream().noneMatch(s -> s.startsWith("↓")),
                "un joueur à jour ne doit RIEN télécharger — statuts: " + listener.statuses());
    }

    @Test
    @Timeout(30)
    void premiereInstall_cdnMort_echouePropre() {
        // Pas de cache manifest → impossible de savoir quoi installer : erreur claire attendue
        // (le dialog Réessayer/Lancer quand même/Annuler prend le relais côté UI).
        Captured listener = new Captured(new CopyOnWriteArrayList<>());
        assertThrows(IOException.class, () -> new ModpackUpdater(listener).sync());
    }

    @Test
    @Timeout(30)
    void hashCache_evite_le_rehachage_et_detecte_les_modifications() throws Exception {
        Path f = tempHome.resolve("data.bin");
        Files.write(f, "v1".getBytes(StandardCharsets.UTF_8));
        Path cacheFile = tempHome.resolve("hashcache.json");

        HashCache cache = HashCache.load(cacheFile);
        String h1 = cache.sha(f);
        assertEquals(Hashing.sha256(f), h1, "le premier passage hache réellement");
        assertEquals(h1, cache.sha(f), "fichier inchangé → même SHA (depuis le cache)");

        cache.save(cacheFile);
        assertTrue(Files.exists(cacheFile), "le cache doit être persisté");

        // Modification du contenu (taille change) → le SHA DOIT être recalculé.
        Files.write(f, "v2-plus-long".getBytes(StandardCharsets.UTF_8));
        String h2 = HashCache.load(cacheFile).sha(f);
        assertEquals(Hashing.sha256(f), h2, "fichier modifié → re-hachage obligatoire");
        assertNotEquals(h1, h2);
    }
}
