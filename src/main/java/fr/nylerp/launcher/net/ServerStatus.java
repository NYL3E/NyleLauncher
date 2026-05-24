package fr.nylerp.launcher.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/**
 * Polls the Minecraft server's online player count and emits each
 * reading to the given callback. Hits the public mcsrvstat.us API
 * (no auth, no rate limiting for our volume) every 30 s so the UI
 * stays fresh without hammering the network.
 *
 * <p>The callback receives a non-negative count when the server is
 * online, or -1 when the server is offline / unreachable. The UI
 * decides how to display each case (we use "X JOUEURS EN LIGNE"
 * vs "HORS LIGNE").
 *
 * <p>Single static start() so the caller doesn't need to manage a
 * lifecycle — the executor is a daemon so it dies with the JVM.
 */
public final class ServerStatus {

    private ServerStatus() {}

    private static final String HOST = "play.nylerp.fr";
    private static final long REFRESH_SECONDS = 30;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static void start(IntConsumer onCount) {
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ServerStatus");
            t.setDaemon(true);
            return t;
        });
        exec.scheduleAtFixedRate(() -> tick(onCount), 0, REFRESH_SECONDS, TimeUnit.SECONDS);
    }

    private static void tick(IntConsumer onCount) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.mcsrvstat.us/3/" + HOST))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "NyleLauncher/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) { onCount.accept(-1); return; }
            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
            boolean online = json.has("online") && json.get("online").getAsBoolean();
            if (!online) { onCount.accept(-1); return; }
            JsonObject players = json.has("players") && json.get("players").isJsonObject()
                    ? json.getAsJsonObject("players") : null;
            int count = players != null && players.has("online")
                    ? players.get("online").getAsInt() : 0;
            onCount.accept(count);
        } catch (Throwable t) {
            // Network hiccup → display offline rather than freeze the
            // last reading. mcsrvstat.us occasionally rate-limits us in
            // bursts ; the next 30 s tick recovers.
            onCount.accept(-1);
        }
    }
}
