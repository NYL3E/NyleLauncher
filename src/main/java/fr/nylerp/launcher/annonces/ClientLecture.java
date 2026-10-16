package fr.nylerp.launcher.annonces;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Lecture GET d'un point d'accès JSON avec {@code If-None-Match} : une seule requête, délai 10 s,
 * aucune nouvelle tentative (le cycle suivant s'en charge). L'heure du serveur est relevée sur
 * l'en-tête {@code Date}, présent aussi sur les 304 sans corps.
 */
final class ClientLecture {

    /** Réponse brute : {@code corps} vaut null sur un 304. */
    record Reponse(int statut, String corps, String etag, Optional<OffsetDateTime> dateServeur) { }

    static final Duration DELAI = Duration.ofSeconds(10);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(DELAI)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    Reponse lire(URI uri, String etag) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri)
                .timeout(DELAI)
                .header("Accept", "application/json")
                .header("User-Agent", "NyleLauncher-annonces/1")
                .GET();
        if (etag != null && !etag.isBlank()) b.header("If-None-Match", etag);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String nouvelEtag = r.headers().firstValue("etag").orElse(null);
        Optional<OffsetDateTime> date = r.headers().firstValue("date").flatMap(ClientLecture::lireDateHttp);
        return new Reponse(r.statusCode(), r.statusCode() == 304 ? null : r.body(), nouvelEtag, date);
    }

    static Optional<OffsetDateTime> lireDateHttp(String valeur) {
        try {
            return Optional.of(ZonedDateTime.parse(valeur.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toOffsetDateTime());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
