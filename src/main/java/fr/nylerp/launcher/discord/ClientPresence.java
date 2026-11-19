package fr.nylerp.launcher.discord;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Le client Rich Presence : UN fil démon qui fait tout (connexion, poignée de main, READY, SET_ACTIVITY,
 * PING/PONG, reconnexion, fermeture), pour que rien ne puisse bloquer l'interface ni le lancement du jeu.
 *
 * <h2>Règles</h2>
 * <ul>
 *   <li>Discord absent, fermé, relancé, ou qui refuse : nouvel essai calme toutes les 20 à 30 s ;
 *       une seule ligne de journal par changement d'état ; toute exception est contenue dans le fil.</li>
 *   <li>Limite de Discord : 5 mises à jour d'activité par 20 s. On en envoie au plus une toutes les 5 s, et
 *       des changements rapprochés sont regroupés : seul le dernier état part.</li>
 *   <li>Identifiant d'application vide ou réglage éteint : aucune connexion ; s'il y en avait une, l'activité
 *       est effacée puis la connexion fermée (trame CLOSE) aussitôt.</li>
 * </ul>
 * Les fournisseurs (identifiant, réglage, activité) sont relus à chaque tour : {@link #reveiller()} après
 * un changement les fait relire tout de suite.
 *
 * <p>Partagé tel quel avec le mod client {@code nylepresence} (même fichier, à garder identique).
 */
public final class ClientPresence {

    private static final Logger LOG = LoggerFactory.getLogger("Discord");

    /** Durées en millisecondes (les essais les raccourcissent). */
    public record Reglages(long reessaiMinMs, long reessaiMaxMs, long ecartMajMs, long periodeMs,
                    long attenteReadyMs, long sommeilInerteMs) {
        public static final Reglages NORMAUX = new Reglages(20_000, 30_000, 5_000, 500, 5_000, 30_000);
    }

    public enum EtatConnexion { DEMARRAGE, INERTE, COUPEE, ABSENT, ECHEC, REFUS, CONNECTE, PERDUE, ARRETEE }

    /** Discord a refusé la poignée de main (identifiant inconnu…) : inutile d'essayer les autres canaux. */
    private static final class Refus extends IOException {
        Refus(String motif) { super(motif); }
    }

    private final Supplier<List<String>> chemins;
    private final Supplier<String> identifiant;
    private final BooleanSupplier actif;
    private final Supplier<JsonObject> activite;
    private final Reglages r;
    private final long pid = ProcessHandle.current().pid();
    private final Random hasard = new Random();

    private final Object verrou = new Object();
    private boolean reveil;                 // sous verrou
    private volatile boolean arret;
    private volatile EtatConnexion etat = EtatConnexion.DEMARRAGE;
    private Thread fil;

    // ── État du fil : seul le fil de l'activité y touche ─────────────────────
    private Canal canal;
    private Trames.Lecteur lecteur;
    private String idConnecte;
    private JsonObject envoyee;             // dernière activité posée (null = aucune)
    private boolean aRenvoyer;
    private int niveauRepli;
    private String nonceEnAttente;
    private String dernierRefusActivite;
    private long derniereMaj;
    private long prochainEssai;

    public ClientPresence(Supplier<List<String>> chemins, Supplier<String> identifiant, BooleanSupplier actif,
                   Supplier<JsonObject> activite, Reglages reglages) {
        this.chemins = chemins;
        this.identifiant = identifiant;
        this.actif = actif;
        this.activite = activite;
        this.r = reglages;
    }

    public synchronized void demarrer() {
        if (fil != null) return;
        fil = new Thread(this::boucle, "nyle-discord");
        fil.setDaemon(true);
        fil.start();
    }

    /** Fait relire tout de suite l'identifiant, le réglage et l'activité. N'importe quel fil. */
    public void reveiller() {
        synchronized (verrou) {
            reveil = true;
            verrou.notifyAll();
        }
    }

    /**
     * Efface l'activité, ferme la connexion et arrête le fil. N'attend pas plus de {@code attenteMs} :
     * le fil est démon, il ne retient jamais la fermeture du launcher.
     *
     * @return vrai si le fil est terminé
     */
    public boolean arreter(long attenteMs) {
        arret = true;
        reveiller();
        Thread f;
        synchronized (this) { f = fil; }
        if (f == null || f == Thread.currentThread()) return true;
        try { f.join(attenteMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return !f.isAlive();
    }

    public EtatConnexion etat() { return etat; }

    // ── Le fil ───────────────────────────────────────────────────────────────

    private void boucle() {
        try {
            while (!arret) {
                long attente;
                try {
                    attente = tour();
                } catch (Throwable t) {
                    abandonner(EtatConnexion.PERDUE, raison(t));
                    attente = Math.max(r.periodeMs(), prochainEssai - maintenant());
                }
                attendre(attente);
            }
        } catch (Throwable t) {
            LOG.warn("Discord : activité arrêtée sur une erreur inattendue : {}", t.toString());
        } finally {
            boolean connecte = canal != null;
            try { fermerProprement(); } catch (Throwable ignore) { }
            if (connecte) LOG.info("Discord : activité effacée, connexion fermée");
            etat = EtatConnexion.ARRETEE;
        }
    }

    /** Un tour de la boucle ; rend le temps d'attente avant le suivant (ms). */
    private long tour() throws IOException {
        String id = idValide(lireIdentifiant());
        boolean voulu = id != null && lireActif();
        if (canal != null && (!voulu || !id.equals(idConnecte))) fermerProprement();
        if (!voulu) {
            noter(id == null ? EtatConnexion.INERTE : EtatConnexion.COUPEE, null);
            prochainEssai = 0; // rallumé : premier essai tout de suite
            return r.sommeilInerteMs();
        }
        if (canal == null) {
            long t = maintenant();
            if (t < prochainEssai) return prochainEssai - t;
            if (!connecter(id)) {
                prochainEssai = maintenant() + reessai();
                return prochainEssai - maintenant();
            }
        }
        lireTrames();
        if (canal == null) return Math.max(r.periodeMs(), prochainEssai - maintenant());

        JsonObject voulue = lireActivite();
        if (aRenvoyer || !Objects.equals(voulue, envoyee)) {
            long depuis = maintenant() - derniereMaj;
            if (depuis < r.ecartMajMs()) return Math.min(r.periodeMs(), r.ecartMajMs() - depuis);
            envoyer(voulue);
        }
        return r.periodeMs();
    }

    private boolean connecter(String id) {
        String raisonEchec = null;
        boolean ouvert = false;
        for (String chemin : chemins.get()) {
            if (arret) return false;
            Canal c;
            try {
                c = Canal.ouvrir(chemin);
            } catch (NoSuchFileException absent) {
                continue;
            } catch (IOException orphelin) {
                continue; // socket laissée par un Discord planté, tube occupé : le suivant
            }
            ouvert = true;
            try {
                Trames.Lecteur l = poigneeDeMain(c, id);
                canal = c;
                lecteur = l;
                idConnecte = id;
                envoyee = null;
                aRenvoyer = false;
                niveauRepli = 0;
                nonceEnAttente = null;
                derniereMaj = maintenant() - r.ecartMajMs();
                noter(EtatConnexion.CONNECTE, nomCanal(chemin));
                return true;
            } catch (Refus refus) {
                c.close();
                noter(EtatConnexion.REFUS, refus.getMessage());
                return false;
            } catch (IOException | RuntimeException e) {
                c.close();
                raisonEchec = raison(e);
            }
        }
        noter(ouvert ? EtatConnexion.ECHEC : EtatConnexion.ABSENT, raisonEchec);
        return false;
    }

    private Trames.Lecteur poigneeDeMain(Canal c, String id) throws IOException {
        JsonObject hs = new JsonObject();
        hs.addProperty("v", 1);
        hs.addProperty("client_id", id);
        c.ecrire(Trames.encoder(Trames.HANDSHAKE, hs.toString()));
        Trames.Lecteur l = new Trames.Lecteur();
        long limite = maintenant() + r.attenteReadyMs();
        while (maintenant() < limite) {
            if (arret) throw new IOException("arrêt du launcher");
            Trames.Trame t = l.suivante(c);
            if (t == null) {
                dormir(20);
                continue;
            }
            switch (t.op()) {
                case Trames.FRAME -> {
                    JsonObject o = objet(t.json());
                    String evt = texte(o, "evt");
                    if ("READY".equals(evt)) return l;
                    if ("ERROR".equals(evt)) throw new Refus(motif(o));
                }
                case Trames.CLOSE -> throw new Refus(motif(objet(t.json())));
                case Trames.PING -> c.ecrire(Trames.encoder(Trames.PONG, t.json()));
                default -> { }
            }
        }
        throw new IOException("pas de READY après " + r.attenteReadyMs() + " ms");
    }

    private void lireTrames() throws IOException {
        for (int i = 0; i < 64 && canal != null; i++) {
            Trames.Trame t = lecteur.suivante(canal);
            if (t == null) return;
            switch (t.op()) {
                case Trames.PING -> canal.ecrire(Trames.encoder(Trames.PONG, t.json()));
                case Trames.CLOSE -> abandonner(EtatConnexion.PERDUE, "Discord a fermé la connexion : " + motif(objet(t.json())));
                case Trames.FRAME -> reponse(objet(t.json()));
                default -> { }
            }
        }
    }

    /** Réponse de Discord à une commande : seule une erreur sur notre dernière activité nous intéresse. */
    private void reponse(JsonObject o) {
        if (o == null || !"ERROR".equals(texte(o, "evt"))) return;
        String nonce = texte(o, "nonce");
        if (nonceEnAttente == null || !nonceEnAttente.equals(nonce)) return;
        nonceEnAttente = null;
        String motif = motif(o);
        if (niveauRepli < 2) {
            niveauRepli++;
            aRenvoyer = true; // la même activité, simplifiée (Activite.repli)
        }
        if (!motif.equals(dernierRefusActivite)) {
            dernierRefusActivite = motif;
            LOG.info("Discord : activité refusée ({}){}", motif, aRenvoyer ? ", nouvel essai simplifié" : "");
        }
    }

    private void envoyer(JsonObject voulue) throws IOException {
        String nonce = UUID.randomUUID().toString();
        canal.ecrire(Trames.encoder(Trames.FRAME, commande(Activite.repli(voulue, niveauRepli), nonce)));
        boolean change = !Objects.equals(voulue, envoyee);
        envoyee = voulue;
        aRenvoyer = false;
        nonceEnAttente = nonce;
        derniereMaj = maintenant();
        if (change) LOG.info("Discord : activité « {} »", Activite.resume(voulue));
    }

    /** {@code SET_ACTIVITY} ; sans activité, Discord efface celle du processus (comme discord-rpc). */
    private String commande(JsonObject activiteAPoser, String nonce) {
        JsonObject args = new JsonObject();
        args.addProperty("pid", pid);
        if (activiteAPoser != null) args.add("activity", activiteAPoser);
        JsonObject c = new JsonObject();
        c.addProperty("cmd", "SET_ACTIVITY");
        c.add("args", args);
        c.addProperty("nonce", nonce);
        return c.toString();
    }

    /** Efface l'activité posée puis envoie CLOSE et ferme le canal. Ne lève rien. */
    private void fermerProprement() {
        Canal c = canal;
        canal = null;
        lecteur = null;
        idConnecte = null;
        if (c == null) return;
        try {
            if (envoyee != null) c.ecrire(Trames.encoder(Trames.FRAME, commande(null, UUID.randomUUID().toString())));
            c.ecrire(Trames.encoder(Trames.CLOSE, "{}"));
        } catch (Throwable ignore) {
            // Discord parti entre-temps : il efface de lui-même l'activité d'un client déconnecté
        }
        envoyee = null;
        c.close();
    }

    /** Connexion perdue (Discord fermé, relancé, figé) : on ferme sans rien envoyer et on réessaiera plus tard. */
    private void abandonner(EtatConnexion nouvel, String raison) {
        Canal c = canal;
        canal = null;
        lecteur = null;
        idConnecte = null;
        envoyee = null;
        if (c != null) c.close();
        noter(nouvel, raison);
        prochainEssai = maintenant() + reessai();
    }

    // ── Journal : une ligne par changement d'état ───────────────────────────

    private void noter(EtatConnexion nouvel, String detail) {
        if (nouvel == etat) return;
        etat = nouvel;
        switch (nouvel) {
            case INERTE -> LOG.info("Discord : pas d'identifiant d'application, activité inerte");
            case COUPEE -> LOG.info("Discord : activité coupée dans les réglages");
            case ABSENT -> LOG.info("Discord : application introuvable (fermée ou absente), nouvel essai toutes les {} à {} s",
                    r.reessaiMinMs() / 1000, r.reessaiMaxMs() / 1000);
            case ECHEC -> LOG.info("Discord : connexion impossible ({}), nouvel essai plus tard", detail);
            case REFUS -> LOG.info("Discord : connexion refusée ({}), nouvel essai plus tard", detail);
            case CONNECTE -> LOG.info("Discord : connecté ({})", detail);
            case PERDUE -> LOG.info("Discord : connexion perdue ({}), nouvel essai plus tard", detail);
            default -> { }
        }
    }

    // ── Outils ───────────────────────────────────────────────────────────────

    private String lireIdentifiant() {
        try { return identifiant.get(); } catch (Throwable t) { return null; }
    }

    private boolean lireActif() {
        try { return actif.getAsBoolean(); } catch (Throwable t) { return false; }
    }

    private JsonObject lireActivite() {
        try { return activite.get(); } catch (Throwable t) { return null; }
    }

    /** Un identifiant d'application Discord (nombre de 15 à 25 chiffres), ou null. */
    public static String idValide(String id) {
        if (id == null) return null;
        String t = id.trim();
        if (t.length() < 15 || t.length() > 25) return null;
        for (int i = 0; i < t.length(); i++) if (t.charAt(i) < '0' || t.charAt(i) > '9') return null;
        return t;
    }

    private long reessai() {
        long ecart = Math.max(0, r.reessaiMaxMs() - r.reessaiMinMs());
        return r.reessaiMinMs() + (ecart == 0 ? 0 : (long) (hasard.nextDouble() * ecart));
    }

    private void attendre(long ms) {
        if (ms <= 0) return;
        synchronized (verrou) {
            long fin = System.nanoTime() + ms * 1_000_000L;
            while (!reveil && !arret) {
                long reste = (fin - System.nanoTime()) / 1_000_000L;
                if (reste <= 0) break;
                try { verrou.wait(reste); } catch (InterruptedException ignore) { /* seul « arret » arrête le fil */ }
            }
            reveil = false;
        }
    }

    private static void dormir(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignore) { /* la boucle regarde « arret » */ }
    }

    private static long maintenant() {
        return System.nanoTime() / 1_000_000L;
    }

    private static String nomCanal(String chemin) {
        int i = Math.max(chemin.lastIndexOf('/'), chemin.lastIndexOf('\\'));
        return i >= 0 ? chemin.substring(i + 1) : chemin;
    }

    private static String raison(Throwable t) {
        String m = t.getMessage();
        return t.getClass().getSimpleName() + (m == null || m.isBlank() ? "" : " : " + m);
    }

    private static JsonObject objet(String json) {
        try {
            JsonElement e = JsonParser.parseString(json);
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String texte(JsonObject o, String cle) {
        if (o == null) return null;
        JsonElement e = o.get(cle);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    /** « code message » d'une erreur de Discord (dans {@code data} ou à la racine). */
    private static String motif(JsonObject o) {
        if (o == null) return "sans motif";
        JsonObject d = o.has("data") && o.get("data").isJsonObject() ? o.getAsJsonObject("data") : o;
        String code = texte(d, "code");
        String message = texte(d, "message");
        String m = ((code == null ? "" : code + " ") + (message == null ? "" : message)).trim();
        return m.isEmpty() ? "sans motif" : m;
    }
}
