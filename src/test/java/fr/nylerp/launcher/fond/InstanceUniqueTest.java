package fr.nylerp.launcher.fond;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Un second lancement réveille le premier et s'arrête ; jamais de blocage si le premier est mort ou muet. */
@Timeout(30)
class InstanceUniqueTest {

    @Test
    void le_second_lancement_relaie_son_message(@TempDir Path dir) throws Exception {
        BlockingQueue<String> recus = new ArrayBlockingQueue<>(4);
        InstanceUnique premier = new InstanceUnique(dir);
        assertEquals(InstanceUnique.Resultat.PREMIERE, premier.acquerir("OUVRIR", recus::add));
        try {
            InstanceUnique second = new InstanceUnique(dir);
            assertEquals(InstanceUnique.Resultat.RELAYEE, second.acquerir("OUVRIR pokenyle", m -> fail("le second n'écoute pas")));
            assertEquals("OUVRIR pokenyle", recus.poll(5, TimeUnit.SECONDS));
            InstanceUnique troisieme = new InstanceUnique(dir);
            assertEquals(InstanceUnique.Resultat.RELAYEE, troisieme.acquerir("FOND", m -> { }));
            assertEquals("FOND", recus.poll(5, TimeUnit.SECONDS));
        } finally {
            premier.fermer();
        }
        InstanceUnique apres = new InstanceUnique(dir);
        assertEquals(InstanceUnique.Resultat.PREMIERE, apres.acquerir("OUVRIR", m -> { }), "verrou libéré à la fermeture");
        apres.fermer();
    }

    @Test
    void un_fichier_de_port_perime_ne_bloque_pas(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("instance.port"), "1 0123456789abcdef");
        InstanceUnique i = new InstanceUnique(dir);
        assertEquals(InstanceUnique.Resultat.PREMIERE, i.acquerir("OUVRIR", m -> { }), "personne ne tient le verrou");
        i.fermer();
    }

    @Test
    void premier_muet_le_second_demarre_sans_garde(@TempDir Path dir) throws Exception {
        InstanceUnique premier = new InstanceUnique(dir);
        assertEquals(InstanceUnique.Resultat.PREMIERE, premier.acquerir("OUVRIR", m -> { }));
        try {
            // Simule un premier figé : port qui ne répond plus.
            Files.writeString(dir.resolve("instance.port"), "1 jeton");
            InstanceUnique second = new InstanceUnique(dir);
            assertEquals(InstanceUnique.Resultat.SANS_GARDE, second.acquerir("OUVRIR", m -> { }));
        } finally {
            premier.fermer();
        }
    }

    @Test
    void un_message_sans_le_jeton_est_ignore(@TempDir Path dir) throws Exception {
        BlockingQueue<String> recus = new ArrayBlockingQueue<>(4);
        InstanceUnique premier = new InstanceUnique(dir);
        premier.acquerir("OUVRIR", recus::add);
        try {
            int port = Integer.parseInt(Files.readString(dir.resolve("instance.port")).split(" ")[0]);
            try (java.net.Socket s = new java.net.Socket(java.net.InetAddress.getLoopbackAddress(), port)) {
                s.getOutputStream().write("mauvais OUVRIR\n".getBytes());
                s.getOutputStream().flush();
            }
            assertNull(recus.poll(1, TimeUnit.SECONDS));
        } finally {
            premier.fermer();
        }
    }
}
