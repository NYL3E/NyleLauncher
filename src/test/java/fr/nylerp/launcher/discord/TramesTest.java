package fr.nylerp.launcher.discord;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.*;

/** Trames IPC : en-tête petit-boutiste (code puis longueur), trames reçues en morceaux ou collées. */
class TramesTest {

    /** Un canal en mémoire qui rend des morceaux d'octets, un par lecture. */
    private static final class CanalMemoire implements Canal {
        final Deque<byte[]> morceaux = new ArrayDeque<>();
        @Override public int lire(byte[] b, int debut, int longueur) {
            byte[] m = morceaux.peekFirst();
            if (m == null) return 0;
            int n = Math.min(longueur, m.length);
            System.arraycopy(m, 0, b, debut, n);
            morceaux.removeFirst();
            if (n < m.length) morceaux.addFirst(Arrays.copyOfRange(m, n, m.length));
            return n;
        }
        @Override public void ecrire(byte[] b) { }
        @Override public void close() { }
    }

    @Test
    void enTetePetitBoutiste() {
        String json = "{\"v\":1,\"client_id\":\"123456789012345678\"}";
        byte[] t = Trames.encoder(Trames.HANDSHAKE, json);
        assertEquals(40, json.length());
        assertArrayEquals(new byte[] {0, 0, 0, 0, 40, 0, 0, 0}, Arrays.copyOf(t, 8));
        assertEquals(json, new String(t, 8, t.length - 8, StandardCharsets.UTF_8));
        byte[] f = Trames.encoder(Trames.PONG, "é".repeat(200)); // 400 octets en UTF-8
        assertArrayEquals(new byte[] {4, 0, 0, 0, (byte) 0x90, 1, 0, 0}, Arrays.copyOf(f, 8));
    }

    @Test
    void trameEnMorceauxEtTramesCollees() throws IOException {
        CanalMemoire c = new CanalMemoire();
        byte[] a = Trames.encoder(Trames.FRAME, "{\"evt\":\"READY\",\"texte\":\"" + "x".repeat(6000) + "\"}");
        byte[] b = Trames.encoder(Trames.PING, "{\"n\":1}");
        Trames.Lecteur l = new Trames.Lecteur();
        for (int i = 0; i < a.length; i += 7) c.morceaux.add(Arrays.copyOfRange(a, i, Math.min(a.length, i + 7)));
        byte[] collees = new byte[b.length * 2];
        System.arraycopy(b, 0, collees, 0, b.length);
        System.arraycopy(b, 0, collees, b.length, b.length);
        c.morceaux.add(collees);

        Trames.Trame t1 = l.suivante(c);
        assertEquals(Trames.FRAME, t1.op());
        assertTrue(t1.json().endsWith("\"}") && t1.json().length() > 6000);
        assertEquals(new Trames.Trame(Trames.PING, "{\"n\":1}"), l.suivante(c));
        assertEquals(new Trames.Trame(Trames.PING, "{\"n\":1}"), l.suivante(c));
        assertNull(l.suivante(c), "rien de plus : null, sans attendre");
    }

    @Test
    void longueurAbsurdeRefusee() {
        CanalMemoire c = new CanalMemoire();
        c.morceaux.add(new byte[] {1, 0, 0, 0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x7F});
        assertThrows(IOException.class, () -> new Trames.Lecteur().suivante(c));
    }
}
