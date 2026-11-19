package fr.nylerp.launcher.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.EOFException;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Un FAUX Discord : socket Unix dans un dossier temporaire, qui parle le protocole IPC comme l'application
 * (poignée de main, READY, réponse ou erreur à SET_ACTIVITY, PING, CLOSE). Les essais ne touchent jamais au
 * vrai Discord : le client ne reçoit que ce chemin.
 */
final class FauxDiscord implements AutoCloseable {

    /** Une trame reçue du client, avec sa connexion (1, 2…) et son heure d'arrivée. */
    record Recu(int connexion, int op, JsonObject json, long nanos) {
        boolean setActivity() { return op == Trames.FRAME && "SET_ACTIVITY".equals(json.get("cmd").getAsString()); }
        JsonObject args() { return json.getAsJsonObject("args"); }
        JsonObject activite() { return args().has("activity") ? args().getAsJsonObject("activity") : null; }
    }

    final Path chemin;
    final List<Recu> recus = new CopyOnWriteArrayList<>();
    final AtomicInteger connexions = new AtomicInteger();
    /** La poignée de main est refusée (trame CLOSE 4000), comme pour un identifiant inconnu. */
    volatile boolean refuser;
    /** SET_ACTIVITY est refusé (évènement ERROR) tant que l'activité contient « name ». */
    volatile boolean refuserNom;

    private final List<SocketChannel> clients = new CopyOnWriteArrayList<>();
    private volatile ServerSocketChannel serveur;

    FauxDiscord(Path chemin) {
        this.chemin = chemin;
    }

    FauxDiscord demarrer() throws IOException {
        Files.deleteIfExists(chemin);
        ServerSocketChannel s = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        s.bind(UnixDomainSocketAddress.of(chemin));
        serveur = s;
        Thread t = new Thread(() -> accepter(s), "faux-discord-accepte");
        t.setDaemon(true);
        t.start();
        return this;
    }

    /** Discord quitte : le serveur et toutes les connexions se ferment, la socket disparaît. */
    void disparaitre() throws IOException {
        ServerSocketChannel s = serveur;
        serveur = null;
        if (s != null) s.close();
        for (SocketChannel c : clients) {
            try { c.close(); } catch (IOException ignore) { }
        }
        clients.clear();
        Files.deleteIfExists(chemin);
    }

    /** Envoie un PING au dernier client connecté. */
    void ping(String json) throws IOException {
        envoyer(clients.get(clients.size() - 1), Trames.PING, json);
    }

    /** La première trame reçue qui vérifie {@code p}, en attendant au plus {@code ms}. */
    Recu attendre(Predicate<Recu> p, long ms) throws InterruptedException {
        long fin = System.nanoTime() + ms * 1_000_000L;
        while (System.nanoTime() < fin) {
            for (Recu r : recus) if (p.test(r)) return r;
            Thread.sleep(10);
        }
        for (Recu r : recus) if (p.test(r)) return r;
        throw new AssertionError("trame attendue jamais reçue ; reçues : " + recus);
    }

    long compter(Predicate<Recu> p) {
        return recus.stream().filter(p).count();
    }

    @Override
    public void close() throws IOException {
        disparaitre();
    }

    // ── Interne ──────────────────────────────────────────────────────────────

    private void accepter(ServerSocketChannel s) {
        try {
            while (s.isOpen()) {
                SocketChannel c = s.accept();
                int n = connexions.incrementAndGet();
                clients.add(c);
                Thread t = new Thread(() -> servir(c, n), "faux-discord-" + n);
                t.setDaemon(true);
                t.start();
            }
        } catch (IOException fini) {
            // serveur fermé (disparaitre)
        }
    }

    private void servir(SocketChannel c, int n) {
        try {
            while (true) {
                ByteBuffer entete = lireTout(c, 8);
                int op = entete.getInt(0);
                int longueur = entete.getInt(4);
                String texte = StandardCharsets.UTF_8.decode(lireTout(c, longueur)).toString();
                JsonObject json = JsonParser.parseString(texte).getAsJsonObject();
                Recu r = new Recu(n, op, json, System.nanoTime());
                recus.add(r);
                switch (op) {
                    case Trames.HANDSHAKE -> {
                        if (refuser) {
                            envoyer(c, Trames.CLOSE, "{\"code\":4000,\"message\":\"Invalid Client ID\"}");
                            c.close();
                            return;
                        }
                        envoyer(c, Trames.FRAME, "{\"cmd\":\"DISPATCH\",\"evt\":\"READY\",\"nonce\":null,\"data\":{\"v\":1,"
                                + "\"config\":{\"cdn_host\":\"cdn.discordapp.com\",\"api_endpoint\":\"//discord.com/api\","
                                + "\"environment\":\"production\"},\"user\":{\"id\":\"1\",\"username\":\"essai\"}}}");
                    }
                    case Trames.FRAME -> {
                        if (!r.setActivity()) continue;
                        String nonce = json.get("nonce").getAsString();
                        JsonObject a = r.activite();
                        if (refuserNom && a != null && a.has("name")) {
                            repondre(c, Trames.FRAME, "{\"cmd\":\"SET_ACTIVITY\",\"evt\":\"ERROR\",\"nonce\":\"" + nonce
                                    + "\",\"data\":{\"code\":4000,\"message\":\"child \\\"activity\\\" fails\"}}");
                        } else {
                            repondre(c, Trames.FRAME, "{\"cmd\":\"SET_ACTIVITY\",\"evt\":null,\"nonce\":\"" + nonce
                                    + "\",\"data\":" + (a == null ? "null" : a.toString()) + "}");
                        }
                    }
                    case Trames.CLOSE -> {
                        c.close();
                        return;
                    }
                    default -> { }
                }
            }
        } catch (IOException | RuntimeException fini) {
            // client parti
        }
    }

    private static ByteBuffer lireTout(SocketChannel c, int n) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN);
        while (b.hasRemaining()) if (c.read(b) < 0) throw new EOFException();
        return b.flip();
    }

    private static void envoyer(SocketChannel c, int op, String json) throws IOException {
        synchronized (c) {
            ByteBuffer b = ByteBuffer.wrap(Trames.encoder(op, json));
            while (b.hasRemaining()) c.write(b);
        }
    }

    /** Une réponse : comme Discord, on continue de lire ce que le client a déjà envoyé même s'il ne lit plus. */
    private static void repondre(SocketChannel c, int op, String json) {
        try { envoyer(c, op, json); } catch (IOException clientParti) { /* sa trame CLOSE est peut-être déjà là */ }
    }
}
