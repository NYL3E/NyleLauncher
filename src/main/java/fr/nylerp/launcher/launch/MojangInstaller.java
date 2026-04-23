package fr.nylerp.launcher.launch;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.nylerp.launcher.config.AppPaths;
import fr.nylerp.launcher.util.Downloader;
import fr.nylerp.launcher.util.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Downloads the vanilla Minecraft 1.21.1 artefacts + assets from piston-meta.mojang.com,
 * reproducing the layout vanilla launcher produces: versions/{v}/{v}.json, {v}.jar,
 * libraries/**, assets/indexes/{id}.json, assets/objects/**.
 *
 * Returns the parsed vanilla version JSON for downstream merging with Fabric.
 */
public final class MojangInstaller {

    private static final Logger LOG = LoggerFactory.getLogger(MojangInstaller.class);
    private static final Gson GSON = new Gson();

    public static JsonObject install(String mcVersion, Downloader.Progress progress) throws IOException {
        Path root = AppPaths.rootDir().resolve("minecraft");
        Path versionsDir = root.resolve("versions").resolve(mcVersion);
        Files.createDirectories(versionsDir);

        // 1. Version manifest
        LOG.info("Fetching version manifest…");
        String manifestJson = Downloader.toString("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
        JsonObject manifest = GSON.fromJson(manifestJson, JsonObject.class);
        String versionJsonUrl = null;
        for (JsonElement el : manifest.getAsJsonArray("versions")) {
            JsonObject v = el.getAsJsonObject();
            if (mcVersion.equals(v.get("id").getAsString())) {
                versionJsonUrl = v.get("url").getAsString();
                break;
            }
        }
        if (versionJsonUrl == null) {
            throw new IOException("Minecraft version not found: " + mcVersion);
        }

        // 2. Version JSON
        Path versionJsonPath = versionsDir.resolve(mcVersion + ".json");
        String versionJson = Downloader.toString(versionJsonUrl);
        Files.writeString(versionJsonPath, versionJson);
        JsonObject version = GSON.fromJson(versionJson, JsonObject.class);

        // 3. Client JAR
        JsonObject clientDl = version.getAsJsonObject("downloads").getAsJsonObject("client");
        Path clientJar = versionsDir.resolve(mcVersion + ".jar");
        downloadIfNeeded(clientDl.get("url").getAsString(),
                         clientJar,
                         clientDl.get("sha1").getAsString(),
                         progress);

        // 4. Libraries
        JsonArray libs = version.getAsJsonArray("libraries");
        Path librariesDir = root.resolve("libraries");
        int i = 0, n = libs.size();
        for (JsonElement libEl : libs) {
            i++;
            JsonObject lib = libEl.getAsJsonObject();
            if (!ruleAllow(lib)) continue;
            JsonObject dl = lib.getAsJsonObject("downloads");
            if (dl == null) continue;
            JsonObject artifact = dl.getAsJsonObject("artifact");
            if (artifact != null) {
                String path = artifact.get("path").getAsString();
                String sha1 = artifact.get("sha1").getAsString();
                String url = artifact.get("url").getAsString();
                Path dest = librariesDir.resolve(path);
                downloadIfNeeded(url, dest, sha1, null);
            }
            if (progress != null) progress.onBytes(i, n);
        }

        // 5. Assets index
        JsonObject assetIndex = version.getAsJsonObject("assetIndex");
        String idxId = assetIndex.get("id").getAsString();
        Path idxPath = root.resolve("assets").resolve("indexes").resolve(idxId + ".json");
        downloadIfNeeded(assetIndex.get("url").getAsString(),
                         idxPath,
                         assetIndex.get("sha1").getAsString(),
                         null);

        // 6. Assets objects
        JsonObject idx = GSON.fromJson(Files.readString(idxPath), JsonObject.class);
        JsonObject objects = idx.getAsJsonObject("objects");
        int j = 0, m = objects.size();
        for (String key : objects.keySet()) {
            j++;
            JsonObject obj = objects.getAsJsonObject(key);
            String hash = obj.get("hash").getAsString();
            String sub = hash.substring(0, 2);
            Path dest = root.resolve("assets").resolve("objects").resolve(sub).resolve(hash);
            String url = "https://resources.download.minecraft.net/" + sub + "/" + hash;
            downloadIfNeeded(url, dest, hash, null);
            if (progress != null && j % 20 == 0) progress.onBytes(j, m);
        }
        if (progress != null) progress.onBytes(m, m);

        return version;
    }

    private static boolean ruleAllow(JsonObject lib) {
        if (!lib.has("rules")) return true;
        boolean allow = false;
        String curOs = osName();
        for (JsonElement r : lib.getAsJsonArray("rules")) {
            JsonObject rule = r.getAsJsonObject();
            String action = rule.get("action").getAsString();
            boolean matches = true;
            if (rule.has("os")) {
                JsonObject os = rule.getAsJsonObject("os");
                if (os.has("name")) matches = curOs.equals(os.get("name").getAsString());
            }
            if (matches) allow = "allow".equals(action);
        }
        return allow;
    }

    private static String osName() {
        String n = System.getProperty("os.name").toLowerCase();
        if (n.contains("win")) return "windows";
        if (n.contains("mac") || n.contains("darwin")) return "osx";
        return "linux";
    }

    private static void downloadIfNeeded(String url, Path dest, String expectedSha1,
                                         Downloader.Progress p) throws IOException {
        if (Files.exists(dest) && sha1Matches(dest, expectedSha1)) return;
        Files.createDirectories(dest.getParent());
        Downloader.toFile(url, dest, p);
    }

    private static boolean sha1Matches(Path file, String expected) {
        if (expected == null) return true;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[32 * 1024];
            try (var in = Files.newInputStream(file)) {
                int r;
                while ((r = in.read(buf)) > 0) md.update(buf, 0, r);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString().equalsIgnoreCase(expected);
        } catch (Exception e) {
            return false;
        }
    }

    public static List<Path> classpath(JsonObject version) {
        Path libraries = AppPaths.rootDir().resolve("minecraft").resolve("libraries");
        List<Path> out = new ArrayList<>();
        for (JsonElement libEl : version.getAsJsonArray("libraries")) {
            JsonObject lib = libEl.getAsJsonObject();
            if (!ruleAllow(lib)) continue;
            JsonObject dl = lib.getAsJsonObject("downloads");
            if (dl == null) continue;
            JsonObject artifact = dl.getAsJsonObject("artifact");
            if (artifact != null) out.add(libraries.resolve(artifact.get("path").getAsString()));
        }
        return out;
    }

    private MojangInstaller() {}
}
