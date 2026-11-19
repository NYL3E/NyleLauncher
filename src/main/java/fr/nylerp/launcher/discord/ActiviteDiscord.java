package fr.nylerp.launcher.discord;

import com.google.gson.JsonObject;
import fr.nylerp.launcher.config.ModeDeJeu;
import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.univers.Services;
import fr.nylerp.launcher.univers.SourceEtatPokenyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * L'ACTIVITÉ DISCORD « NyleLauncher » (owner, 19/11/2026) : quand le launcher est ouvert, le Discord du
 * joueur le montre à ses amis, pour qu'ils aient envie de le rejoindre sur le serveur.
 *
 * <p>Point d'entrée unique pour le reste du launcher. Toutes les méthodes reviennent tout de suite, depuis
 * n'importe quel fil, et ne lèvent jamais rien : le travail se fait dans le fil démon de
 * {@link ClientPresence}. Tant que {@link #demarrer()} n'a pas été appelé (essais, aperçus), elles ne font
 * que retenir l'état.
 *
 * <h2>Quand l'activité se montre</h2>
 * Fenêtre ouverte (« Dans le lanceur »), mise à jour ou lancement en cours (« Mise à jour du jeu… »),
 * jeu en cours (« En jeu sur Pokényle » ou « En jeu sur NyleRP », avec le chrono). Launcher seul dans la
 * barre système, sans jeu : aucune activité (un PC simplement allumé ne doit pas afficher « Joue à
 * NyleLauncher » toute la journée) ; la clé distante {@code discordEnFond} peut renverser cette règle.
 */
public final class ActiviteDiscord {

    private static final Logger LOG = LoggerFactory.getLogger("Discord");

    /**
     * Identifiant (public) de l'application Discord « NyleLauncher ». Vide : rien ne se connecte.
     * La clé {@code discordAppId} de la config distante le remplace dès qu'elle est lue.
     */
    public static final String ID_APPLICATION = "";

    private static volatile boolean fenetreVisible;
    private static volatile boolean occupe;
    private static volatile boolean enJeu;
    private static volatile boolean jeuPokenyle;
    private static volatile long debutJeu;
    private static ClientPresence client;

    private ActiviteDiscord() { }

    /** À appeler une fois au démarrage, après {@code Annonces.demarrer()} (qui pose la config distante). */
    public static synchronized void demarrer() {
        if (client != null) return;
        try {
            Settings.get(); // chargés ici, sur le fil appelant, avant que le fil de l'activité les lise
            client = new ClientPresence(CheminsIpc::systeme, ActiviteDiscord::identifiant,
                    () -> Settings.get().activiteDiscord, ActiviteDiscord::activite, ClientPresence.Reglages.NORMAUX);
            Services.etatPokenyle.surChangement(ActiviteDiscord::reveiller);
            client.demarrer();
        } catch (Throwable t) {
            LOG.warn("Discord : activité indisponible ({})", t.toString());
        }
    }

    /** La fenêtre du launcher vient d'apparaître ou d'être rangée. */
    public static void fenetre(boolean visible) {
        fenetreVisible = visible;
        reveiller();
    }

    /**
     * Le gros bouton travaille (mise à jour du pack, préparation du lancement, jeu en cours) ou vient de
     * s'arrêter. La fin met aussi fin au jeu : {@code LanceurJeu} repose le bouton quand le jeu se ferme.
     */
    public static void occupe(boolean travail) {
        occupe = travail;
        if (!travail) enJeu = false;
        reveiller();
    }

    /** Le processus du jeu vient de démarrer, sur l'univers courant. */
    public static void jeuLance() {
        try { jeuPokenyle = ModeDeJeu.courant() == ModeDeJeu.POKENYLE; } catch (Throwable ignore) { }
        debutJeu = System.currentTimeMillis() / 1000L;
        enJeu = true;
        reveiller();
    }

    /** Le réglage « Activité Discord » vient de changer (déjà enregistré dans {@link Settings}). */
    public static void reglageChange() {
        reveiller();
    }

    /** Efface l'activité et ferme la connexion (arrêt du launcher). N'attend pas plus d'une seconde et demie. */
    public static void arreter() {
        ClientPresence c;
        synchronized (ActiviteDiscord.class) { c = client; }
        if (c != null) c.arreter(1500);
    }

    // ── Interne ──────────────────────────────────────────────────────────────

    private static void reveiller() {
        ClientPresence c = client;
        if (c != null) c.reveiller();
    }

    private static SourceEtatPokenyle config() {
        SourceEtatPokenyle s = Services.etatPokenyle;
        return s != null ? s : SourceEtatPokenyle.PAR_DEFAUT;
    }

    static String identifiant() {
        String distant = ClientPresence.idValide(config().discordAppId());
        return distant != null ? distant : ID_APPLICATION;
    }

    private static JsonObject activite() {
        Activite.Phase phase = enJeu ? Activite.Phase.EN_JEU
                : occupe ? Activite.Phase.MISE_A_JOUR : Activite.Phase.LANCEUR;
        boolean pokenyle = enJeu ? jeuPokenyle : ModeDeJeu.courant() == ModeDeJeu.POKENYLE;
        SourceEtatPokenyle c = config();
        return Activite.pour(new Activite.Situation(phase, fenetreVisible, pokenyle, debutJeu,
                c.discordEnFond(), "details".equalsIgnoreCase(c.discordStatut())));
    }
}
