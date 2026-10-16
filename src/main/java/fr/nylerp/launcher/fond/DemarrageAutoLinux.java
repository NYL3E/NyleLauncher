package fr.nylerp.launcher.fond;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Fichier {@code $XDG_CONFIG_HOME/autostart/<nom>.desktop} (spécification XDG Autostart), lu par
 * GNOME, KDE, XFCE, Cinnamon… à l'ouverture de session. Un {@code Exec} introuvable est ignoré.
 */
final class DemarrageAutoLinux extends DemarrageAuto {

    private final Path fichier;
    private final String nomAffiche;

    DemarrageAutoLinux(Path configHome, String nomFichier, String nomAffiche) {
        this.fichier = configHome.resolve("autostart").resolve(nomFichier + ".desktop");
        this.nomAffiche = nomAffiche;
    }

    Path fichier() { return fichier; }

    @Override
    public boolean estActif() {
        return Files.isRegularFile(fichier);
    }

    @Override
    public void activer(Path executable) throws IOException {
        String contenu = desktop(executable);
        if (Files.isRegularFile(fichier)
                && contenu.equals(Files.readString(fichier, StandardCharsets.UTF_8))) {
            return;
        }
        Files.createDirectories(fichier.getParent());
        Path tmp = fichier.resolveSibling(fichier.getFileName() + ".tmp");
        Files.writeString(tmp, contenu, StandardCharsets.UTF_8);
        Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public void desactiver() throws IOException {
        Files.deleteIfExists(fichier);
    }

    String desktop(Path executable) {
        return "[Desktop Entry]\n"
                + "Type=Application\n"
                + "Name=" + nomAffiche + "\n"
                + "Comment=Lance " + nomAffiche + " en fond à l'ouverture de session\n"
                + "Exec=" + guillemets(executable.toAbsolutePath().toString()) + " " + Fond.ARG_FOND + "\n"
                + "Terminal=false\n"
                + "X-GNOME-Autostart-enabled=true\n"
                + "Hidden=false\n";
    }

    /** Argument {@code Exec} entre guillemets, avec les échappements de la spécification Desktop Entry. */
    static String guillemets(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '`' || c == '$' || c == '\\') b.append('\\');
            b.append(c);
        }
        return b.append('"').toString();
    }
}
