package fr.nylerp.launcher.launch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One-shot migrations applied to the player's {@code options.txt} before
 * launching Minecraft. Each migration is idempotent — calling them on every
 * launch is fine.
 *
 * <p>Currently:
 * <ul>
 *   <li>{@link #ensureResourcePackEnabled(Path, String)} — adds the modpack's
 *       texture pack to the {@code resourcePacks} list so it's automatically
 *       active on first launch (and doesn't get silently dropped if the
 *       player never opens the resource-pack screen). Idempotent: skipped
 *       when the entry is already present.</li>
 * </ul>
 *
 * <p>options.txt is an unstructured key:value file with values that look like
 * JSON arrays for some keys ({@code resourcePacks:["vanilla","fabric"]}).
 * We do a regex-targeted rewrite of just the {@code resourcePacks} line —
 * leaving every other key untouched so we don't accidentally clobber user
 * customisations like keybinds.
 */
public final class OptionsTxtMigration {

    private static final Logger LOG = LoggerFactory.getLogger(OptionsTxtMigration.class);

    /** Match a line like {@code resourcePacks:["vanilla","fabric","file/foo.zip"]} */
    private static final Pattern RESOURCE_PACKS_LINE =
            Pattern.compile("^resourcePacks:\\[(?<body>.*)\\]\\s*$");

    /** Add "file/<packFileName>" to the resourcePacks list in options.txt
     *  if it's not already present. No-op if options.txt doesn't exist
     *  (clean install — first run will create it; the modpack ships a
     *  pre-configured options.txt with the pack already enabled).
     *
     *  @param gameDir       the .minecraft-style game directory
     *  @param packFileName  the bare zip filename (e.g. "NYLERP-PACK.zip")
     *                       to ensure listed; the function prepends "file/"
     */
    public static void ensureResourcePackEnabled(Path gameDir, String packFileName) {
        Path opts = gameDir.resolve("options.txt");
        if (!Files.exists(opts)) {
            // Player hasn't launched MC yet — vanilla writes options.txt on
            // first close. The shipped options.txt from the modpack includes
            // the pack already, so there's nothing to migrate.
            return;
        }
        try {
            List<String> lines = new ArrayList<>(fr.nylerp.launcher.util.LectureTexte.lignes(opts));
            String entry = "\"file/" + packFileName + "\"";
            boolean changed = false;
            boolean foundLine = false;
            for (int i = 0; i < lines.size(); i++) {
                Matcher m = RESOURCE_PACKS_LINE.matcher(lines.get(i));
                if (!m.matches()) continue;
                foundLine = true;
                String body = m.group("body").trim();
                if (body.contains("file/" + packFileName)) {
                    return; // already enabled
                }
                String newBody = body.isEmpty() ? entry : body + "," + entry;
                lines.set(i, "resourcePacks:[" + newBody + "]");
                changed = true;
                break;
            }
            // No resourcePacks line at all — happens on a player who deleted
            // the line or on partial config files. Append a fresh one with
            // the vanilla baseline + our pack.
            if (!foundLine) {
                lines.add("resourcePacks:[\"vanilla\",\"fabric\"," + entry + "]");
                changed = true;
            }
            if (changed) {
                Files.write(opts, lines);
                LOG.info("options.txt: enabled resource pack file/{}", packFileName);
            }
        } catch (IOException e) {
            // Non-fatal: launch can proceed without the pack auto-enabled,
            // the player will just have to enable it manually once.
            LOG.warn("options.txt migration failed: {}", e.toString());
        }
    }

    /**
     * One-shot keybind defaults migration for EXISTING players (new players get
     * these from the shipped {@code options.txt}). Runs exactly once — tracked by
     * a marker file — so a player who later deliberately rebinds one of these keys
     * is never overridden on subsequent launches.
     *
     * <p>Changes applied once:
     * <ul>
     *   <li>{@code key.toms_storage.open_terminal} → unbound (annoying default on B)</li>
     *   <li>the six {@code key.pointblack.*} PointBlank keys → unbound</li>
     *   <li>{@code key.togglePerspective}: if currently Y → F5 (vanilla default)</li>
     * </ul>
     */
    private static final String[] UNBIND_KEYS = {
            "key_key.toms_storage.open_terminal",
            "key_key.pointblack.attachments",
            "key_key.pointblack.firemode",
            "key_key.pointblack.inspect",
            "key_key.pointblack.reload",
            "key_key.pointblack.scope_switch",
            "key_key.pointblack.settings",
            "key_key.disable_voice_chat",   // pas de touche pour désactiver le chat vocal
    };

    public static void applyKeybindDefaultsOnce(Path gameDir) {
        Path opts = gameDir.resolve("options.txt");
        Path marker = gameDir.resolve(".nyle_keybinds_v2");
        if (Files.exists(marker) || !Files.exists(opts)) {
            return; // already applied, or clean install (shipped options.txt already correct)
        }
        try {
            List<String> lines = new ArrayList<>(fr.nylerp.launcher.util.LectureTexte.lignes(opts));
            boolean changed = false;
            java.util.Set<String> unbind = new java.util.HashSet<>(java.util.Arrays.asList(UNBIND_KEYS));
            java.util.Set<String> present = new java.util.HashSet<>();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int colon = line.indexOf(':');
                if (colon <= 0) continue;
                String key = line.substring(0, colon);
                if (unbind.contains(key)) {
                    present.add(key);
                    if (!line.endsWith(":key.keyboard.unknown")) {
                        lines.set(i, key + ":key.keyboard.unknown");
                        changed = true;
                    }
                } else if (key.equals("key_key.togglePerspective")) {
                    // Y → F5 only (respect any other deliberate binding).
                    if (line.endsWith(":key.keyboard.y")) {
                        lines.set(i, "key_key.togglePerspective:key.keyboard.f5");
                        changed = true;
                    }
                } else if (key.equals("key_key.nylecontent.open_tool_menu")) {
                    // Menu Nyle : nouveau défaut = N.
                    present.add(key);
                    if (!line.endsWith(":key.keyboard.n")) {
                        lines.set(i, "key_key.nylecontent.open_tool_menu:key.keyboard.n");
                        changed = true;
                    }
                }
            }
            // Add lines for keys the player's options.txt doesn't list yet
            // (Minecraft would otherwise re-seed the mod default on next close).
            for (String key : UNBIND_KEYS) {
                if (!present.contains(key)) {
                    lines.add(key + ":key.keyboard.unknown");
                    changed = true;
                }
            }
            if (!present.contains("key_key.nylecontent.open_tool_menu")) {
                lines.add("key_key.nylecontent.open_tool_menu:key.keyboard.n");
                changed = true;
            }
            if (changed) {
                Files.write(opts, lines);
                LOG.info("options.txt: applied keybind defaults (Tom's/PointBlank unbound, perspective Y→F5)");
            }
            Files.writeString(marker, "1"); // run-once, even if nothing changed
        } catch (IOException e) {
            LOG.warn("options.txt keybind migration failed: {}", e.toString());
        }
    }

    private OptionsTxtMigration() {}
}
