package fr.nylerp.launcher.fond;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * LaunchAgent {@code ~/Library/LaunchAgents/<identifiant>.plist}, chargé par launchd à chaque
 * ouverture de session.
 *
 * <p>On fait lancer {@code /usr/bin/open -g -a <app> --args --fond} plutôt que l'exécutable lui-même :
 * l'app démarre par LaunchServices, comme un double-clic (identité du bundle, réouverture par le
 * Finder), et {@code -g} la laisse en arrière-plan. Si l'app a été supprimée, {@code open} échoue
 * dans le journal de launchd, sans fenêtre.
 *
 * <p>macOS 13+ affiche une seule fois « Élément d'arrière-plan ajouté » quand le fichier apparaît :
 * on ne le réécrit donc que s'il change, sinon l'avis reviendrait à chaque démarrage.
 */
final class DemarrageAutoMac extends DemarrageAuto {

    private final Path fichier;
    private final String identifiant;

    DemarrageAutoMac(Path home, String identifiant) {
        this.identifiant = identifiant;
        this.fichier = home.resolve("Library").resolve("LaunchAgents").resolve(identifiant + ".plist");
    }

    Path fichier() { return fichier; }

    @Override
    public boolean estActif() {
        return Files.isRegularFile(fichier);
    }

    @Override
    public void activer(Path executable) throws IOException {
        String contenu = plist(bundle(executable));
        if (Files.isRegularFile(fichier)
                && contenu.equals(Files.readString(fichier, StandardCharsets.UTF_8))) {
            return;
        }
        Files.createDirectories(fichier.getParent());
        Path tmp = fichier.resolveSibling(fichier.getFileName() + ".tmp");
        Files.writeString(tmp, contenu, StandardCharsets.UTF_8);
        Files.move(tmp, fichier, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public void desactiver() throws IOException {
        Files.deleteIfExists(fichier);
    }

    /**
     * {@code …/NyleLauncher.app/Contents/MacOS/NyleLauncher} → {@code …/NyleLauncher.app}.
     * Hors bundle (cas improbable), on garde l'exécutable : {@code open -a} accepte aussi un chemin.
     */
    static Path bundle(Path executable) {
        Path macos = executable.getParent();
        Path contents = macos == null ? null : macos.getParent();
        Path app = contents == null ? null : contents.getParent();
        if (macos != null && contents != null && app != null
                && macos.getFileName().toString().equals("MacOS")
                && contents.getFileName().toString().equals("Contents")
                && app.getFileName().toString().endsWith(".app")) {
            return app;
        }
        return executable;
    }

    String plist(Path app) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                	<key>Label</key>
                	<string>%s</string>
                	<key>ProgramArguments</key>
                	<array>
                		<string>/usr/bin/open</string>
                		<string>-g</string>
                		<string>-a</string>
                		<string>%s</string>
                		<string>--args</string>
                		<string>%s</string>
                	</array>
                	<key>RunAtLoad</key>
                	<true/>
                	<key>LimitLoadToSessionType</key>
                	<string>Aqua</string>
                	<key>ProcessType</key>
                	<string>Interactive</string>
                </dict>
                </plist>
                """.formatted(xml(identifiant), xml(app.toAbsolutePath().toString()), Fond.ARG_FOND);
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
