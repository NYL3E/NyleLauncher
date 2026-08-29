package fr.nylerp.launcher.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Lire un fichier texte sans jamais mourir sur son encodage.
 *
 * <h2>Pourquoi cette classe existe</h2>
 * {@code Files.readString} décode en UTF-8 <b>strict</b> : le moindre octet qui n'est pas de
 * l'UTF-8 valide lève une {@link java.nio.charset.MalformedInputException}. Sur un Windows
 * français, tout ce qu'écrit un programme natif — le chargeur du système, un pilote graphique,
 * la couche audio, la bibliothèque de fenêtrage — sort dans la page de codes ANSI locale
 * (windows-1252), où {@code é} vaut un seul octet {@code 0xE9}. En UTF-8, cet octet annonce un
 * caractère sur trois octets qui n'arrive jamais : le décodeur abandonne avec
 * « {@code Input length = 3} ».
 *
 * <p>C'est exactement ce qu'a vu une joueuse le 29/08 : son jeu s'arrêtait au démarrage, le
 * launcher ouvrait le journal de Minecraft pour lui dire POURQUOI, et se cassait sur le premier
 * accent de ce journal. Elle voyait « Erreur : Input length = 3 » — un message qui ne parle de
 * rien — au lieu de la vraie panne, et il devenait impossible de l'aider.
 *
 * <h2>La règle</h2>
 * Un fichier qu'on n'a pas écrit soi-même se lit en <b>tolérant</b> : UTF-8 d'abord, puisque
 * c'est le cas normal ; et si les octets le refusent, windows-1252, qui accepte tout et rend le
 * texte lisible. Un journal ne doit JAMAIS empêcher de diagnostiquer la panne qu'il décrit.
 */
public final class LectureTexte {

    private LectureTexte() {}

    /** La page de codes d'un Windows occidental — celle des journaux et des consoles. */
    private static final Charset ANSI_OCCIDENTAL = charset("windows-1252");

    /** Le contenu du fichier, quel que soit son encodage. */
    public static String lire(Path fichier) throws IOException {
        return decoder(Files.readAllBytes(fichier));
    }

    /**
     * Le contenu du fichier, ou {@code defaut} si quoi que ce soit empêche de le lire —
     * absent, verrouillé, illisible. Pour les lectures de confort, celles qui ne doivent
     * jamais faire échouer ce qu'elles servent à expliquer.
     */
    public static String lireOuDefaut(Path fichier, String defaut) {
        try {
            return fichier != null && Files.exists(fichier) ? lire(fichier) : defaut;
        } catch (Throwable t) {
            return defaut;
        }
    }

    /** Les lignes du fichier, quel que soit son encodage. */
    public static List<String> lignes(Path fichier) throws IOException {
        return List.of(lire(fichier).split("\r?\n", -1));
    }

    /**
     * Décode des octets en texte : UTF-8 si c'est de l'UTF-8, la page occidentale sinon.
     *
     * <p>L'ordre compte. Tenter windows-1252 en premier réussirait TOUJOURS — cette page mappe
     * presque tous les octets — et transformerait silencieusement nos propres fichiers UTF-8 en
     * charabia (« Ã© » pour « é »). On demande donc d'abord un décodage UTF-8 STRICT : s'il
     * passe, le fichier était bien de l'UTF-8 ; s'il échoue, il vient d'ailleurs.
     */
    public static String decoder(byte[] octets) {
        if (octets == null || octets.length == 0) return "";
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(octets))
                    .toString();
        } catch (CharacterCodingException pasDeLUtf8) {
            return new String(octets, ANSI_OCCIDENTAL);
        }
    }

    private static Charset charset(String nom) {
        try {
            return Charset.forName(nom);
        } catch (Throwable t) {
            return StandardCharsets.ISO_8859_1;   // toujours présente, même famille d'octets
        }
    }
}
