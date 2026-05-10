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
            List<String> lines = new ArrayList<>(Files.readAllLines(opts));
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

    private OptionsTxtMigration() {}
}
