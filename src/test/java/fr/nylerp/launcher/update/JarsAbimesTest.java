package fr.nylerp.launcher.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class JarsAbimesTest {

    private static byte[] jarSain() throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(b)) {
            z.putNextEntry(new ZipEntry("fabric.mod.json"));
            z.write("{}".getBytes());
            z.closeEntry();
        }
        return b.toByteArray();
    }

    @Test
    void retireLesJarsTronquesEtGardeLesSains(@TempDir Path mods) throws Exception {
        byte[] sain = jarSain();
        Files.write(mods.resolve("sain-1.0.jar"), sain);
        Files.write(mods.resolve("tronque-1.0.jar"), java.util.Arrays.copyOf(sain, sain.length / 2));
        Files.writeString(mods.resolve("vide-1.0.jar"), "");
        Files.writeString(mods.resolve("notes.txt"), "pas un jar");

        assertEquals(2, JarsAbimes.purger(mods));
        assertTrue(Files.exists(mods.resolve("sain-1.0.jar")));
        assertFalse(Files.exists(mods.resolve("tronque-1.0.jar")));
        assertFalse(Files.exists(mods.resolve("vide-1.0.jar")));
        assertTrue(Files.exists(mods.resolve("notes.txt")));
        assertEquals(0, JarsAbimes.purger(mods), "un second passage ne retire plus rien");
    }

    /** La mémoire « jars sains » évite de rouvrir un jar inchangé, mais ne laisse JAMAIS passer un jar abîmé après coup. */
    @Test
    void laMemoireNAffaiblitPasLaDetection(@TempDir Path racine) throws Exception {
        Path mods = Files.createDirectories(racine.resolve("mods"));
        Path memoire = racine.resolve("state").resolve("jars-sains.json");
        byte[] sain = jarSain();
        Path a = mods.resolve("a-1.0.jar");
        Path b = mods.resolve("b-1.0.jar");
        Files.write(a, sain);
        Files.write(b, sain);

        assertEquals(0, JarsAbimes.purger(mods, memoire));
        assertTrue(Files.exists(memoire), "la mémoire est écrite après le premier passage");
        String apresPremier = Files.readString(memoire);
        assertEquals(0, JarsAbimes.purger(mods, memoire));
        assertEquals(apresPremier, Files.readString(memoire), "rien n'a changé : la mémoire n'est pas réécrite");

        // Tronqué après coup : la taille change, le jar est rouvert et retiré.
        Files.write(a, java.util.Arrays.copyOf(sain, sain.length / 2));
        assertEquals(1, JarsAbimes.purger(mods, memoire));
        assertFalse(Files.exists(a));
        assertTrue(Files.exists(b));

        // Réécrit à la MÊME taille mais abîmé (date changée) : rouvert et retiré.
        byte[] abime = sain.clone();
        java.util.Arrays.fill(abime, abime.length - 22, abime.length, (byte) 0);   // l'en-tête de fin d'archive est effacé
        Files.write(b, abime);
        Files.setLastModifiedTime(b, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 60_000));
        assertEquals(1, JarsAbimes.purger(mods, memoire));
        assertFalse(Files.exists(b));

        // Mémoire illisible : tout est rouvert, comme sans mémoire.
        Files.write(a, java.util.Arrays.copyOf(sain, sain.length / 2));
        Files.writeString(memoire, "ceci n'est pas du JSON {");
        assertEquals(1, JarsAbimes.purger(mods, memoire));

        // Un jar sain reste là, et un jar retiré ne reste pas dans la mémoire.
        Files.write(a, sain);
        assertEquals(0, JarsAbimes.purger(mods, memoire));
        assertFalse(Files.readString(memoire).contains("b-1.0.jar"));
    }
}
