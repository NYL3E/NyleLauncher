package fr.nylerp.launcher.config;

/**
 * LE MODE DE JEU — quel univers le launcher prépare et lance.
 *
 * <h2>Pourquoi ce n'est pas un {@link Channel}</h2>
 * Le canal (PROD / DEV) est décidé à la COMPILATION : il est lu dans une ressource écrite par
 * Gradle, et un launcher déjà installé ne peut pas en changer. Le mode de jeu, lui, se choisit
 * PENDANT l'exécution, d'un clic, et doit survivre au redémarrage. Ce sont deux axes
 * indépendants : on peut être en DEV sur pokényle, ou en PROD sur NyleRP.
 *
 * <h2>Ce qu'un mode emporte avec lui</h2>
 * Un mode n'est pas seulement une étiquette : il décide du <b>modpack</b> téléchargé, du
 * <b>serveur</b> rejoint, et surtout du <b>dossier de jeu</b>. Ce dernier point est le plus
 * important — deux univers qui partageraient le même dossier {@code mods} se détruiraient
 * mutuellement à chaque bascule, et le joueur retélécharcherait un gigaoctet à chaque
 * aller-retour. Chaque mode a donc sa propre instance, complète et indépendante.
 *
 * <h2>La règle qui protège les joueurs déjà installés</h2>
 * {@link #NYLERP} garde le dossier historique {@code game/} et les adresses historiques. Ce
 * n'est pas un détail d'implémentation, c'est une garantie : des centaines de launchers sont
 * déjà installés avec leur instance à cet endroit. Le jour où ils reçoivent cette mise à jour,
 * ils doivent démarrer exactement comme la veille, sans un octet à retélécharger. Tout ce qui
 * est nouveau vit à côté, jamais à la place.
 */
public enum ModeDeJeu {

    /**
     * Le serveur historique : CrazyTown, la survie RP. C'est le mode par défaut, et celui que
     * tout joueur non-testeur utilise sans jamais savoir que l'autre existe.
     */
    NYLERP("nylerp", "NYLERP", "CrazyTown / Survie RP",
            Constants.DEV ? "pack-dev" : "pack-latest",
            Constants.DEV ? "game45-fr.hosterfy.com" : "play.nylerp.fr",
            Constants.DEV ? 20652 : 20161,
            "game"),

    /**
     * Pokényle : le serveur Cobblemon. Son pack est distinct de celui de NyleRP — ce sont deux
     * collections de mods qui n'ont presque rien en commun — d'où une instance séparée.
     */
    POKENYLE("pokenyle", "POKÉNYLE", "Cobblemon",
            "pack-pokenyle",
            // EN DIRECT, sans passer par le proxy : celui-ci ne connaît pas encore de route vers
            // pokényle, et play.nylerp.fr enverrait simplement sur le serveur RP — le joueur se
            // demanderait pourquoi son Cobblemon n'a pas démarré. Tant que c'est un terrain
            // d'essai, l'adresse directe est la seule qui dise la vérité. Le jour où pokényle
            // s'ouvre aux joueurs, il faudra le déclarer dans velocity.toml et repasser par le
            // proxy : c'est lui qui authentifie.
            "83.143.117.19",
            20619,
            "game-pokenyle");

    /** Identifiant stable écrit dans les réglages. Ne JAMAIS le changer : il est persisté. */
    public final String id;
    /** Nom court, tel qu'il s'affiche sur le bouton. */
    public final String titre;
    /** Sous-titre : ce qu'on vient y faire. */
    public final String sousTitre;
    /** Tag de la release GitHub qui héberge le modpack de ce mode. */
    public final String packTag;
    /** Adresse du serveur à rejoindre. */
    public final String hote;
    /** Port du serveur. */
    public final int port;
    /** Nom du dossier d'instance, sous la racine du launcher. */
    public final String dossier;

    ModeDeJeu(String id, String titre, String sousTitre,
              String packTag, String hote, int port, String dossier) {
        this.id = id;
        this.titre = titre;
        this.sousTitre = sousTitre;
        this.packTag = packTag;
        this.hote = hote;
        this.port = port;
        this.dossier = dossier;
    }

    /**
     * Le mode enregistré dans les réglages — {@link #NYLERP} par défaut, et à la moindre
     * anomalie.
     *
     * <p>La tolérance est volontaire : un identifiant inconnu (réglages d'une version plus
     * récente, fichier corrompu, bricolage à la main) ne doit jamais empêcher le launcher de
     * démarrer. Dans le doute, on ramène le joueur là où se trouve son jeu.
     */
    public static ModeDeJeu courant() {
        try {
            String id = Settings.get().modeDeJeu;
            if (id != null) {
                for (ModeDeJeu m : values()) {
                    if (m.id.equalsIgnoreCase(id.trim())) return m;
                }
            }
        } catch (Exception ignored) {
            // réglages illisibles : le mode par défaut reste le bon choix
        }
        return NYLERP;
    }

    /** Enregistre le mode choisi. Sans effet si c'était déjà celui-là. */
    public static void definir(ModeDeJeu mode) {
        if (mode == null || mode == courant()) return;
        Settings s = Settings.get();
        s.modeDeJeu = mode.id;
        s.save();
    }
}
