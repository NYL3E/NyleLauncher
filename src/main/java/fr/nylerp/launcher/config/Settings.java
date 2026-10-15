package fr.nylerp.launcher.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * User preferences persisted on disk across runs.
 * One instance — always read/saved through the static helpers.
 */
public final class Settings {

    private static final Logger LOG = LoggerFactory.getLogger(Settings.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Default heap = 4 GB per user request. Refurbished Furniture (the chronic
    // tick allocator at 1.29M ms cumulative across MrCedriic's sessions) has
    // been removed from the modpack so allocation rate drops back to a level
    // 4 GB can sustain without pushing G1 into the Old Generation Compaction
    // pause zone. Players can manually bump in Settings if they want more.
    public int ramMb = DEFAULT_RAM_MB;
    /** Valeur historique du défaut de {@link #ramMb}. */
    public static final int DEFAULT_RAM_MB = 4096;
    /**
     * Mémoire « Auto » (15/10/2026) : quand c'est vrai, le launcher choisit le -Xmx d'après la
     * RAM du PC ({@link #autoRamMb(long)}) et ignore {@link #ramMb}. Déplacer le curseur des
     * paramètres le passe à false et garde la valeur choisie.
     *
     * <p>Pourquoi : avec le pack pokényle, l'ensemble vivant du jeu fait 3,3 à 3,6 Go ; un tas de
     * 4 Go enchaîne les nettoyages complets (~0,7 s de gel chacun, 2 187 en 3 jours chez l'owner).
     *
     * <p>Réglages existants : Gson passe par le constructeur sans argument, donc une clé absente
     * garde ce défaut (true). Exception, voir {@link #parse(String)} : un joueur qui avait
     * DÉLIBÉRÉMENT choisi une autre valeur que 4 Go la garde.
     */
    public boolean ramAuto = true;
    /**
     * Le MODE DE JEU choisi — {@code "nylerp"} (défaut) ou {@code "pokenyle"}.
     *
     * <p>Stocké en texte plutôt qu'en énumération, et lu avec tolérance : un fichier de réglages
     * écrit par une version future ne doit pas empêcher une version antérieure de démarrer. Un
     * joueur qui n'a jamais touché au sélecteur voit ce champ absent de son fichier, Gson laisse
     * donc la valeur par défaut, et il reste sur NyleRP sans rien remarquer — c'est exactement
     * ce qu'on veut pour les milliers de launchers déjà installés.
     *
     * @see ModeDeJeu
     */
    public String modeDeJeu = "nylerp";
    /** Legacy field kept for backwards-compatible Gson deserialisation —
     *  earlier payload (1.0.46) used this to one-time bump 6 GB users to
     *  8 GB. We no longer enforce a minimum, so the field is read-only
     *  here. Don't delete: Gson would reject the unknown property on
     *  existing settings.json files. */
    @SuppressWarnings("unused") public boolean heapMigratedToV2 = false;
    public boolean optionalLitematica       = false;
    public boolean optionalDistantHorizons  = false;
    /** Iris shaders toggle. Independent of DH so a player can run shader packs
     *  without DH's far-LOD load, but DH unconditionally force-installs Iris
     *  too — see {@code OptionalMods.ENTRIES} — because DH's render pipeline
     *  produces 1+ second hitches on the no-Iris fallback path. */
    public boolean optionalIris             = false;
    /** Punchy! — animations à la première personne (mains visibles, combat,
     *  mouvement, interaction). Purement client/cosmétique : aucun effet sur
     *  les dégâts ni le recul réels, donc rien à synchroniser côté serveur. */
    public boolean optionalPunchy           = false;
    /** Controlify (manettes) — mod optionnel du mode POKÉNYLE seulement (15/10/2026), coupé
     *  par défaut : il a quitté le pack pokényle (natifs SDL, à-coups mesurés au rendu). */
    public boolean optionalControlify       = false;
    /** Persists the launcher's mute toggle (ambient + music) across sessions
     *  so players who silenced the launcher once don't have to do it on
     *  every start. */
    public boolean launcherAudioMuted       = false;
    /** Coupe l'arrière-plan VIDÉO du launcher, et le garde coupé aux démarrages
     *  suivants (owner 2026-08-13 : « les petits PC ont du mal avec la vidéo »).
     *  <p>Ce n'est pas un simple masquage : quand c'est vrai, les MediaPlayer ne
     *  sont pas construits du tout au démarrage — donc aucun décodage H.264 ne
     *  tourne, exactement comme sur le canal DEV. Cacher la MediaView sans
     *  arrêter le lecteur aurait laissé le décodeur consommer le processeur pour
     *  des images que personne ne regarde, c'est-à-dire tout le contraire de ce
     *  qui est demandé. */
    public boolean launcherVideoDisabled    = false;
    /** Ferme le launcher dès que le jeu démarre. Activé par défaut — le joueur
     *  n'a pas besoin du launcher une fois Minecraft lancé ; décochable dans
     *  les paramètres pour le garder ouvert (debug, relance rapide). */
    public boolean closeOnLaunch            = true;
    /** Volume of the ambient campfire loop. Range 0.0–1.0. Default 0.05 —
     *  a discreet sub-music texture that stays well under the foreground
     *  music and never dominates the home screen. */
    public double ambientVolume             = 0.05;
    /** Volume of the foreground music loop. Range 0.0–1.0. Default 0.12 —
     *  a soft, low default so the home-screen music is present but quiet
     *  out of the box (the player can raise it in Settings). */
    public double musicVolume               = 0.12;
    // optionalBobby was retired in 1.0.28 (replaced by Distant Horizons). The
    // field is left as a no-op deserialisation target so existing saved
    // settings.json files don't fail to parse for users who had it on; Gson
    // tolerates unknown fields, but keeping the slot avoids a future Gson
    // strict-mode bump biting us. Remove after a few versions of grace.
    @SuppressWarnings("unused") public boolean optionalBobby = false;

    private static Settings current;

    // ── Mémoire automatique ──────────────────────────────────────────────────────────────────
    // Paliers de RAM PHYSIQUE. Volontairement sous 16384 / 12288 Mo : Windows rapporte la mémoire
    // UTILISABLE (TotalPhysicalMemory), un peu sous la capacité annoncée — un PC « 16 Go » remonte
    // souvent 15,8-16,0 Go, voire moins avec une carte graphique intégrée.
    /** RAM physique à partir de laquelle on considère un PC « 16 Go ». */
    public static final long AUTO_TIER_16G_PHYS_MB = 15_000;
    /** RAM physique à partir de laquelle on considère un PC « 12 Go ». */
    public static final long AUTO_TIER_12G_PHYS_MB = 11_000;
    public static final int AUTO_XMX_16G_MB = 6144;
    public static final int AUTO_XMX_12G_MB = 5120;
    public static final int AUTO_XMX_DEFAULT_MB = DEFAULT_RAM_MB;

    /** Le -Xmx « Auto » pour une RAM physique donnée ({@code physMb <= 0} = détection ratée →
     *  4 Go). Le plafond physique (RAM − 2 Go, plancher 2 Go) est appliqué ensuite, au lancement,
     *  par {@code MinecraftLauncher.clampHeapMb}. */
    public static int autoRamMb(long physMb) {
        if (physMb >= AUTO_TIER_16G_PHYS_MB) return AUTO_XMX_16G_MB;
        if (physMb >= AUTO_TIER_12G_PHYS_MB) return AUTO_XMX_12G_MB;
        return AUTO_XMX_DEFAULT_MB;
    }

    /** La mémoire demandée, avant le plafond physique : la valeur Auto, ou celle du curseur. */
    public int requestedRamMb(long physMb) {
        return ramAuto ? autoRamMb(physMb) : ramMb;
    }

    /**
     * Lit un settings.json. Clé {@code ramAuto} absente (réglages d'avant le 15/10/2026) : Gson
     * laisse le défaut true, SAUF si {@code ramMb} diffère de l'ancien défaut (4 Go) — le joueur
     * l'avait alors choisi au curseur (ex. 2 Go sur un petit PC, 8 Go sur un gros) et on ne le
     * lui reprend pas.
     */
    static Settings parse(String json) {
        Settings s = GSON.fromJson(json, Settings.class);
        if (s == null) return new Settings();
        boolean hasRamAuto = false;
        try {
            com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(json);
            hasRamAuto = el.isJsonObject() && el.getAsJsonObject().has("ramAuto");
        } catch (Exception ignored) { /* fromJson a réussi : on garde le défaut */ }
        if (!hasRamAuto && s.ramMb != DEFAULT_RAM_MB) s.ramAuto = false;
        return s;
    }

    public static Settings get() {
        if (current != null) return current;
        Path file = AppPaths.settingsFile();
        if (Files.exists(file)) {
            try {
                current = parse(fr.nylerp.launcher.util.LectureTexte.lire(file));
            } catch (Exception e) {
                LOG.warn("Settings unreadable, resetting: {}", e.toString());
                current = new Settings();
            }
        } else {
            current = new Settings();
        }
        // No silent bump of ramMb — a value chosen with the slider is kept as is
        // (ramAuto=false). Fresh installs and untouched 4 GB players are on Auto
        // (see ramAuto / parse).
        return current;
    }

    public void save() {
        try {
            Files.writeString(AppPaths.settingsFile(), GSON.toJson(this));
        } catch (Exception e) {
            LOG.warn("Could not save settings: {}", e.toString());
        }
    }
}
