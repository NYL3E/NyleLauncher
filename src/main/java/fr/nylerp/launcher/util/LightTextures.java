package fr.nylerp.launcher.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import fr.nylerp.launcher.config.AppPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Pack « Textures légères » (owner 2026-07-23) : resource pack ÷2 (Decocraft/Yuushya/PointBlank)
 * livré par le modpack dans {@code resourcepacks/}, ACTIVABLE depuis les réglages du launcher pour
 * les GPU anciens dont l'atlas dépasse la limite matérielle (cas Intel HD 4600 : « Requested atlas
 * size 16384x8192 exceeds maximum of 8192x8192 » → crash au chargement).
 *
 * <p>L'activation = présence de {@code "file/<zip>"} dans la ligne {@code resourcePacks:[...]}
 * d'options.txt (ajouté en FIN de liste = priorité haute, il doit couvrir les textures des mods).
 * Best-effort : ne casse jamais un lancement.
 */
public final class LightTextures {

    public static final String PACK_FILE = "NyleRP-Textures-Legeres.zip";
    private static final String PACK_ENTRY = "file/" + PACK_FILE;
    private static final String LEGACY_ENTRY = "file/NYLERP-PACK.zip";
    private static final Gson GSON = new Gson();

    private LightTextures() {}

    public static boolean isEnabled() {
        try {
            return readPacks().contains(PACK_ENTRY);
        } catch (Throwable t) { return false; }
    }

    public static void setEnabled(boolean on) {
        try {
            List<String> packs = readPacks();
            packs.remove(PACK_ENTRY);
            if (on) packs.add(PACK_ENTRY);   // fin de liste = priorité la plus haute
            writePacks(packs);
        } catch (Throwable ignored) {}
    }

    /**
     * MIGRATION ONE-SHOT : l'ancien resource pack serveur ({@code NYLERP-PACK.zip}) est désormais
     * embarqué dans le mod {@code nylerp_pack} — la copie locale du zip et sa sélection dans
     * options.txt sont redondantes (assets en double + cycle de reload « pack manquant » chez
     * certains). On la retire UNE fois ; marqueur posé, plus jamais retouché ensuite.
     */
    public static void cleanupLegacyPack() {
        try {
            Path marker = AppPaths.launcherState().resolve("rpfix-nylerp-zip-v1.done");
            if (Files.exists(marker)) return;
            List<String> packs = readPacks();
            if (packs.remove(LEGACY_ENTRY)) writePacks(packs);
            Files.deleteIfExists(AppPaths.gameDir().resolve("resourcepacks").resolve("NYLERP-PACK.zip"));
            Files.writeString(marker, "done\n", StandardCharsets.UTF_8);
        } catch (Throwable ignored) {}
    }

    // ── options.txt : ligne resourcePacks:[...] ─────────────────────────────────

    private static Path options() { return AppPaths.gameDir().resolve("options.txt"); }

    private static List<String> readPacks() throws Exception {
        List<String> out = new ArrayList<>();
        Path opt = options();
        if (!Files.exists(opt)) { out.add("vanilla"); out.add("fabric"); return out; }
        for (String line : LectureTexte.lignes(opt)) {
            if (line.startsWith("resourcePacks:")) {
                JsonArray arr = JsonParser.parseString(line.substring("resourcePacks:".length())).getAsJsonArray();
                arr.forEach(e -> out.add(e.getAsString()));
                return out;
            }
        }
        out.add("vanilla"); out.add("fabric");
        return out;
    }

    private static void writePacks(List<String> packs) throws Exception {
        Path opt = options();
        List<String> lines = Files.exists(opt)
                ? new ArrayList<>(LectureTexte.lignes(opt))
                : new ArrayList<>();
        String newLine = "resourcePacks:" + GSON.toJson(packs);
        boolean replaced = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("resourcePacks:")) { lines.set(i, newLine); replaced = true; break; }
        }
        if (!replaced) lines.add(newLine);
        Files.write(opt, lines, StandardCharsets.UTF_8);
    }
}
