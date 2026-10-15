package fr.nylerp.launcher.update;

import fr.nylerp.launcher.config.AppPaths;
import fr.nylerp.launcher.config.ModeDeJeu;
import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.util.Downloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Manages optional client-side mods that the player toggles in
 * {@link fr.nylerp.launcher.ui.SettingsView} but are NOT shipped in the base
 * modpack manifest.
 *
 * <p>For each entry: when the matching settings flag is true and the jar is
 * absent from {@code mods/}, fetch from Modrinth and place it; when the flag
 * is false and the jar is present, delete it. Run once before each launch via
 * {@link #applyAll()}.
 *
 * <p>The base modpack pipeline must NOT list these jars in its manifest —
 * otherwise {@link ModpackUpdater} would re-delete them on cleanup. The
 * filenames here are also threaded through {@link ModpackUpdater#OPTIONAL_MOD_FILENAMES}
 * so cleanup leaves them alone.
 */
public final class OptionalMods {

    private static final Logger LOG = LoggerFactory.getLogger(OptionalMods.class);

    /** A single optional mod definition.
     *  @param fileName  exact filename to use under mods/
     *  @param url       direct CDN url (Modrinth release link)
     *  @param sha512    expected SHA-512 from the Modrinth API (verified after download)
     *  @param modes     modes de jeu où l'entrée AGIT. Hors de ces modes, {@link #applyAll()} ne
     *                   l'installe ni ne la supprime : un jar du même nom livré par le pack de
     *                   l'autre mode reste intact (15/10/2026, Controlify n'est optionnel qu'en
     *                   pokényle). */
    public record Entry(String fileName, String url, String sha512, BooleanSupplier enabled,
                        Predicate<ModeDeJeu> modes) {
        /** Entrée active dans tous les modes (le cas historique). */
        public Entry(String fileName, String url, String sha512, BooleanSupplier enabled) {
            this(fileName, url, sha512, enabled, m -> true);
        }

        /** L'entrée agit-elle dans ce mode ? */
        public boolean appliesTo(ModeDeJeu mode) {
            return modes.test(mode);
        }
    }

    public static final List<Entry> ENTRIES = List.of(
            new Entry(
                "litematica-fabric-1.21-0.19.60.jar",
                "https://cdn.modrinth.com/data/bEpr0Arc/versions/aEvrmYqW/litematica-fabric-1.21-0.19.60.jar",
                "6808c6987a15c6659ceadd7757dcceefbbe3e45dbf5c017d2a7606b6e7d4dcffdeac3206b42950c2024b3f613d55fdb66d93e831d0ed83d874f5e2508c525218",
                () -> Settings.get().optionalLitematica),
            // malilib 0.21.10 — bibliothèque OBLIGATOIRE de Litematica 0.19.60 (« requires version 0.21.10- or
            // later of malilib » : sans elle le jeu refuse de démarrer, vu le 15/10/2026). Suit le réglage
            // Litematica : installée avec lui, supprimée avec lui.
            new Entry(
                "malilib-fabric-1.21-0.21.10.jar",
                "https://cdn.modrinth.com/data/GcWjdA9I/versions/C99LEy6r/malilib-fabric-1.21-0.21.10.jar",
                "9f70b4605ea23f8c8b519622a6cbdcac6c8934c70bab3b8ea2d5da071a37e71ff74406d2e154c5ba4ebc7772d76e97ae94f5760e71632bd8a11d23ae8ae9a111",
                () -> Settings.get().optionalLitematica),
            // Distant Horizons 2.4.5-b — dropped back from the 3.0.3-b branch
            // we previously shipped (2026-05-12 freeze investigation). On
            // 3.0.3-b, terrain shader binds + buffer uploads were producing
            // 1+ second hitches when not paired with Iris (16.6 s of stalls
            // measured across 3 player sessions). 2.4.5-b is the consensus
            // stable branch on 1.21.1 — ~6 months of bug-fix iteration and
            // smaller (~24 MB vs ~29 MB), with the same LOD feature set the
            // player actually cares about. Auto-coupled to Iris (see below):
            // toggling DH on forces Iris on too, because without Iris DH's
            // shader pipeline still hits the legacy bind paths that stutter.
            new Entry(
                "DistantHorizons-2.4.5-b-1.21.1-fabric-neoforge.jar",
                "https://cdn.modrinth.com/data/uCdwusMi/versions/bLPLghy9/DistantHorizons-2.4.5-b-1.21.1-fabric-neoforge.jar",
                "6ee8b04af858450eac2e0fe6c3a6cb09dfc0f9c1691fb0f76f79bbc73e08e5dca6f18257294ba647b1520d4fb2110bbbb085830e536c8f4638995c75f66fe1eb",
                // JAMAIS sur macOS : DH rend ses LOD via un GLProxy sur le chemin OpenGL-4.1-sur-Metal
                // d'Apple (pas de Buffer Storage, upload "DATA" lent) → le render thread gèle 1,5-2 s/frame
                // puis freeze dur (cf crash Apple M5). Même la config safe (instanced/generic off, dist 96)
                // gèle. On force DH OFF sur Mac : non installé, et le jar existant est supprimé au sync
                // (applyOne supprime quand wanted=false). Iris n'est PAS touché (son entry reste OR'd).
                () -> Settings.get().optionalDistantHorizons && !isMac()),
            // Iris 1.8.8 — back as an optional after the 2026-05-11 removal.
            // Re-introduced 2026-05-12 because Distant Horizons depends on it
            // for a stutter-free render path. Iris alone (no DH) is fine too,
            // for players who want shader packs without the LOD load. The DH
            // toggle force-enables this (see Settings.get().optionalIris being
            // OR'd with optionalDistantHorizons below).
            // 15/10/2026 : toujours installé en mode pokényle, où Complementary Reimagined est
            // actif par défaut (ShaderDefaults). Le joueur coupe les shaders en jeu avec K ; le
            // réglage « Iris (shaders) » ne vaut donc plus que pour NyleRP.
            new Entry(
                "iris-fabric-1.8.8+mc1.21.1.jar",
                "https://cdn.modrinth.com/data/YL57xq9U/versions/zsoi0dso/iris-fabric-1.8.8%2Bmc1.21.1.jar",
                "2e6ba2ffa1e1a6799288245a7e0ac68ee8df1d41b98362189df58f535cae34fa9277801e4136633467341b7dae5be0e5c698011b480b3d91b66d3dd4f7567aa6",
                () -> Settings.get().optionalIris || Settings.get().optionalDistantHorizons
                        || ModeDeJeu.courant() == ModeDeJeu.POKENYLE),
            // Punchy! 2.6.2 — animations à la première personne (mains visibles avec
            // l'objet tenu, animations de combat/mouvement/interaction, physique en main).
            // `environment: client` dans son fabric.mod.json et `server_side: unsupported`
            // sur Modrinth → strictement du rendu local : ni les dégâts ni le recul réels
            // ne changent, donc aucune divergence client/serveur possible. Sa seule
            // dépendance déclarée est fabric-api, déjà livrée par le manifeste du pack
            // (mods/fabric-api-0.116.12+1.21.1.jar) → rien à ajouter ici.
            new Entry(
                "punchy-2.6.2-fabric-1.21.1.jar",
                "https://cdn.modrinth.com/data/8aoMKplv/versions/H45vu8VY/punchy-2.6.2-fabric-1.21.1.jar",
                "2bcf8e68b0bbaeb8b9261bfe2e5cbd43e150e8dfcaa0424f84a7799b1dd2892a241bae43dc8dd64a8583d1d6ce5c1f9bc89fc484a8e5389ac8d5e2a63829729e",
                () -> Settings.get().optionalPunchy),
            // Controlify 3.0.1 (manettes) — quitte le pack pokényle le 15/10/2026 et devient
            // optionnel, coupé par défaut : 2 113 classes, natifs SDL et 19 à-coups mesurés sur
            // le fil de rendu chez des joueurs qui n'ont pas de manette. Fichier identique au
            // jar du pack 181 (même SHA-512, version Modrinth y9bu5RxH). Sa seule dépendance hors
            // Fabric est YACL (yet_another_config_lib_v3 ≥ 3.8.0), livré par le pack pokényle.
            // POKÉNYLE SEULEMENT : en mode NyleRP l'entrée n'installe ni ne supprime rien.
            new Entry(
                "controlify-3.0.1+lts+1.21.1-fabric.jar",
                "https://cdn.modrinth.com/data/DOUdJVEm/versions/y9bu5RxH/controlify-3.0.1%2Blts%2B1.21.1-fabric.jar",
                "55fa83d2a332bd8d26624a95043bb38a28028154982ee4a7d7560df380582a68985c13c6c73e55f1047010d7be2c6bb38f1bb67fe5f4eddb2b6c99f04caf25c0",
                () -> Settings.get().optionalControlify,
                m -> m == ModeDeJeu.POKENYLE)
    );

    /** Filenames the launcher itself shipped as optional but that have been
     *  removed from the registry (e.g. Bobby — superseded by Distant
     *  Horizons). {@link #applyAll()} unconditionally deletes any of these
     *  jars from {@code mods/}, so a user who previously toggled them on
     *  doesn't keep an orphan jar that the modpack manifest no longer manages
     *  and the optional registry no longer knows about. */
    private static final List<String> DEPRECATED_FILENAMES = List.of(
            "bobby-5.2.4+mc1.21.jar",
            // DH 3.0.3-b — replaced 2026-05-12 by the 2.4.5-b consensus stable
            // (without-Iris pipeline on 3.0.3 was producing 1+ second hitches).
            // Any client that previously toggled DH on under the 3.0.3 entry
            // is auto-purged here; OptionalMods.applyAll() then installs the
            // 2.4.5 filename listed in ENTRIES.
            "DistantHorizons-3.0.3-b-1.21.1-fabric-neoforge.jar",
            // 3D Skin Layers — retiré définitivement le 2026-06-08. Tout client qui
            // l'avait activé via l'ancien toggle voit le jar auto-purgé au prochain
            // lancement (applyAll() supprime ces filenames inconditionnellement).
            "skinlayers3d-fabric-1.11.1-mc1.21.1.jar"
    );

    /** Filenames declared as optional mods — used by {@link ModpackUpdater}
     *  cleanup so they're not deleted as "unmanaged" files. Includes the
     *  deprecated set so a still-installed orphan jar isn't deleted by the
     *  modpack-cleanup pass *before* {@link #applyAll()} gets the chance to
     *  log the removal as an explicit "deprecated" cleanup. */
    public static List<String> filenames() {
        List<String> out = new ArrayList<>(ENTRIES.size() + DEPRECATED_FILENAMES.size());
        for (Entry e : ENTRIES) out.add(e.fileName);
        out.addAll(DEPRECATED_FILENAMES);
        return out;
    }

    /** Comme {@link #filenames()}, limité aux entrées qui agissent dans {@code mode}. Dans un
     *  mode où une entrée ne s'applique pas, son jar est un fichier ordinaire du pack : le
     *  nettoyage de {@link ModpackUpdater} doit le traiter comme tel. */
    public static List<String> filenames(ModeDeJeu mode) {
        List<String> out = new ArrayList<>(ENTRIES.size() + DEPRECATED_FILENAMES.size());
        for (Entry e : ENTRIES) if (e.appliesTo(mode)) out.add(e.fileName);
        out.addAll(DEPRECATED_FILENAMES);
        return out;
    }

    /** Reconcile mods/ with current Settings:
     *  <ul>
     *    <li>flag ON + file missing → download + verify</li>
     *    <li>flag OFF + file present → delete</li>
     *    <li>otherwise → no-op</li>
     *  </ul>
     *  Errors are logged but never bubble up — a failed optional mod must not
     *  block launch. */
    public static void applyAll() {
        applyAll(AppPaths.modsDir(), ModeDeJeu.courant());
        // Shaders par défaut du mode pokényle : le zip Complementary dans shaderpacks/. Les
        // réglages Iris (une seule fois) sont posés plus tard, par MinecraftLauncher.
        if (ModeDeJeu.courant() == ModeDeJeu.POKENYLE) {
            ShaderDefaults.ensurePack(AppPaths.shaderPacks());
        }
    }

    /** Cœur de {@link #applyAll()}, sur un dossier et un mode explicites (testable). */
    static void applyAll(Path modsDir, ModeDeJeu mode) {
        // Always-clean step: jars that were optional in a previous launcher
        // version but have been retired. Run before applying current entries
        // so orphans are gone even if the registry shrinks further.
        for (String name : DEPRECATED_FILENAMES) {
            Path p = modsDir.resolve(name);
            if (Files.exists(p)) {
                try {
                    Files.deleteIfExists(p);
                    LOG.info("Removed deprecated optional mod: {}", name);
                } catch (Exception ex) {
                    LOG.warn("Could not delete deprecated optional {}: {}", name, ex.toString());
                }
            }
        }
        for (Entry e : ENTRIES) {
            if (!e.appliesTo(mode)) continue;   // hors de son mode : ni installé, ni supprimé
            try {
                applyOne(modsDir, e);
            } catch (Exception ex) {
                LOG.warn("Optional mod {} failed: {}", e.fileName, ex.toString());
            }
        }
    }

    private static void applyOne(Path modsDir, Entry e) throws IOException {
        Path target = modsDir.resolve(e.fileName);
        boolean wanted = e.enabled.getAsBoolean();
        boolean present = Files.exists(target);
        if (wanted && !present) {
            LOG.info("Installing optional mod: {}", e.fileName);
            Downloader.toFile(e.url, target, null);
            // Verify SHA-512 (Modrinth's primary digest); if it mismatches the
            // file is corrupt or upstream replaced the version — delete and
            // log so a retry next launch re-downloads cleanly.
            String got = sha512Hex(target);
            if (!got.equalsIgnoreCase(e.sha512)) {
                Files.deleteIfExists(target);
                throw new IOException("SHA mismatch for " + e.fileName);
            }
        } else if (!wanted && present) {
            LOG.info("Removing optional mod (toggled off): {}", e.fileName);
            Files.deleteIfExists(target);
        }
    }

    static String sha512Hex(Path file) throws IOException {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-512");
            try (var in = Files.newInputStream(file)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            }
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException nse) {
            throw new IOException(nse);
        }
    }

    /** macOS = pas de Distant Horizons (rendu OpenGL/Metal trop lent sur GPU Apple → freeze). */
    public static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    private OptionalMods() {}
}
