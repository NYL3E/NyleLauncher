package fr.nylerp.launcher.discord;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/** Socket Unix de Discord (macOS, Linux), en mode non bloquant : le JDK suffit, aucune dépendance. */
final class CanalUnix implements Canal {

    /** Une écriture qui n'avance plus pendant ce temps : Discord est figé, on abandonne la connexion. */
    private static final long ECRITURE_MAX_NS = 2_000_000_000L;

    private final SocketChannel socket;

    private CanalUnix(SocketChannel socket) {
        this.socket = socket;
    }

    static CanalUnix ouvrir(String chemin) throws IOException {
        Path p = Path.of(chemin);
        if (!Files.exists(p)) throw new NoSuchFileException(chemin);
        SocketChannel s = SocketChannel.open(StandardProtocolFamily.UNIX);
        try {
            s.connect(UnixDomainSocketAddress.of(p)); // socket local : acceptée ou refusée sur-le-champ
            s.configureBlocking(false);
            return new CanalUnix(s);
        } catch (IOException | RuntimeException e) {
            try { s.close(); } catch (IOException ignore) { }
            throw e;
        }
    }

    @Override
    public int lire(byte[] b, int debut, int longueur) throws IOException {
        int n = socket.read(ByteBuffer.wrap(b, debut, longueur));
        if (n < 0) throw new EOFException("canal fermé par Discord");
        return n;
    }

    @Override
    public void ecrire(byte[] b) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(b);
        long limite = System.nanoTime() + ECRITURE_MAX_NS;
        while (buf.hasRemaining()) {
            if (socket.write(buf) > 0) continue;
            if (System.nanoTime() > limite) throw new IOException("Discord ne lit plus le canal");
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("écriture interrompue");
            }
        }
    }

    @Override
    public void close() {
        try { socket.close(); } catch (IOException ignore) { }
    }
}
