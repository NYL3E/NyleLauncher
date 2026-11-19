package fr.nylerp.launcher.discord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Le socket Unix ne fait jamais attendre le fil : écriture vers un Discord qui ne lit plus, lecture sans données. */
@Timeout(20)
class CanalUnixTest {

    @Test
    void ecritureVersUnDiscordQuiNeLitPlusEchoueEnUneSeconde(@TempDir Path dir) throws Exception {
        Path p = dir.resolve("discord-ipc-0");
        AtomicReference<SocketChannel> accepte = new AtomicReference<>();
        try (ServerSocketChannel serveur = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            serveur.bind(UnixDomainSocketAddress.of(p));
            Thread t = new Thread(() -> {
                try { accepte.set(serveur.accept()); } catch (IOException ignore) { }
            });
            t.setDaemon(true);
            t.start();
            try (CanalUnix c = CanalUnix.ouvrir(p.toString())) {
                byte[] rien = new byte[16];
                assertEquals(0, c.lire(rien, 0, rien.length), "rien d'arrivé : 0, sans attendre");
                long t0 = System.nanoTime();
                IOException e = assertThrows(IOException.class, () -> c.ecrire(new byte[8 * 1024 * 1024]));
                long ms = (System.nanoTime() - t0) / 1_000_000;
                assertTrue(ms >= 900 && ms < 2000, "abandon après ~1 s (" + ms + " ms) : " + e.getMessage());
            } finally {
                SocketChannel s = accepte.get();
                if (s != null) s.close();
            }
        }
    }
}
