package fr.nylerp.launcher.annonces;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Faux points d'accès {@code /api/launcher/*} : corps, statut, ETag (304 sur {@code If-None-Match}
 * égal) et en-tête {@code Date} réglables, compteurs de requêtes et de 304.
 *
 * <p>Petit serveur HTTP/1.1 sur socket brute plutôt que {@code com.sun.net.httpserver} : celui-ci
 * impose son propre en-tête {@code Date} (l'heure réelle), or les tests doivent justement faire
 * dire au serveur une autre heure que celle du PC.
 */
final class FauxServeur implements AutoCloseable {

    static final class Reponse {
        volatile int statut = 200;
        volatile String corps = "{}";
        volatile String etag;
        /** null = pas d'en-tête Date. */
        volatile ZonedDateTime date;
    }

    private final ServerSocket socket;
    private final Thread boucle;
    final Map<String, Reponse> reponses = new ConcurrentHashMap<>();
    final Map<String, AtomicInteger> requetes = new ConcurrentHashMap<>();
    final Map<String, AtomicInteger> nonModifies = new ConcurrentHashMap<>();
    private volatile boolean ferme;

    FauxServeur() throws IOException {
        socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        boucle = new Thread(() -> {
            while (!ferme) {
                try (Socket s = socket.accept()) {
                    servir(s);
                } catch (IOException e) {
                    if (ferme) return;
                }
            }
        }, "faux-serveur-annonces");
        boucle.setDaemon(true);
        boucle.start();
    }

    private void servir(Socket s) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1));
        String ligne = in.readLine();
        if (ligne == null) return;
        String chemin = URI.create(ligne.split(" ")[1]).getPath().replaceFirst("^/api/launcher/", "");
        Map<String, String> entetes = new HashMap<>();
        for (String h; (h = in.readLine()) != null && !h.isEmpty(); ) {
            int i = h.indexOf(':');
            if (i > 0) entetes.put(h.substring(0, i).trim().toLowerCase(Locale.ROOT), h.substring(i + 1).trim());
        }
        requetes.computeIfAbsent(chemin, k -> new AtomicInteger()).incrementAndGet();
        Reponse r = reponses.get(chemin);
        StringBuilder rep = new StringBuilder();
        byte[] corps = new byte[0];
        int statut;
        if (r == null) {
            statut = 404;
        } else if (r.statut == 200 && r.etag != null && r.etag.equals(entetes.get("if-none-match"))) {
            nonModifies.computeIfAbsent(chemin, k -> new AtomicInteger()).incrementAndGet();
            statut = 304;
        } else {
            statut = r.statut;
            corps = r.corps.getBytes(StandardCharsets.UTF_8);
        }
        rep.append("HTTP/1.1 ").append(statut).append(statut == 200 ? " OK" : statut == 304 ? " Not Modified" : " Erreur").append("\r\n");
        if (r != null && r.date != null) rep.append("Date: ").append(DateTimeFormatter.RFC_1123_DATE_TIME.format(r.date)).append("\r\n");
        if (r != null && r.etag != null) rep.append("ETag: ").append(r.etag).append("\r\n");
        if (statut != 304) {
            rep.append("Content-Type: application/json; charset=utf-8\r\n");
            rep.append("Content-Length: ").append(corps.length).append("\r\n");
        }
        rep.append("Connection: close\r\n\r\n");
        OutputStream out = s.getOutputStream();
        out.write(rep.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(corps);
        out.flush();
    }

    Reponse pour(String chemin) { return reponses.computeIfAbsent(chemin, k -> new Reponse()); }

    int requetes(String chemin) { AtomicInteger a = requetes.get(chemin); return a == null ? 0 : a.get(); }

    int nonModifies(String chemin) { AtomicInteger a = nonModifies.get(chemin); return a == null ? 0 : a.get(); }

    String base() { return "http://127.0.0.1:" + socket.getLocalPort() + "/api/launcher/"; }

    URI uri(String chemin) { return URI.create(base() + chemin); }

    @Override public synchronized void close() {
        if (ferme) return;
        ferme = true;
        try { socket.close(); } catch (IOException ignored) { }
    }
}
