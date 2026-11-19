package fr.nylerp.launcher.discord;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Le client Rich Presence contre un FAUX Discord (socket Unix temporaire) : poignée de main, READY,
 * SET_ACTIVITY conforme, PING/PONG, Discord qui part et revient, réglage éteint, identifiant vide,
 * refus, regroupement des mises à jour, fermeture propre. Jamais le vrai Discord : le client ne connaît
 * que le chemin du faux.
 */
@Timeout(30)
class ClientPresenceTest {

    private static final String ID = "123456789012345678";
    private static final long DEBUT = 1_763_500_000L;
    /** Durées courtes : réessai 150 à 250 ms, une mise à jour par 100 ms au plus, tours de 20 ms. */
    private static final ClientPresence.Reglages RAPIDES = new ClientPresence.Reglages(150, 250, 100, 20, 2000, 100);

    @TempDir Path dossier;
    private final List<ClientPresence> clients = new ArrayList<>();
    private FauxDiscord faux;

    @AfterEach
    void fermer() throws Exception {
        for (ClientPresence c : clients) c.arreter(2000);
        if (faux != null) faux.close();
    }

    private static JsonObject enJeu(boolean pokenyle) {
        return Activite.pour(new Activite.Situation(Activite.Phase.EN_JEU, false, pokenyle, DEBUT, false, false));
    }

    private Path canal() { return dossier.resolve("discord-ipc-0"); }

    private FauxDiscord faux() throws Exception {
        faux = new FauxDiscord(canal()).demarrer();
        return faux;
    }

    private ClientPresence client(AtomicReference<String> id, AtomicBoolean actif, AtomicReference<JsonObject> activite,
                                  ClientPresence.Reglages r) {
        ClientPresence c = new ClientPresence(() -> List.of(canal().toString()), id::get, actif::get, activite::get, r);
        clients.add(c);
        c.demarrer();
        return c;
    }

    private ClientPresence client(JsonObject activite) {
        return client(new AtomicReference<>(ID), new AtomicBoolean(true), new AtomicReference<>(activite), RAPIDES);
    }

    private static void attendreEtat(ClientPresence c, ClientPresence.EtatConnexion e, long ms) throws InterruptedException {
        long fin = System.currentTimeMillis() + ms;
        while (c.etat() != e && System.currentTimeMillis() < fin) Thread.sleep(10);
        assertEquals(e, c.etat());
    }

    @Test
    void poigneeDeMainReadyPuisActiviteConforme() throws Exception {
        FauxDiscord f = faux();
        ClientPresence c = client(enJeu(true));

        FauxDiscord.Recu hs = f.attendre(r -> r.op() == Trames.HANDSHAKE, 3000);
        assertEquals(1, hs.json().get("v").getAsInt());
        assertEquals(ID, hs.json().get("client_id").getAsString());

        FauxDiscord.Recu set = f.attendre(FauxDiscord.Recu::setActivity, 3000);
        assertEquals(ProcessHandle.current().pid(), set.args().get("pid").getAsLong());
        assertFalse(set.json().get("nonce").getAsString().isBlank(), "chaque commande porte un nonce");
        JsonObject a = set.activite();
        assertEquals("NyleLauncher", a.get("name").getAsString());
        assertEquals(0, a.get("type").getAsInt());
        assertEquals("En jeu sur Pokényle", a.get("details").getAsString());
        assertEquals(DEBUT, a.getAsJsonObject("timestamps").get("start").getAsLong());
        assertEquals("logo", a.getAsJsonObject("assets").get("large_image").getAsString());
        assertEquals("NyleLauncher", a.getAsJsonObject("assets").get("large_text").getAsString());
        JsonObject bouton = a.getAsJsonArray("buttons").get(0).getAsJsonObject();
        assertEquals("Rejoindre le serveur", bouton.get("label").getAsString());
        assertEquals("https://pokerayou.academy/", bouton.get("url").getAsString());
        assertEquals(ClientPresence.EtatConnexion.CONNECTE, c.etat());
        assertEquals(1, f.compter(FauxDiscord.Recu::setActivity), "une seule mise à jour pour un état stable");
    }

