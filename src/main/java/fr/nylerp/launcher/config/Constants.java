package fr.nylerp.launcher.config;

public final class Constants {

    /** true sur un build DEV (voir {@link Channel}). Pilote nom/serveur/pack/thème. */
    public static final boolean DEV = Channel.isDev();

    public static final String APP_NAME     = DEV ? "NyleLauncher DEV" : "NyleLauncher";
    public static final String APP_VERSION  = "0.3.19";
    /** Payload version baked at compile time. Bump together with the
     *  {@code payload-X.Y.Z} git tag. Displayed in the launcher footer so a
     *  player can visually confirm which payload they're running after a
     *  silent bootstrap refresh.
     *
     *  <p>ATTENTION — purement cosmétique : la version publiée dans
     *  {@code manifest.json} (celle que le bootstrap compare) est dérivée du NOM DU TAG
     *  par la CI ({@code .github/workflows/payload.yml}, {@code ${GITHUB_REF_NAME#payload-}}),
     *  pas de cette constante. Elle avait dérivé (1.0.84 dans le code alors que
     *  payload-1.0.87 était publié) sans que personne ne le voie : le seul symptôme est
     *  un mauvais numéro affiché dans le pied de page et dans « À propos ». */
    public static final String PAYLOAD_VERSION = "1.0.97";

    /**
     * Version de payload à AFFICHER.
     *
     * <p>Pourquoi une méthode et pas la constante : {@link #PAYLOAD_VERSION} est figée à la
     * compilation et a déjà dérivé sans que personne ne le voie. Depuis 2026-08-08 le socle expose
     * la version qu'il vient réellement d'appliquer via la propriété système
     * {@code nyleauth.payloadVersion} ; c'est la seule source de vérité. Sur le canal DEV, où une
     * nouvelle charge peut arriver à chaque démarrage, un testeur DOIT pouvoir lire d'un coup d'œil
     * quelle charge tourne — sinon on ne sait pas distinguer « la mise à jour n'est pas passée » de
     * « la correction ne marche pas ».
     *
     * <p><b>Production strictement inchangée</b> : hors canal DEV la méthode renvoie le littéral,
     * exactement comme avant. Aucun joueur de production ne peut voir une valeur différente de celle
     * qu'il voit aujourd'hui, quelle que soit la propriété système présente.
     */
    public static String runningPayloadVersion() {
        if (!DEV) return PAYLOAD_VERSION;
        String injected = System.getProperty("nyleauth.payloadVersion");
        return (injected != null && !injected.isBlank()) ? injected : PAYLOAD_VERSION;
    }

    public static final String MC_VERSION   = "1.21.1";
    public static final String LOADER       = "fabric";

    // ── Serveur de jeu ──────────────────────────────────────────────────────────────────────
    // PROD : domaine → proxy Velocity (robuste si l'IP change).
    // DEV  : SERVER DEV direct (2f350981 = game45-fr.hosterfy.com:20652) — isolé de la prod.
    //
    // DEPUIS LE SÉLECTEUR DE MODE (30/08), CE SONT DES MÉTHODES. Elles l'étaient déjà en
    // pratique — la valeur dépendait du canal — mais le canal est figé à la compilation, alors
    // que le mode de jeu change d'un clic, en cours d'exécution. Une constante `static final`
    // aurait mémorisé le mode du DÉMARRAGE et envoyé le joueur sur le mauvais serveur après
    // une bascule, sans rien signaler.
    public static String serverHost() { return ModeDeJeu.courant().hote; }
    public static int    serverPort() { return ModeDeJeu.courant().port; }

    public static final String DISCORD_URL  = "https://discord.gg/nylerp"; // TODO: real invite
    public static final String WEBSITE      = "https://nylerp.fr";

    /** Tag GitHub de la release qui héberge le pack du MODE COURANT : {@code pack-latest} (NyleRP
     *  en prod), {@code pack-dev} (NyleRP sur canal DEV, mods client WIP) ou
     *  {@code pack-pokenyle}. Le manifest pack-dev réutilise les URLs pack-latest pour les
     *  fichiers inchangés et ne remplace que les jars en test → aucune duplication du pack. */
    public static String packTag() { return ModeDeJeu.courant().packTag; }

    /** URL du manifeste décrivant l'état du modpack à installer, pour le mode courant. */
    public static String manifestUrl() {
        return "https://github.com/NYL3E/NyleLauncher/releases/download/" + packTag() + "/manifest.json";
    }

    /** Base depuis laquelle se téléchargent les fichiers listés par le manifeste. */
    public static String packBaseUrl() {
        return "https://github.com/NYL3E/NyleLauncher/releases/download/" + packTag() + "/";
    }

    /** Microsoft Minecraft public client id — used by MultiMC, Prism, etc. */
    public static final String MS_CLIENT_ID = "00000000402b5328";

    private Constants() {}
}
