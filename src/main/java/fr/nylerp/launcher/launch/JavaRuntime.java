package fr.nylerp.launcher.launch;

import fr.nylerp.launcher.config.AppPaths;
import fr.nylerp.launcher.util.Downloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.stream.Stream;

/**
 * Ensures a usable Java 21 JRE is available locally. Downloads Eclipse Temurin
 * from adoptium.net on first run and caches under ~/.NyleLauncher/runtime/jdk-21.
 */
public final class JavaRuntime {

    private static final Logger LOG = LoggerFactory.getLogger(JavaRuntime.class);

    public static Path ensure(Downloader.Progress progress) throws IOException {
        Path root = AppPaths.rootDir().resolve("runtime").resolve("jdk-21");
        Path javaBin = javaBinary(root);
        if (javaBin != null && Files.isExecutable(javaBin)) {
            LOG.info("JRE already present: {}", javaBin);
            return javaBin;
        }
        // AUCUN JDK COMPLET ICI. Le dossier peut malgré tout contenir les restes d'une installation
        // à moitié faite : c'est le cas quand l'aplatissement a échoué en cours de route (antivirus
        // qui verrouille un fichier), laissant `bin/` d'un côté et `lib/` de l'autre. On lançait
        // alors un `javaw.exe` dont les bibliothèques avaient déménagé, et la JVM mourait sur
        // « could not open …/lib/jvm.cfg » — une erreur hors de notre fenêtre, dans une boîte
        // Windows, que le joueur ne pouvait ni comprendre ni contourner.
        // On repart donc d'un dossier VIDE : une réinstallation propre coûte un téléchargement,
        // une arborescence incohérente coûte un joueur.
        if (Files.exists(root)) {
            LOG.warn("Runtime incomplet sous {} — purge avant réinstallation", root);
            supprimerRecursivement(root);
        }
        Files.createDirectories(root);

        String os = osSlug();
        String arch = archSlug();
        String ext = os.equals("windows") ? "zip" : "tar.gz";
        String url = "https://api.adoptium.net/v3/binary/latest/21/ga/"
                + os + "/" + arch + "/jdk/hotspot/normal/eclipse";
        Path archive = root.resolve("jdk21." + ext);

        LOG.info("Downloading Temurin 21 for {}/{} → {}", os, arch, archive);
        Downloader.toFile(url, archive, progress);

        LOG.info("Extracting JRE…");
        extract(archive, root, ext);
        Files.deleteIfExists(archive);

        // The archive unpacks to a single top-level dir like "jdk-21.0.10+7". Flatten it.
        flattenSingleChild(root);

        javaBin = javaBinary(root);
        if (javaBin == null || !Files.isExecutable(javaBin)) {
            // javaBinary() n'accepte QUE les JDK entiers : si l'on tombe ici après une extraction
            // réussie, c'est que l'arborescence est restée éclatée malgré les réessais et la copie
            // de secours. Mieux vaut le dire franchement que rendre un binaire qui mourra sur
            // « could not open …/lib/jvm.cfg » dans une boîte Windows.
            throw new IOException("Java n'a pas pu être installé complètement (dossier "
                    + root + "). Un antivirus bloque probablement l'écriture : autorise le dossier "
                    + "NyleLauncher, puis relance.");
        }
        LOG.info("JRE installed at {}", javaBin);
        return javaBin;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static Path javaBinary(Path root) {
        Path attendu = javaBinaryAt(root);
        if (estComplet(attendu)) return attendu;
        // L'APLATISSEMENT A PU ÉCHOUER : le binaire est alors resté un cran plus bas, dans le
        // dossier d'origine de l'archive (« jdk-21.0.12+8 »). On le cherche là plutôt que de
        // déclarer le runtime absent — sans quoi le launcher re-télécharge 180 Mo à CHAQUE
        // démarrage et rééchoue au même endroit, bloquant le joueur définitivement.
        // Constaté le 13/08 chez LeGueux0 : « Erreur: …/jdk-21/jdk-21.0.12+8/bin/ucrtbase.dll ».
        try (Stream<Path> s = Files.list(root)) {
            for (Path enfant : s.filter(Files::isDirectory).toList()) {
                Path p = javaBinaryAt(enfant);
                if (estComplet(p)) return p;
            }
        } catch (IOException ignored) { /* dossier illisible → aucun candidat */ }
        return null;
    }

    /**
     * VRAI seulement si ce binaire appartient à un JDK ENTIER.
     *
     * <p>Trouver {@code javaw.exe} ne suffit pas : la JVM charge ses bibliothèques RELATIVEMENT à
     * son propre emplacement, à commencer par {@code ../lib/jvm.cfg}. Quand l'aplatissement du
     * dossier échoue à mi-chemin, {@code bin/} peut rester dans le dossier de l'archive pendant que
     * {@code lib/} a, lui, bien été déplacé. Le binaire existe, il est même exécutable — et il
     * meurt aussitôt sur « could not open …/lib/jvm.cfg », dans une boîte de dialogue Windows hors
     * de notre interface. Deuxième symptôme observé chez le même joueur, le 14/08, juste après
     * avoir corrigé le premier.
     *
     * <p>On exige donc la présence de {@code lib/jvm.cfg} À CÔTÉ du {@code bin/} retenu. Un JDK
     * éclaté sur deux dossiers n'est pas « presque bon » : il est inutilisable, et le dire tout de
     * suite déclenche la réinstallation propre au lieu d'un lancement voué à l'échec.
     */
    private static boolean estComplet(Path binaire) {
        if (binaire == null || !Files.exists(binaire)) return false;
        Path racineJdk = binaire.getParent() == null ? null : binaire.getParent().getParent();
        if (racineJdk == null) return false;
        return Files.isRegularFile(racineJdk.resolve("lib").resolve("jvm.cfg"));
    }

    /** Efface un dossier et tout son contenu, sans jamais lever. */
    private static void supprimerRecursivement(Path racine) {
        try (Stream<Path> s = Files.walk(racine)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) { }
            });
        } catch (IOException e) {
            LOG.warn("Purge partielle de {} : {}", racine, e.toString());
        }
    }

    /** Le chemin du binaire java SOUS une racine de JDK donnée, selon l'OS. */
    private static Path javaBinaryAt(Path root) {
        String os = osSlug();
        if ("windows".equals(os)) {
            // javaw.exe = GUI-subsystem java : launching the game from the
            // (GUI) launcher no longer pops a black console window. Game
            // output still lands in mc.log via ProcessBuilder.redirectOutput.
            Path javaw = root.resolve("bin").resolve("javaw.exe");
            if (Files.exists(javaw)) return javaw;
            return root.resolve("bin").resolve("java.exe");
        } else if ("mac".equals(os)) {
            return root.resolve("Contents").resolve("Home").resolve("bin").resolve("java");
        } else {
            return root.resolve("bin").resolve("java");
        }
    }

    private static String osSlug() {
        String n = System.getProperty("os.name").toLowerCase();
        if (n.contains("win")) return "windows";
        if (n.contains("mac") || n.contains("darwin")) return "mac";
        return "linux";
    }

    private static String archSlug() {
        String a = System.getProperty("os.arch").toLowerCase();
        if (a.contains("aarch64") || a.contains("arm64")) return "aarch64";
        if (a.contains("amd64") || a.contains("x86_64")) return "x64";
        return "x64";
    }

    private static void extract(Path archive, Path dest, String ext) throws IOException {
        if ("zip".equals(ext)) {
            // Pure-Java unzip. The previous 'powershell Expand-Archive' child
            // process flashed a console window on Windows (powershell.exe is a
            // console-subsystem binary spawned from our GUI app) — scary for
            // non-technical players. java.util.zip needs no external process.
            try (java.util.zip.ZipInputStream zin =
                         new java.util.zip.ZipInputStream(Files.newInputStream(archive))) {
                java.util.zip.ZipEntry entry;
                Path destReal = dest.toAbsolutePath().normalize();
                while ((entry = zin.getNextEntry()) != null) {
                    Path out = destReal.resolve(entry.getName()).normalize();
                    if (!out.startsWith(destReal)) {
                        throw new IOException("Zip entry escapes destination: " + entry.getName());
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(out);
                    } else {
                        Files.createDirectories(out.getParent());
                        Files.copy(zin, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zin.closeEntry();
                }
            }
            return;
        }
        // .tar.gz (mac/linux) — keep the system tar (symlinks + exec bits are
        // preserved natively), but route its output to a log file instead of
        // inheritIO so nothing ever surfaces visually.
        ProcessBuilder pb = new ProcessBuilder("tar", "-xzf", archive.toString(), "-C", dest.toString());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.to(dest.resolve("extract.log").toFile()));
        try {
            int rc = pb.start().waitFor();
            if (rc != 0) throw new IOException("Extract failed rc=" + rc);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Extract interrupted", ie);
        } finally {
            Files.deleteIfExists(dest.resolve("extract.log"));
        }
    }

    /**
     * If the archive extracted into a single subfolder (e.g. "jdk-21.0.10+7"),
     * move all of its contents up to {@code root} so our layout stays stable.
     */
    private static void flattenSingleChild(Path root) throws IOException {
        try (Stream<Path> s = Files.list(root)) {
            long dirCount = s.filter(Files::isDirectory).count();
            if (dirCount != 1) return;
        }
        Path child;
        try (Stream<Path> s = Files.list(root)) {
            child = s.filter(Files::isDirectory).findFirst().orElseThrow();
        }
        java.util.List<Path> entrees;
        try (Stream<Path> s = Files.list(child)) {
            entrees = s.toList();
        }
        for (Path p : entrees) {
            deplacerObstine(p, root.resolve(p.getFileName()));
        }
        Files.deleteIfExists(child);
    }

    /**
     * Déplace une entrée en RÉSISTANT aux verrous transitoires de Windows.
     *
     * <p>Le déplacement échouait chez un joueur sur {@code bin/ucrtbase.dll} — une DLL du runtime C
     * que Windows Defender scanne systématiquement à l'extraction, et qu'il VERROUILLE le temps du
     * scan. L'ancienne version relançait l'exception au premier échec : l'arborescence restait à
     * moitié déplacée, {@code javaw.exe} n'était jamais au chemin attendu, et le launcher
     * re-téléchargeait le JDK à chaque démarrage pour rééchouer au même endroit. Le joueur était
     * bloqué DÉFINITIVEMENT, avec pour toute explication le chemin du fichier — parce que c'est
     * exactement ce que rend {@code AccessDeniedException.getMessage()}.
     *
     * <p>Trois filets, dans l'ordre : on réessaie (le verrou d'un antivirus dure une poignée de
     * centaines de millisecondes), puis on COPIE si le déplacement reste impossible (une copie
     * n'exige pas le verrou exclusif qu'un renommage réclame), et en dernier recours on renonce à
     * CETTE entrée sans casser l'installation — {@link #javaBinary} sait désormais retrouver le
     * binaire resté un cran plus bas.
     */
    private static void deplacerObstine(Path source, Path cible) {
        IOException dernier = null;
        for (int essai = 1; essai <= 5; essai++) {
            try {
                Files.move(source, cible, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                dernier = e;
                try { Thread.sleep(150L * essai); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        // Le renommage reste refusé : on copie. Plus lent, mais sans verrou exclusif.
        try {
            if (Files.isDirectory(source)) {
                copierArborescence(source, cible);
            } else {
                Files.createDirectories(cible.getParent());
                Files.copy(source, cible, StandardCopyOption.REPLACE_EXISTING);
            }
            LOG.warn("Déplacement refusé pour {} ({}), copie effectuée à la place",
                    source.getFileName(), dernier == null ? "?" : dernier.getClass().getSimpleName());
        } catch (IOException e) {
            LOG.error("Ni déplacement ni copie possibles pour {} : {} — installation poursuivie, "
                    + "le binaire java sera cherché à son emplacement d'origine",
                    source, e.toString());
        }
    }

    /** Copie récursive, utilisée quand un renommage de dossier est refusé. */
    private static void copierArborescence(Path source, Path cible) throws IOException {
        try (Stream<Path> s = Files.walk(source)) {
            for (Path p : s.toList()) {
                Path dst = cible.resolve(source.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dst);
                } else {
                    Files.createDirectories(dst.getParent());
                    Files.copy(p, dst, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private JavaRuntime() {}
}
