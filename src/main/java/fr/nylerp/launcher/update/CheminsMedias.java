package fr.nylerp.launcher.update;

/**
 * Les chemins LOGIQUES du paquet de médias distant (voir {@link Medias}).
 *
 * <p>Un chemin logique est l'adresse stable d'un média, indépendante de son contenu : le
 * manifeste {@code medias.json} le relie à un SHA-256 et à un fichier de release. On change une
 * image en publiant un nouveau paquet ; le code, lui, continue de demander le même chemin.
 *
 * <p>Les chemins {@code pokenyle/…}, {@code univers/…} et {@code logos/…} sont RÉSERVÉS aux
 * médias que l'agent du site produit pour la refonte 2026-10 (formats exacts dans
 * {@code medias/source/LISEZMOI.md}). Tant qu'ils ne sont pas publiés, {@link Medias#fichier}
 * renvoie {@code null} et l'interface s'affiche sans eux.
 */
public final class CheminsMedias {

    // ── NyleRP (médias historiques sortis du jar) ───────────────────────────────────────────
    /** Boucle de musique de l'accueil (13,7 Mo, sortie du jar). */
    public static final String NYLERP_MUSIQUE  = "nylerp/music.mp3";
    /** Boucle d'ambiance « feu de camp » (4,3 Mo, sortie du jar). */
    public static final String NYLERP_AMBIANCE = "nylerp/ambient.mp3";
    /** Vidéos de fond : restent DANS le jar (petites, vitales) ; un paquet peut les remplacer. */
    public static final String NYLERP_VIDEO_1  = "nylerp/videolauncher1.mp4";
    public static final String NYLERP_VIDEO_2  = "nylerp/videolauncher2.mp4";

    // ── Pokényle : ciel animé (réservé, à venir de l'agent du site) ────────────────────────
    public static final String POKENYLE_FOND     = "pokenyle/fond-pokenyle.jpg";
    public static final String POKENYLE_FOND_2X  = "pokenyle/fond-pokenyle@2x.jpg";
    public static final String POKENYLE_NUAGES_2X = "pokenyle/nuages@2x.png";
    public static final String POKENYLE_TRAJETS  = "pokenyle/trajets.json";
    public static final String POKENYLE_PLUME_2X = "pokenyle/particules/plume@2x.png";
    /** Variantes de couleur des plumes de Ho-Oh (fournies par l'agent du site, 16/10). */
    public static final String POKENYLE_PLUME_ROUGE_2X = "pokenyle/particules/plume-rouge@2x.png";
    public static final String POKENYLE_PLUME_ARC_2X   = "pokenyle/particules/plume-arc@2x.png";
    public static final String POKENYLE_ECLAT_2X = "pokenyle/particules/eclat@2x.png";

    /** Noms des atlas de vol : {@code pokenyle/atlas/<nom>.png} + {@code pokenyle/atlas/<nom>.json}. */
    public static final String[] POKENYLE_ATLAS = {
            "pidgeot-net", "pidgeot-flou", "pidgeotto-net", "pidgeotto-flou", "pidgey-net", "ho_oh-net"
    };
    /** Image d'un atlas, ex. {@code atlasImage("ho_oh-net")}. */
    public static String atlasImage(String nom) { return "pokenyle/atlas/" + nom + ".png"; }
    /** Description d'un atlas ({@code {n, colonnes, lignes, w, h, duree_s, pivot}}). */
    public static String atlasJson(String nom)  { return "pokenyle/atlas/" + nom + ".json"; }

    // ── Cartes d'univers et logos (réservé) ─────────────────────────────────────────────────
    public static final String CARTE_POKENYLE_2X = "univers/carte-pokenyle@2x.jpg";
    public static final String CARTE_NYLERP_2X   = "univers/carte-nylerp@2x.jpg";
    /** Logos en 2x, aux noms livrés par l'agent du site (16/10) : texte en 44 et 88 px de haut (1x),
     *  et l'icône carrée aux mêmes hauteurs (la tête de Pikachu pour Pokényle, le coffre pour NyleRP). */
    public static final String LOGO_POKENYLE_ENTETE_2X = "logos/logo-pokenyle-44@2x.png";
    public static final String LOGO_POKENYLE_GRAND_2X  = "logos/logo-pokenyle-88@2x.png";
    public static final String LOGO_NYLERP_ENTETE_2X   = "logos/logo-nylerp-44@2x.png";
    public static final String LOGO_NYLERP_GRAND_2X    = "logos/logo-nylerp-88@2x.png";
    public static final String PIKACHU_POKENYLE_44_2X  = "logos/logo-pokenyle-pikachu-44@2x.png";
    public static final String PIKACHU_POKENYLE_88_2X  = "logos/logo-pokenyle-pikachu-88@2x.png";
    public static final String COFFRE_NYLERP_44_2X     = "logos/logo-nylerp-coffre-44@2x.png";
    public static final String COFFRE_NYLERP_88_2X     = "logos/logo-nylerp-coffre-88@2x.png";

    // ── Barre système (réservé — voir la note : à embarquer AUSSI dans le jar) ──────────────
    /** L'icône de barre système doit exister hors ligne à l'ouverture de session : le lot 1
     *  l'embarque dans le jar ; ces chemins ne servent qu'à la remplacer à distance. */
    public static final String BARRE_MAC      = "barre/barre-mac-Template.png";
    public static final String BARRE_MAC_2X   = "barre/barre-mac-Template@2x.png";
    public static final String BARRE_WINDOWS  = "barre/barre.ico";
    public static final String BARRE_LINUX_24 = "barre/barre-24.png";
    public static final String BARRE_LINUX_48 = "barre/barre-48.png";

    private CheminsMedias() {}
}