    @Test
    void repondAuPingParUnPong() throws Exception {
        FauxDiscord f = faux();
        client(enJeu(true));
        f.attendre(FauxDiscord.Recu::setActivity, 3000);
        f.ping("{\"essai\":42}");
        FauxDiscord.Recu pong = f.attendre(r -> r.op() == Trames.PONG, 3000);
        assertEquals(42, pong.json().get("essai").getAsInt(), "le PONG renvoie le contenu du PING");
    }

    @Test
    void seReconnecteQuandDiscordRevient() throws Exception {
        FauxDiscord f = faux();
        ClientPresence c = client(enJeu(false));
        f.attendre(r -> r.connexion() == 1 && r.setActivity(), 3000);

        f.disparaitre();
        attendreEtat(c, ClientPresence.EtatConnexion.PERDUE, 3000);
        Thread.sleep(400); // au moins un essai pendant l'absence
        f.demarrer();

        FauxDiscord.Recu set = f.attendre(r -> r.connexion() == 2 && r.setActivity(), 5000);
        assertEquals("En jeu sur NyleRP", set.activite().get("details").getAsString(), "l'activité est reposée après le retour");
        assertEquals(ClientPresence.EtatConnexion.CONNECTE, c.etat());
    }

    @Test
    void reglageEteintEffaceLActiviteEtFermeAussitot() throws Exception {
        FauxDiscord f = faux();
        AtomicBoolean actif = new AtomicBoolean(true);
        ClientPresence c = client(new AtomicReference<>(ID), actif, new AtomicReference<>(enJeu(true)), RAPIDES);
        f.attendre(r -> r.connexion() == 1 && r.setActivity(), 3000);

        long avant = System.nanoTime();
        actif.set(false);
        c.reveiller();
        FauxDiscord.Recu efface = f.attendre(r -> r.connexion() == 1 && r.setActivity() && r.activite() == null, 2000);
        FauxDiscord.Recu close = f.attendre(r -> r.connexion() == 1 && r.op() == Trames.CLOSE, 2000);
        assertTrue(close.nanos() >= efface.nanos(), "l'activité est effacée AVANT la fermeture");
        assertTrue((efface.nanos() - avant) / 1_000_000 < 1000, "aussitôt, sans attendre un réessai");
        attendreEtat(c, ClientPresence.EtatConnexion.COUPEE, 2000);

        Thread.sleep(500);
        assertEquals(1, f.connexions.get(), "réglage éteint : aucune nouvelle connexion");

        actif.set(true);
        c.reveiller();
        f.attendre(r -> r.connexion() == 2 && r.setActivity(), 3000);
    }

    @Test
    void identifiantVideAucuneConnexion() throws Exception {
        FauxDiscord f = faux();
        AtomicReference<String> id = new AtomicReference<>("");
        ClientPresence c = client(id, new AtomicBoolean(true), new AtomicReference<>(enJeu(true)), RAPIDES);
        Thread.sleep(700);
        assertEquals(0, f.connexions.get(), "identifiant vide : aucune tentative");
        assertEquals(ClientPresence.EtatConnexion.INERTE, c.etat());

        id.set("pas-un-identifiant");
        c.reveiller();
        Thread.sleep(300);
        assertEquals(0, f.connexions.get(), "identifiant invalide : traité comme vide");

        id.set(ID); // arrivé par la config distante
        c.reveiller();
        f.attendre(FauxDiscord.Recu::setActivity, 3000);
    }

    @Test
    void fermeturePropreEffacePuisClose() throws Exception {
        FauxDiscord f = faux();
        ClientPresence c = client(enJeu(true));
        f.attendre(FauxDiscord.Recu::setActivity, 3000);

        assertTrue(c.arreter(2000), "le fil s'arrête vite");
        FauxDiscord.Recu efface = f.attendre(r -> r.setActivity() && r.activite() == null, 2000);
        FauxDiscord.Recu close = f.attendre(r -> r.op() == Trames.CLOSE, 2000);
        assertTrue(close.nanos() >= efface.nanos());
        assertEquals(ClientPresence.EtatConnexion.ARRETEE, c.etat());
        assertTrue(c.arreter(100), "un second arrêt ne fait rien");
    }

