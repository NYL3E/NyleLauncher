package fr.nylerp.launcher.update;

import fr.nylerp.launcher.config.ModeDeJeu;
import fr.nylerp.launcher.util.Downloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * SHADERS PAR DÉFAUT DU MODE POKÉNYLE (15/10/2026) : Complementary Reimagined r5.9.3, actif dès
 * le premier lancement, coupable en jeu avec la touche K d'Iris.
 *
 * <h2>Licence (Complementary License Agreement 1.7, § 1.2)</h2>
 * <ul>
 *   <li>Le zip vient TOUJOURS du CDN Modrinth ({@link #PACK_URL}) : aucun réhébergement, ni sur
 *       GitHub ni sur le site.</li>
 *   <li>Le zip n'est JAMAIS modifié : il est vérifié au SHA-512 publié par l'API Modrinth, puis
 *       laissé tel quel. Les réglages vont dans le fichier {@code .txt} d'Iris, à côté.</li>
 *   <li>Crédit visible : section « À propos » des paramètres du launcher.</li>
 * </ul>
 *
 * <h2>Deux temps</h2>
 * <ol>
 *   <li>{@link #ensurePack(Path)} — à chaque lancement pokényle, depuis
 *       {@link OptionalMods#applyAll()} : pose le zip s'il manque. Ne supprime jamais rien dans
 *       {@code shaderpacks/} (les packs du joueur y vivent).</li>
 *   <li>{@link #applyIrisDefaultsOnce(Path, ModeDeJeu)} — UNE SEULE FOIS par instance (marqueur
 *       {@value #MARKER}) : choisit le pack dans {@code config/iris.properties} si le joueur n'en
 *       a choisi aucun, et pose le profil allégé. Ensuite plus rien n'écrit dans ces fichiers :
 *       la touche K écrit {@code enableShaders=false}, et ce choix doit tenir.</li>
 * </ol>
 */
public final class ShaderDefaults {

    private static final Logger LOG = LoggerFactory.getLogger(ShaderDefaults.class);

    /** Nom exact du zip publié sur Modrinth (projet HVnmMxH1, version Bqen1mJX). */
    public static final String PACK_FILE = "ComplementaryReimagined_r5.9.3.zip";
    public static final String PACK_URL =
            "https://cdn.modrinth.com/data/HVnmMxH1/versions/Bqen1mJX/ComplementaryReimagined_r5.9.3.zip";
    /** SHA-512 publié par {@code https://api.modrinth.com/v2/version/Bqen1mJX} (vérifié le 15/10/2026). */
    public static final String PACK_SHA512 =
            "45304b1d7862afdb2177b7e6226fc9c6737911d17a4ad46cc9c519ca4deb935f05072cd337cd8b19a15bb8062b57b54eceec7bdee4cd572d94858e2946d37d85";
    /** Taille publiée (octets). */
    public static final long PACK_SIZE = 553_397L;

    /** Marqueur « réglages Iris déjà posés », dans le dossier de jeu du mode (comme
     *  {@code .nyle_keybinds_v2}). Changer de version (v2) = rejouer la migration une fois. */
    public static final String MARKER = ".nyle_shaders_v1";

    /**
     * Profil « petit PC mais beau » (RAPPORT perf-shaders § 4.5), écrit dans
     * {@code shaderpacks/<zip>.txt} — le fichier où Iris garde les options d'un pack, ce qui évite
     * de toucher au zip. Entre LOW et MEDIUM : carte d'ombres 1024, distance d'ombre 96, rayons,
     * nuages et reflets de blocs allégés, ombres des ENTITÉS coupées (la passe d'ombre redessine
     * chaque modèle Cobblemon). À ajuster après mesure au banc.
     */
    public static final String PROFILE_TXT = String.join("\n",
            "SHADOW_QUALITY=1",
            "shadowDistance=96.0",
            "LIGHTSHAFT_QUALI_DEFINE=1",
            "CLOUD_QUALITY=1",
            "BLOCK_REFLECT_QUALITY=1",
            "ENTITY_SHADOW=-1") + "\n";

    // ── 1. Le zip ────────────────────────────────────────────────────────────────────────────

    /** Pose le zip Complementary dans {@code shaderPacksDir} s'il manque (téléchargement Modrinth
     *  + SHA-512). Ne lève jamais : un shader absent ne doit pas bloquer le lancement (Iris
     *  retombe sur le rendu normal quand le pack choisi est introuvable). */
    public static void ensurePack(Path shaderPacksDir) {
        ensurePack(shaderPacksDir, PACK_URL);
    }

    /** Cœur de {@link #ensurePack(Path)} avec une URL injectable (tests sur serveur local). */
    static boolean ensurePack(Path shaderPacksDir, String url) {
        Path target = shaderPacksDir.resolve(PACK_FILE);
        if (Files.exists(target)) return true;   // déjà là (ou fichier du joueur) : on n'y touche pas
        // Téléchargé à côté puis renommé seulement une fois vérifié : le nom définitif ne désigne
        // jamais un zip tronqué ou altéré.
        Path tmp = shaderPacksDir.resolve(PACK_FILE + ".download");
        try {
            Files.createDirectories(shaderPacksDir);
            LOG.info("Installing default shader pack: {}", PACK_FILE);
            Downloader.toFile(url, tmp, null);
            String got = OptionalMods.sha512Hex(tmp);
            if (!got.equalsIgnoreCase(PACK_SHA512)) {
                throw new IOException("SHA-512 mismatch for " + PACK_FILE);
            }
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (Exception e) {
            LOG.warn("Default shader pack not installed ({}): {}", PACK_FILE, e.toString());
            return false;
        } finally {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
        }
    }

    // ── 2. Les réglages Iris, une seule fois ─────────────────────────────────────────────────

    /**
     * Réglages Iris par défaut, UNE SEULE FOIS par instance, en mode pokényle seulement.
     * <ul>
     *   <li>{@code config/iris.properties} absent, ou clé {@code shaderPack} absente ou vide →
     *       {@code shaderPack=}{@value #PACK_FILE}, {@code enableShaders=true},
     *       {@code disableUpdateMessage=true} ; toutes les autres clés sont gardées. Un autre
     *       pack déjà choisi → fichier intouché.</li>
     *   <li>{@code shaderpacks/<zip>.txt} absent → profil {@link #PROFILE_TXT}. Présent →
     *       intouché.</li>
     *   <li>Marqueur {@value #MARKER} posé : plus jamais rien ensuite.</li>
     * </ul>
     * Ne lève jamais : en cas d'erreur, le marqueur n'est pas posé et on réessaiera.
     */
    public static void applyIrisDefaultsOnce(Path gameDir, ModeDeJeu mode) {
        if (mode != ModeDeJeu.POKENYLE) return;
        Path marker = gameDir.resolve(MARKER);
        if (Files.exists(marker)) return;   // déjà fait : le choix du joueur (touche K) fait foi
        try {
            Path props = gameDir.resolve("config").resolve("iris.properties");
            Properties p = new Properties();
            if (Files.exists(props)) {
                // Format d'Iris : java.util.Properties sur flux d'octets = ISO-8859-1 (IrisConfig).
                try (InputStream in = Files.newInputStream(props)) { p.load(in); }
            }
            String pack = p.getProperty("shaderPack");
            if (pack == null || pack.isBlank()) {
                p.setProperty("shaderPack", PACK_FILE);
                p.setProperty("enableShaders", "true");
                p.setProperty("disableUpdateMessage", "true");
                Files.createDirectories(props.getParent());
                try (OutputStream out = Files.newOutputStream(props)) {
                    p.store(out, "This file stores configuration options for Iris, such as the currently active shaderpack");
                }
                LOG.info("iris.properties: default shader pack set to {}", PACK_FILE);
            } else {
                LOG.info("iris.properties: player already chose '{}', left untouched", pack);
            }

            Path txt = gameDir.resolve("shaderpacks").resolve(PACK_FILE + ".txt");
            if (!Files.exists(txt)) {
                Files.createDirectories(txt.getParent());
                Files.writeString(txt, PROFILE_TXT, StandardCharsets.ISO_8859_1);
                LOG.info("shaderpacks: light profile written to {}", txt.getFileName());
            }
            Files.writeString(marker, "1");   // une seule fois, même si rien n'a changé
        } catch (IOException e) {
            LOG.warn("Default Iris settings not applied: {}", e.toString());
        }
    }

    private ShaderDefaults() {}
}
