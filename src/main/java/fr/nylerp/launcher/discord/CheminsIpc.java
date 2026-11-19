package fr.nylerp.launcher.discord;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Où chercher le canal local de Discord (doc « RPC », section IPC) : {@code discord-ipc-0} à {@code 9}.
 * <ul>
 *   <li>Windows : tube nommé {@code \\?\pipe\discord-ipc-N} ;</li>
 *   <li>macOS et Linux : socket Unix dans {@code XDG_RUNTIME_DIR}, puis {@code TMPDIR}, {@code TMP},
 *       {@code TEMP}, puis {@code /tmp} ; sous Linux, aussi dans les dossiers des paquets Flatpak et Snap
 *       de Discord, qui posent leur canal dans un sous-dossier de {@code XDG_RUNTIME_DIR}.</li>
 * </ul>
 * La variable d'environnement {@value #VARIABLE_ESSAI}, si elle est posée, remplace TOUTE la liste par
 * le seul chemin qu'elle donne : les essais (faux Discord) ne touchent ainsi jamais le vrai.
 *
 * <p>Partagé tel quel avec le mod client {@code nylepresence} (même fichier, à garder identique).
 */
public final class CheminsIpc {

    static final String VARIABLE_ESSAI = "NYLE_DISCORD_IPC";
    static final int CANAUX = 10;

    /** Sous-dossiers de {@code XDG_RUNTIME_DIR} des Discord empaquetés (Linux). */
    private static final List<String> SOUS_DOSSIERS_LINUX = List.of(
            "app/com.discordapp.Discord", "app/com.discordapp.DiscordCanary", "app/com.discordapp.DiscordPTB",
            "app/dev.vencord.Vesktop", "snap.discord", "snap.discord-canary");

    private CheminsIpc() { }

    /** Les chemins à essayer sur cette machine. */
    public static List<String> systeme() {
        // Filet macOS : si TMPDIR manque à l'environnement du processus, le dossier temporaire de l'utilisateur
        // (celui où Discord pose son canal) reste connu de Java.
        return candidats(v -> {
            String x = System.getenv(v);
            return x == null && "TMPDIR".equals(v) ? System.getProperty("java.io.tmpdir") : x;
        }, System.getProperty("os.name", ""));
    }

    /** Les chemins à essayer, dans l'ordre, pour cet environnement et ce système. */
    static List<String> candidats(Function<String, String> env, String nomSysteme) {
        String force = env.apply(VARIABLE_ESSAI);
        if (force != null && !force.isBlank()) return List.of(force.trim());

        String os = nomSysteme.toLowerCase(Locale.ROOT);
        List<String> r = new ArrayList<>();
        if (os.contains("win")) {
            for (int i = 0; i < CANAUX; i++) r.add("\\\\?\\pipe\\discord-ipc-" + i);
            return r;
        }
        Set<String> dossiers = new LinkedHashSet<>();
        for (String variable : List.of("XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP")) {
            String d = sansBarreFinale(env.apply(variable));
            if (d != null) dossiers.add(d);
        }
        dossiers.add("/tmp");
        String xdg = sansBarreFinale(env.apply("XDG_RUNTIME_DIR"));
        if (xdg != null && os.contains("linux")) {
            for (String sous : SOUS_DOSSIERS_LINUX) dossiers.add(xdg + "/" + sous);
        }
        for (String d : dossiers) {
            for (int i = 0; i < CANAUX; i++) r.add(d + "/discord-ipc-" + i);
        }
        return r;
    }

    static boolean estTubeWindows(String chemin) {
        return chemin.startsWith("\\\\?\\pipe\\") || chemin.startsWith("\\\\.\\pipe\\");
    }

    private static String sansBarreFinale(String d) {
        if (d == null || d.isBlank()) return null;
        String t = d.trim();
        while (t.length() > 1 && t.endsWith("/")) t = t.substring(0, t.length() - 1);
        return t;
    }
}