    @Test
    void refusDeDiscordReessaieCalmementSansPlanter() throws Exception {
        FauxDiscord f = faux();
        f.refuser = true;
        ClientPresence c = client(enJeu(true));
        attendreEtat(c, ClientPresence.EtatConnexion.REFUS, 3000);
        Thread.sleep(800);
        int essais = f.connexions.get();
        assertTrue(essais >= 2, "nouvel essai après un refus (" + essais + ")");
        assertTrue(essais <= 7, "réessais espacés (150 à 250 ms), pas en boucle (" + essais + ")");

        f.refuser = false;
        f.attendre(FauxDiscord.Recu::setActivity, 3000);
        assertEquals(ClientPresence.EtatConnexion.CONNECTE, c.etat());
    }

    @Test
    void discordAbsentNiErreurNiBoucle() throws Exception {
        ClientPresence c = client(enJeu(true)); // aucun faux Discord : la socket n'existe pas
        attendreEtat(c, ClientPresence.EtatConnexion.ABSENT, 2000);
        assertTrue(c.arreter(2000));
    }

    @Test
    void changementsRapprochesRegroupesSousLaLimite() throws Exception {
        FauxDiscord f = faux();
        AtomicReference<JsonObject> activite = new AtomicReference<>(enJeu(true));
        ClientPresence.Reglages ecart400 = new ClientPresence.Reglages(150, 250, 400, 20, 2000, 100);
        ClientPresence c = client(new AtomicReference<>(ID), new AtomicBoolean(true), activite, ecart400);
        f.attendre(FauxDiscord.Recu::setActivity, 3000);

        String dernier = null;
        for (int i = 0; i < 10; i++) {
            JsonObject a = enJeu(true);
            dernier = "Étape " + i;
            a.addProperty("details", dernier);
            activite.set(a);
            c.reveiller();
            Thread.sleep(10);
        }
        String attendu = dernier;
        f.attendre(r -> r.setActivity() && r.activite() != null && attendu.equals(r.activite().get("details").getAsString()), 3000);

        List<FauxDiscord.Recu> envois = f.recus.stream().filter(FauxDiscord.Recu::setActivity).toList();
        assertTrue(envois.size() <= 3, "dix changements regroupés (" + envois.size() + " envois)");
        for (int i = 1; i < envois.size(); i++) {
            long ecartMs = (envois.get(i).nanos() - envois.get(i - 1).nanos()) / 1_000_000;
            assertTrue(ecartMs >= 350, "mises à jour espacées d'au moins l'écart réglé (" + ecartMs + " ms)");
        }
    }

    @Test
    void activiteRefuseeRenvoyeeSansLeNom() throws Exception {
        FauxDiscord f = faux();
        f.refuserNom = true;
        client(enJeu(true));
        f.attendre(r -> r.setActivity() && r.activite() != null && r.activite().has("name"), 3000);
        FauxDiscord.Recu simple = f.attendre(r -> r.setActivity() && r.activite() != null && !r.activite().has("name"), 3000);
        assertEquals("En jeu sur Pokényle", simple.activite().get("details").getAsString());
        assertTrue(simple.activite().has("buttons"), "seul le nom est retiré au premier repli");
    }

    @Test
    void sansActiviteAMontrerRienNEstPose() throws Exception {
        FauxDiscord f = faux();
        AtomicReference<JsonObject> activite = new AtomicReference<>(null); // launcher seul dans la barre système
        ClientPresence c = client(new AtomicReference<>(ID), new AtomicBoolean(true), activite, RAPIDES);
        f.attendre(r -> r.op() == Trames.HANDSHAKE, 3000);
        Thread.sleep(400);
        assertEquals(0, f.compter(FauxDiscord.Recu::setActivity));

        activite.set(enJeu(true)); // le jeu démarre
        c.reveiller();
        f.attendre(r -> r.setActivity() && r.activite() != null, 2000);
        activite.set(null); // le jeu se ferme, fenêtre rangée
        c.reveiller();
        f.attendre(r -> r.setActivity() && r.activite() == null, 2000);
    }
}
