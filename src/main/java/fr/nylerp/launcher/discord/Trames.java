package fr.nylerp.launcher.discord;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Les trames du canal local de Discord (doc « RPC », section IPC) : code d'opération sur 4 octets,
 * longueur du JSON sur 4 octets, tous deux petit-boutistes, puis le JSON en UTF-8.
 */
final class Trames {

    static final int HANDSHAKE = 0;
    static final int FRAME = 1;
    static final int CLOSE = 2;
    static final int PING = 3;
    static final int PONG = 4;

    /** Au-delà, ce n'est pas Discord qui parle (READY fait environ 1 Ko) : on coupe plutôt que d'allouer. */
    static final int TAILLE_MAX = 64 * 1024;

    record Trame(int op, String json) { }

    private Trames() { }

    static byte[] encoder(int op, String json) {
        byte[] corps = json.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(8 + corps.length).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(op).putInt(corps.length).put(corps).array();
    }

    /** Rassemble les octets reçus en trames complètes ; une trame peut arriver en plusieurs morceaux. */
    static final class Lecteur {
        private byte[] tampon = new byte[4096];
        private int rempli;

        /** La prochaine trame complète, en lisant ce qui est déjà arrivé sur le canal sans jamais attendre ; null si rien. */
        Trame suivante(Canal canal) throws IOException {
            Trame t = extraire();
            while (t == null) {
                if (rempli == tampon.length) tampon = Arrays.copyOf(tampon, Math.min(tampon.length * 2, TAILLE_MAX + 8));
                int n = canal.lire(tampon, rempli, tampon.length - rempli);
                if (n == 0) return null;
                rempli += n;
                t = extraire();
            }
            return t;
        }

        private Trame extraire() throws IOException {
            if (rempli < 8) return null;
            ByteBuffer b = ByteBuffer.wrap(tampon, 0, rempli).order(ByteOrder.LITTLE_ENDIAN);
            int op = b.getInt(0);
            int longueur = b.getInt(4);
            if (longueur < 0 || longueur > TAILLE_MAX) throw new IOException("trame invalide (" + longueur + " octets annoncés)");
            if (rempli < 8 + longueur) {
                if (tampon.length < 8 + longueur) tampon = Arrays.copyOf(tampon, 8 + longueur);
                return null;
            }
            String json = new String(tampon, 8, longueur, StandardCharsets.UTF_8);
            int reste = rempli - 8 - longueur;
            System.arraycopy(tampon, 8 + longueur, tampon, 0, reste);
            rempli = reste;
            return new Trame(op, json);
        }
    }
}
