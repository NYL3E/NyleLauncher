package fr.nylerp.launcher.update;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Retire de mods/ les jars qui ne s'ouvrent pas comme une archive (téléchargement coupé, disque plein, antivirus) : Fabric refuse de
 * démarrer sur un seul d'entre eux (« zip END header not found »). Un jar retiré est absent du disque, donc la passe de
 * synchronisation qui suit le retélécharge toute seule. Ne touche que les .jar ; une archive saine n'est jamais modifiée.
 */
final class JarsAbimes {

    private static final Logger LOG = LoggerFactory.getLogger(JarsAbimes.class);

    private JarsAbimes() {}

    /** @return le nombre de jars abîmés supprimés. */
    static int purger(Path modsDir) {
        if (!Files.isDirectory(modsDir)) return 0;
        int n = 0;
        try (Stream<Path> list = Files.list(modsDir)) {
            for (Path p : (Iterable<Path>) list.filter(Files::isRegularFile)
                    .filter(x -> x.getFileName().toString().endsWith(".jar"))::iterator) {
                if (!ouvrable(p)) {
                    try {
                        Files.deleteIfExists(p);
                        n++;
                        LOG.warn("Jar abîmé retiré (sera retéléchargé) : {}", p.getFileName());
                    } catch (IOException e) {
                        LOG.warn("Jar abîmé non supprimable : {} ({})", p.getFileName(), e.toString());
                    }
                }
            }
        } catch (IOException e) {
            LOG.warn("Contrôle des jars impossible : {}", e.toString());
        }
        return n;
    }

    static boolean ouvrable(Path jar) {
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(jar.toFile())) {
            return z.size() >= 0;
        } catch (IOException e) {
            return false;
        }
    }
}
