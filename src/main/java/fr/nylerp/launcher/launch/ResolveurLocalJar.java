package fr.nylerp.launcher.launch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Fabrique, depuis les classes du launcher lui-même, le petit jar du résolveur de noms du jeu
 * ({@link fr.nylerp.resolveur.ResolveurLocal}) et rend son chemin, à passer au jeu en {@code -Xbootclasspath/a}.
 *
 * Pourquoi : sur macOS, log4j demande le nom de la machine avant que Fabric écrive sa première ligne ; quand mDNS ne
 * répond pas pour ce nom, la JVM attend 5 s (mesuré 5,01 s le 06/11/2026, à chaque lancement). Le résolveur répond
 * aussitôt pour ce seul nom et laisse tous les autres au résolveur de la JVM.
 *
 * Aucun binaire versionné : le jar est reconstruit si son contenu diffère (empreinte dans le nom). En cas d'échec,
 * {@code null} : le jeu se lance comme avant (5 s de plus), jamais d'erreur bloquante.
 */
final class ResolveurLocalJar {

    private static final Logger LOG = LoggerFactory.getLogger(ResolveurLocalJar.class);
    private static final String[] CLASSES = {
            "fr/nylerp/resolveur/ResolveurLocal.class",
            "fr/nylerp/resolveur/ResolveurLocal$1.class",
    };
    private static final String SERVICE = "META-INF/services/java.net.spi.InetAddressResolverProvider";

    static Path preparer(Path dossier) {
        try {
            byte[] jar = construire();
            String emp = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jar)).substring(0, 12);
            Path cible = dossier.resolve("nyle-resolveur-local-" + emp + ".jar");
            if (!Files.exists(cible) || Files.size(cible) != jar.length) {
                Files.createDirectories(dossier);
                Path tmp = dossier.resolve(cible.getFileName() + ".tmp");
                Files.write(tmp, jar);
                Files.move(tmp, cible, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            return cible;
        } catch (Exception e) {
            LOG.warn("Résolveur local indisponible (le jeu démarrera sans) : {}", e.toString());
            return null;
        }
    }

    private static byte[] construire() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (String c : CLASSES) {
                try (InputStream in = ResolveurLocalJar.class.getClassLoader().getResourceAsStream(c)) {
                    if (in == null) throw new IllegalStateException("classe absente du launcher : " + c);
                    ZipEntry e = new ZipEntry(c);
                    e.setTime(0L);   // jar déterministe : même contenu → même empreinte → même fichier
                    z.putNextEntry(e);
                    z.write(in.readAllBytes());
                    z.closeEntry();
                }
            }
            ZipEntry s = new ZipEntry(SERVICE);
            s.setTime(0L);
            z.putNextEntry(s);
            z.write("fr.nylerp.resolveur.ResolveurLocal\n".getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        return out.toByteArray();
    }

    private ResolveurLocalJar() {}
}
