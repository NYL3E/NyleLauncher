package fr.nylerp.launcher.discord;

import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.List;

/**
 * Outil de contrôle : {@code NyleLauncher --discord-test [identifiant] [details] [--duree S]} pose l'activité
 * « En jeu sur Pokényle » pendant 20 s (ou S secondes, 5 à 600), puis l'efface et ferme la connexion ; chaque
 * changement d'état et la durée de l'arrêt s'écrivent avec l'heure. Sans interface, sans le verrou
 * d'instance (marche même si le launcher tourne déjà), sans lire ni écrire les réglages du joueur.
 *
 * <p>ATTENTION : contre le vrai Discord, cela change le statut que voient les amis du compte connecté.
 * {@code NYLE_DISCORD_IPC} (chemin d'un canal de test) le dirige vers un faux Discord.
 */
public final class EssaiDiscord {

    public static final String ARG = "--discord-test";
    private static final long DUREE_MS = 20_000;
    private static final java.time.format.DateTimeFormatter HEURE = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private EssaiDiscord() { }

    public static void main(String[] args) throws InterruptedException {
        List<String> a = Arrays.asList(args);
        int i = a.indexOf(ARG);
        String id = i >= 0 && i + 1 < a.size() ? ClientPresence.idValide(a.get(i + 1)) : null;
        if (id == null) id = ClientPresence.idValide(ActiviteDiscord.ID_APPLICATION);
        if (id == null) {
            System.out.println("Essai Discord : aucun identifiant d'application (argument ou ActiviteDiscord.ID_APPLICATION).");
            return;
        }
        boolean details = a.contains("details");
        long duree = DUREE_MS;
        int d = a.indexOf("--duree");
        if (d >= 0 && d + 1 < a.size()) {
            try { duree = Math.max(5, Math.min(600, Long.parseLong(a.get(d + 1)))) * 1000L; } catch (NumberFormatException ignore) { }
        }
        JsonObject activite = Activite.pour(new Activite.Situation(Activite.Phase.EN_JEU, true, true,
                System.currentTimeMillis() / 1000L, false, details));
        System.out.println("Essai Discord : activité envoyée : " + activite);
        String identifiant = id;
        ClientPresence c = new ClientPresence(CheminsIpc::systeme, () -> identifiant, () -> true, () -> activite,
                ClientPresence.Reglages.NORMAUX);
        c.demarrer();
        long fin = System.currentTimeMillis() + duree;
        ClientPresence.EtatConnexion vu = null;
        while (System.currentTimeMillis() < fin) {
            ClientPresence.EtatConnexion e = c.etat();
            if (e != vu) {
                System.out.println(heure() + " Essai Discord : " + e);
                vu = e;
            }
            Thread.sleep(200);
        }
        long t0 = System.nanoTime();
        boolean arrete = c.arreter(2000);
        System.out.println(heure() + " Essai Discord : activité effacée, connexion fermée" + (arrete ? "" : " (fil encore vivant)")
                + " en " + (System.nanoTime() - t0) / 1_000_000 + " ms.");
    }

    private static String heure() {
        return java.time.LocalTime.now().format(HEURE);
    }
}
