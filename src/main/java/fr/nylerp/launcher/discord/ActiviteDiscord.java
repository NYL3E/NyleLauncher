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
 *
 * <h2>Activation progressive</h2>
 * Clé distante {@code discordPour} : absente ou {@code "testeurs"} = seulement les comptes testeurs (liste
 * {@code testeursPokenyle}) ; {@code "tous"} = tout le monde. Hors de la cible, aucune connexion à Discord.
 *
 * <h2>Avec le mod {@code nylepresence} dans le pack</h2>
 * Le jeu montre lui-même l'activité (il sait si le joueur est vraiment connecté au serveur). Si le jar
 * {@code nylepresence-*.jar} est dans le dossier {@code mods} de l'instance au lancement, le launcher se tait
 * tant que le jeu tourne ; sinon il garde « En jeu sur … » comme avant. Le jeu reçoit le réglage du launcher
 * par {@code -Dnylelauncher.discord=on|off} ({@code MinecraftLauncher}) : « off » fait taire le mod aussi.
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
    /** Le jeu lancé a le mod {@code nylepresence} : c'est lui qui parle tant qu'il tourne. */
    private static volatile boolean modEnJeu;
    private static ClientPresence client;
    private static volatile Boolean dernierAcces;

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
        try { modEnJeu = modPresenceDans(fr.nylerp.launcher.config.AppPaths.modsDir()); } catch (Throwable t) { modEnJeu = false; }
        enJeu = true;
        reveiller();
    }

    /** Le réglage « Activité Discord » vient de changer (déjà enregistré dans {@link Settings}). */
    public static void reglageChange() {
        reveiller();
    }

    /**
     * Efface l'activité et ferme la connexion (arrêt du launcher). Attend le fil 0,4 s au plus : la fermeture du
     * launcher ne dépend jamais de lui (fil démon, entrées-sorties bornées à 1 s) ; si Discord est figé, la fin du
     * processus coupe le canal et Discord efface l'activité de lui-même.
     */
    public static void arreter() {
        ClientPresence c;
        synchronized (ActiviteDiscord.class) { c = client; }
        if (c != null) c.arreter(400);
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

    /** L'identifiant à utiliser pour le compte actif, ou "" (inerte : pas d'identifiant, ou compte hors de la cible). */
    static String identifiant() {
        String pseudo = null;
        try {
            fr.nylerp.launcher.auth.Account a = fr.nylerp.launcher.auth.AccountStore.active();
            if (a != null) pseudo = a.username();
        } catch (Throwable ignore) { }
        return identifiantPour(config(), pseudo);
    }

    static String identifiantPour(SourceEtatPokenyle c, String pseudo) {
        String distant = ClientPresence.idValide(c.discordAppId());
        String id = distant != null ? distant : ClientPresence.idValide(ID_APPLICATION);
        if (id == null) return "";
        boolean acces = autorise(c, pseudo);
        if (!Boolean.valueOf(acces).equals(dernierAcces)) {
            dernierAcces = acces;
            if (!acces) LOG.info("Discord : activité réservée aux testeurs pour l'instant (config distante « discordPour »)");
        }
        return acces ? id : "";
    }

    /** {@code discordPour = "tous"}, ou un compte testeur. */
    static boolean autorise(SourceEtatPokenyle c, String pseudo) {
        return c.discordPourTous() || c.estTesteur(pseudo);
    }

    /** Vrai si {@code mods} contient le mod client {@code nylepresence} (il tient alors l'activité en jeu). */
    static boolean modPresenceDans(java.nio.file.Path mods) {
        try (var jars = java.nio.file.Files.newDirectoryStream(mods, "nylepresence-*.jar")) {
            return jars.iterator().hasNext();
        } catch (Throwable t) {
            return false;
        }
    }

    private static JsonObject activite() {
        if (enJeu && modEnJeu) return null; // le mod du jeu parle : une seule source à la fois
        Activite.Phase phase = enJeu ? Activite.Phase.EN_JEU
                : occupe ? Activite.Phase.MISE_A_JOUR : Activite.Phase.LANCEUR;
        boolean pokenyle = enJeu ? jeuPokenyle : ModeDeJeu.courant() == ModeDeJeu.POKENYLE;
        SourceEtatPokenyle c = config();
        return Activite.pour(new Activite.Situation(phase, fenetreVisible, pokenyle, debutJeu,
                c.discordEnFond(), "details".equalsIgnoreCase(c.discordStatut())));
    }
}
