package fr.nylerp.launcher.launch;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.nylerp.launcher.config.AppPaths;
import fr.nylerp.launcher.util.Downloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Downloads Fabric loader's libraries for a given (mcVersion, loaderVersion) and returns
 * the merged classpath + main class to pass to the JVM.
 */
public final class FabricInstaller {

    private static final Logger LOG = LoggerFactory.getLogger(FabricInstaller.class);
    private static final Gson GSON = new Gson();

    public record Result(String mainClass, List<Path> extraClasspath) {}

    public static Result install(String mcVersion, String loaderVersion) throws IOException {
        String url = "https://meta.fabricmc.net/v2/versions/loader/"
                + mcVersion + "/" + loaderVersion + "/profile/json";
        // Local d'abord : le profil d'un couple (Minecraft, loader) ne change jamais. Le re-télécharger à chaque « Jouer »
        // coûtait un aller-retour réseau (0,1 à 0,15 s mesurés) et faisait ÉCHOUER un lancement hors ligne alors que tout
        // est sur le disque. Re-téléchargé seulement s'il manque ou s'il est illisible.
        Path cache = AppPaths.rootDir().resolve("minecraft").resolve("versions")
                .resolve("fabric-loader-" + loaderVersion + "-" + mcVersion).resolve("profile.json");
        JsonObject profile = null;
        if (Files.exists(cache)) {
            try {
                profile = GSON.fromJson(Files.readString(cache), JsonObject.class);
                if (profile == null || !profile.has("mainClass") || !profile.has("libraries")) profile = null;
            } catch (Exception illisible) {
                profile = null;
            }
        }
        if (profile == null) {
            LOG.info("Fetching Fabric profile {}", url);
            String json = Downloader.toString(url);
            profile = GSON.fromJson(json, JsonObject.class);
            try {
                Files.createDirectories(cache.getParent());
                Files.writeString(cache, json);
            } catch (Exception ignored) {
                // pas de cache : on re-téléchargera la prochaine fois, comme avant
            }
        }

        String mainClass = profile.get("mainClass").getAsString();
        Path libraries = AppPaths.rootDir().resolve("minecraft").resolve("libraries");
        List<Path> cp = new ArrayList<>();
        for (JsonElement libEl : profile.getAsJsonArray("libraries")) {
            JsonObject lib = libEl.getAsJsonObject();
            String name = lib.get("name").getAsString();           // group:artifact:version
            String base = lib.has("url") ? lib.get("url").getAsString() : "https://maven.fabricmc.net/";
            if (!base.endsWith("/")) base += "/";
            Path relPath = mavenPath(name);
            Path dest = libraries.resolve(relPath);
            if (!Files.exists(dest)) {
                Files.createDirectories(dest.getParent());
                Downloader.toFile(base + relPath.toString().replace('\\', '/'), dest, null);
            }
            cp.add(dest);
        }
        return new Result(mainClass, cp);
    }

    /**
     * Converts a Maven coordinate "group:artifact:version" (optionally with :classifier)
     * to the standard Maven repo path "group/path/artifact/version/artifact-version.jar".
     */
    private static Path mavenPath(String coord) {
        String[] parts = coord.split(":");
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        String file = artifact + "-" + version + classifier + ".jar";
        return Path.of(group, artifact, version, file);
    }

    private FabricInstaller() {}
}
