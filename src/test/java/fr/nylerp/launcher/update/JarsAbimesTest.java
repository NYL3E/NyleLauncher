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
}
