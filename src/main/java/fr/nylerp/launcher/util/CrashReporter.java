package fr.nylerp.launcher.util;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Captures uncaught exceptions from anywhere in the launcher and writes them to
 * {@code ~/Library/Logs/NyleLauncher/crash-<timestamp>.log} (or platform equivalent). On the next
 * launcher start, scans this directory; if any crashes from a previous run are present, asks the
 * user for explicit consent before uploading them to a GitHub-hosted ingest endpoint.
 *
 * Privacy / legality:
 *   - No automatic upload. Ever.
 *   - The dialog shows the EXACT contents that would be sent (the user can read the log first).
 *   - Logs are wiped from disk after a successful upload OR if the user clicks "Ne pas envoyer".
 *   - Uploads contain anonymous data only — no MS access tokens, no UUIDs, no IP. We strip those
 *     fields via {@link #scrub(String)} before display + upload.
 *
 * Upload mechanism: POSTs to a GitHub Issues API endpoint via a server-side worker URL. We do
 * NOT bake a write token into the launcher (it would be public). Instead we use the public
 * GitHub Issues API in "anonymous" mode via a Cloudflare Worker proxy at
 * https://nylerp-crash.nyle-rp.workers.dev/submit (the worker holds the GitHub PAT).
 * If the worker isn't reachable we fall back to opening a pre-filled issue URL in the user's
 * browser (truly free, no infrastructure needed).
 */
public final class CrashReporter {
    private static final Logger LOG = LoggerFactory.getLogger(CrashReporter.class);

    private static final String UPLOAD_URL = "https://nylerp-crash.nyle-rp.workers.dev/submit";
    private static final String FALLBACK_ISSUE_URL =
        "https://github.com/NYL3E/NyleLauncher/issues/new?title=%s&body=%s&labels=crash-report,from-launcher";

    private static volatile Path crashDir;

    /** Install the global handler. Call once at app startup. */
    public static void install() {
        try {
            crashDir = resolveCrashDir();
            Files.createDirectories(crashDir);
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> writeCrash(t, e));
        } catch (Exception ex) {
            LOG.warn("[CrashReporter] install failed: {}", ex.toString());
        }
    }

    private static Path resolveCrashDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("mac")) return Paths.get(home, "Library", "Logs", "NyleLauncher");
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            return Paths.get(appdata != null ? appdata : home, "NyleLauncher", "logs");
        }
        return Paths.get(home, ".config", "NyleLauncher", "logs");
    }

    private static void writeCrash(Thread t, Throwable e) {
        try {
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path file = crashDir.resolve("crash-" + stamp + "-" + UUID.randomUUID() + ".log");

            StringBuilder sb = new StringBuilder();
            sb.append("# NyleLauncher crash report\n");
            sb.append("ts=").append(LocalDateTime.now()).append('\n');
            sb.append("os=").append(System.getProperty("os.name"))
              .append(' ').append(System.getProperty("os.version"))
              .append(' ').append(System.getProperty("os.arch")).append('\n');
            sb.append("java=").append(System.getProperty("java.version")).append('\n');
            sb.append("thread=").append(t.getName()).append('\n');
            sb.append("---\n");
            try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
                e.printStackTrace(pw);
                sb.append(sw);
            }
            String body = scrub(sb.toString());
            Files.writeString(file, body, StandardOpenOption.CREATE_NEW);
            LOG.error("[CrashReporter] wrote {}", file);
        } catch (Throwable t2) {
            // crash-while-handling-crash — last resort, write to stderr
            System.err.println("[CrashReporter] meta-failure: " + t2);
            e.printStackTrace();
        }
    }

    /**
     * Scrub sensitive substrings from a log body before persisting/uploading.
     * Conservative regex-based approach — better safe than sorry.
     */
    public static String scrub(String s) {
        if (s == null) return "";
        return s
            .replaceAll("(?i)access[_-]?token\\s*[=:]\\s*\\S+", "access_token=<redacted>")
            .replaceAll("(?i)refresh[_-]?token\\s*[=:]\\s*\\S+", "refresh_token=<redacted>")
            .replaceAll("(?i)bearer\\s+[A-Za-z0-9._\\-]+", "Bearer <redacted>")
            .replaceAll("[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}",
                "<uuid-redacted>")
            // IPv4
            .replaceAll("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b", "<ip-redacted>");
    }

    /** Called from main UI after JavaFX is up. Shows the consent dialog if any crashes are pending. */
    public static void promptIfPending() {
        if (crashDir == null) return;
        try (Stream<Path> stream = Files.list(crashDir)) {
            var pending = stream
                .filter(p -> p.getFileName().toString().startsWith("crash-"))
                .filter(p -> p.getFileName().toString().endsWith(".log"))
                .toList();
            if (pending.isEmpty()) return;
            // Concatenate
            StringBuilder all = new StringBuilder();
            for (Path p : pending) {
                all.append("==== ").append(p.getFileName()).append(" ====\n");
                try { all.append(Files.readString(p)).append("\n"); } catch (IOException ignored) {}
            }
            String body = scrub(all.toString());
            Platform.runLater(() -> showDialog(body, pending));
        } catch (IOException ignored) {}
    }

    private static void showDialog(String body, java.util.List<Path> files) {
        Stage st = new Stage();
        st.initModality(Modality.APPLICATION_MODAL);
        st.setTitle("NyleLauncher — rapport de crash");

        Label title = new Label("Le launcher a planté lors d'une session précédente.");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        Label sub = new Label("Veux-tu envoyer le log au dev pour qu'il puisse réparer ? "
            + "Le contenu exact ci-dessous est ce qui sera envoyé. Tu peux refuser sans conséquence.");
        sub.setWrapText(true);

        TextArea ta = new TextArea(body);
        ta.setEditable(false);
        ta.setPrefRowCount(18);
        ta.setStyle("-fx-font-family: 'Menlo','Consolas',monospace; -fx-font-size: 11px;");

        Button yes = new Button("Envoyer au dev (anonyme)");
        Button no = new Button("Ne pas envoyer");
        Button keep = new Button("Plus tard");

        yes.setStyle("-fx-background-color: #6e44ff; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 18; -fx-background-radius: 6;");
        no.setStyle("-fx-padding: 8 18;");
        keep.setStyle("-fx-padding: 8 18;");

        Label status = new Label("");

        yes.setOnAction(e -> {
            yes.setDisable(true); no.setDisable(true); keep.setDisable(true);
            status.setText("Envoi en cours…");
            uploadAsync(body).whenComplete((result, err) -> Platform.runLater(() -> {
                if (err == null && Boolean.TRUE.equals(result)) {
                    status.setText("✓ Envoyé. Merci !");
                    files.forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                    new java.util.Timer().schedule(new java.util.TimerTask() {
                        @Override public void run() { Platform.runLater(st::close); }
                    }, 1500);
                } else {
                    status.setText("Échec d'envoi. Ouverture d'une issue pré-remplie dans ton navigateur.");
                    openIssueInBrowser(body);
                    files.forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                    new java.util.Timer().schedule(new java.util.TimerTask() {
                        @Override public void run() { Platform.runLater(st::close); }
                    }, 1500);
                }
            }));
        });
        no.setOnAction(e -> {
            files.forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            st.close();
        });
        keep.setOnAction(e -> st.close());

        VBox buttons = new VBox(8);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        var row = new javafx.scene.layout.HBox(8, keep, no, yes);
        row.setAlignment(Pos.CENTER_RIGHT);
        buttons.getChildren().addAll(row, status);

        VBox root = new VBox(12, title, sub, ta, buttons);
        root.setPadding(new Insets(20));
        st.setScene(new Scene(root, 720, 540));
        st.show();
    }

    private static CompletableFuture<Boolean> uploadAsync(String body) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .build();
                String json = "{\"body\":" + jsonEscape(body) + ",\"version\":\""
                    + escape(fr.nylerp.launcher.config.Constants.APP_VERSION) + "\"}";
                HttpRequest req = HttpRequest.newBuilder(URI.create(UPLOAD_URL))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "NyleLauncher/" + fr.nylerp.launcher.config.Constants.APP_VERSION)
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                return resp.statusCode() >= 200 && resp.statusCode() < 300;
            } catch (Exception e) {
                LOG.warn("[CrashReporter] upload failed: {}", e.toString());
                return false;
            }
        });
    }

    private static void openIssueInBrowser(String body) {
        try {
            String title = java.net.URLEncoder.encode("[crash] auto", StandardCharsets.UTF_8);
            // GitHub URL length cap ~8 KB — truncate if necessary
            String trimmed = body.length() > 4000 ? body.substring(0, 4000) + "\n…[trimmed]…" : body;
            String b = java.net.URLEncoder.encode("```\n" + trimmed + "\n```", StandardCharsets.UTF_8);
            String url = String.format(FALLBACK_ISSUE_URL, title, b);
            java.awt.Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ignored) {}
    }

    private static String jsonEscape(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }
    private static String escape(String s) { return s == null ? "" : s.replace("\"", "\\\""); }

    private CrashReporter() {}
}
