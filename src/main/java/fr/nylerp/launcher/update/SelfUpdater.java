package fr.nylerp.launcher.update;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import fr.nylerp.launcher.config.Constants;
import fr.nylerp.launcher.util.Downloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Checks GitHub Releases for a newer launcher version.
 * Uses the public API — no token needed (repo is public).
 */
public final class SelfUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(SelfUpdater.class);
    private static final Gson GSON = new Gson();

    private static final String LATEST_API =
            "https://api.github.com/repos/NYL3E/NyleLauncher/releases/latest";

    public record Info(boolean hasUpdate, String latestTag, String currentTag, String releaseUrl) {}

    public static CompletableFuture<Info> check() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String json = Downloader.toString(LATEST_API);
                JsonObject release = GSON.fromJson(json, JsonObject.class);
                String latestTag = release.get("tag_name").getAsString();
                String htmlUrl   = release.get("html_url").getAsString();
                String current = "v" + Constants.APP_VERSION;
                boolean newer = compareVersions(latestTag, current) > 0;
                LOG.info("Version check: current={} latest={} newer={}", current, latestTag, newer);
                return new Info(newer, latestTag, current, htmlUrl);
            } catch (Exception e) {
                LOG.warn("Version check failed: {}", e.toString());
                return new Info(false, "", "v" + Constants.APP_VERSION, "");
            }
        });
    }

    /** Compare tags like "v0.1.2" vs "v0.1.0" — returns >0 if a > b, <0 if a < b, 0 if equal. */
    private static int compareVersions(String a, String b) {
        String ca = a.replaceFirst("^v", "");
        String cb = b.replaceFirst("^v", "");
        String[] pa = ca.split("\\.");
        String[] pb = cb.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int ia = i < pa.length ? parse(pa[i]) : 0;
            int ib = i < pb.length ? parse(pb[i]) : 0;
            if (ia != ib) return Integer.compare(ia, ib);
        }
        return 0;
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); }
        catch (Exception e) { return 0; }
    }

    private SelfUpdater() {}
}
