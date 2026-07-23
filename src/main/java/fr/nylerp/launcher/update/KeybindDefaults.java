package fr.nylerp.launcher.update;

import fr.nylerp.launcher.config.AppPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Migrations ONE-SHOT de keybinds (owner 2026-07-23) : force certaines touches de MODS à
 * « non assignée » UNE seule fois par installation, puis ne retouche PLUS JAMAIS — un rebind
 * ultérieur du joueur (sauvé par Minecraft dans options.txt) est donc respecté à vie.
 *
 * <p>Mécanique : un fichier-marqueur par migration dans le dossier {@code state/} du launcher.
 * Marqueur absent → la ligne {@code key_<id>} d'options.txt est REMPLACÉE (ou ajoutée) par
 * {@code key.keyboard.unknown}, puis le marqueur est posé. Marqueur présent → no-op.
 * Idempotent, silencieux, ne casse jamais le lancement (best-effort).
 */
public final class KeybindDefaults {

    /** {marqueur, clé options.txt (sans le préfixe key_)}. */
    private static final String[][] UNBIND_ONCE = {
            // Chisels & Bits « Zoom with monocle » : bindé Z par défaut, en conflit avec le zoom
            // Just Zoom du pack — non assignée par défaut pour tout le monde (owner 2026-07-23).
            {"kbfix-cnb-zoom-v1", "mod.chiselsandbits.keys.key.zoom"},
    };

    private KeybindDefaults() {}

    /** À appeler au pré-lancement (après le sync du modpack, avant le démarrage de MC). */
    public static void applyOnce() {
        for (String[] fix : UNBIND_ONCE) {
            try {
                Path marker = AppPaths.launcherState().resolve(fix[0] + ".done");
                if (Files.exists(marker)) continue;
                patchOptions("key_" + fix[1]);
                Files.writeString(marker, "done\n", StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                // best-effort : ne bloque jamais un lancement pour une histoire de touche
            }
        }
    }

    private static void patchOptions(String optionKey) throws Exception {
        Path options = AppPaths.gameDir().resolve("options.txt");
        List<String> lines = Files.exists(options)
                ? new ArrayList<>(Files.readAllLines(options, StandardCharsets.UTF_8))
                : new ArrayList<>();
        boolean replaced = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(optionKey + ":")) {
                lines.set(i, optionKey + ":key.keyboard.unknown");
                replaced = true;
                break;
            }
        }
        if (!replaced) lines.add(optionKey + ":key.keyboard.unknown");
        Files.write(options, lines, StandardCharsets.UTF_8);
    }
}
