package fr.nylerp.launcher.ui.terminal;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;

import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Outil de vérification hors ligne de l'écran d'ouverture DEV : rend des <b>instants précis</b> de
 * la séquence en PNG, sans lancer le launcher ni toucher au réseau.
 *
 * <p><b>Pourquoi cet outil est dans les sources livrées</b> — une animation ne se prouve pas avec
 * une capture : il en faut plusieurs, au même endroit de la séquence, pour comparer des variantes.
 * {@link TerminalBoot#renderAt(double)} étant une fonction pure du temps, on peut demander
 * exactement l'instant voulu. Garder l'outil à côté du code animé garantit qu'il ne dérive pas ;
 * il n'est jamais exécuté par le launcher (classe sans lien depuis {@code Main}).
 *
 * <p>Usage :
 * <pre>java -cp nylelauncher.jar fr.nylerp.launcher.ui.terminal.TerminalPreview &lt;dossier&gt; [all]</pre>
 * Sans « all », seule la variante retenue est rendue sur toute la séquence.
 */
public final class TerminalPreview {

    /**
     * Point d'entrée réel. Comme {@code Main}, on ne lance PAS une classe qui hérite de
     * {@code Application} directement : depuis le classpath (JavaFX en module anonyme) le
     * lanceur java refuse de démarrer une telle classe (« composants d'exécution JavaFX
     * manquants »). On passe donc par {@code Application.launch(Classe.class, …)}.
     */
    public static void main(String[] args) { Application.launch(App.class, args); }

    /** Application JavaFX minimale : rend les captures puis quitte. */
    public static final class App extends Application {

    private static final double W = 1000, H = 620;   // = taille réelle de la fenêtre du launcher

    /** Instants remarquables pour comparer les variantes d'apparition du banner. */
    private static final double[] COMPARE = { 0.70, 1.05, 1.40, 1.75 };
    /** Séquence complète, de l'amorçage à l'effacement. */
    private static final double[] SEQUENCE =
            { 0.15, 0.40, 0.62, 0.80, 1.00, 1.20, 1.40, 1.60, 1.85, 2.10, 2.40, 2.70, 2.95 };

    @Override
    public void start(Stage stage) throws Exception {
        java.util.List<String> args = getParameters().getRaw();
        Path out = Path.of(args.isEmpty() ? "design/terminal-dev" : args.get(0));
        boolean all = args.size() > 1 && "all".equals(args.get(1));
        Files.createDirectories(out);

        if (all) {
            for (AsciiReveal.Style s : AsciiReveal.Style.values()) {
                for (double t : COMPARE) {
                    shoot(s, t, out.resolve(String.format(java.util.Locale.ROOT,
                            "variante-%s-t%.2f.png", s.name().toLowerCase(java.util.Locale.ROOT), t)));
                }
            }
        }
        AsciiReveal.Style retenue = AsciiReveal.Style.BALAYAGE_DECODE;
        for (double t : SEQUENCE) {
            shoot(retenue, t, out.resolve(String.format(java.util.Locale.ROOT, "sequence-t%.2f.png", t)));
        }
        rendreSurvol(out);
        mesurerCout(retenue);
        mesurerContraste(out);
        System.out.println("[preview] captures ecrites dans " + out.toAbsolutePath());
        Platform.exit();
    }

    private void shoot(AsciiReveal.Style style, double t, Path file) throws Exception {
        TerminalBoot boot = new TerminalBoot(style);
        StackPane root = new StackPane(boot);
        root.setPrefSize(W, H);
        Scene scene = new Scene(root, W, H);
        root.applyCss();
        root.layout();
        boot.renderAt(t);
        WritableImage img = scene.snapshot(null);
        File f = file.toFile();
        javax.imageio.ImageIO.write(
                javafx.embed.swing.SwingFXUtils.fromFXImage(img, null), "png", f);
        System.out.println("[preview] " + f.getName());
    }

    /**
     * Rend la <b>réaction au survol</b> du bouton principal, étape par étape.
     *
     * <p>Une souris ne se pilote pas depuis une capture d'écran : on force donc l'état CSS
     * {@code :hover} (cadre et fond du bouton) et on demande à {@link TerminalHover#recomposition}
     * — la fonction que l'animation utilise réellement — l'état du texte à chaque étape. Ce qui est
     * montré ici est donc bien ce qui s'affiche au survol, pas une reconstitution approchée.
     */
    private void rendreSurvol(Path out) throws Exception {
        javafx.css.PseudoClass survol = javafx.css.PseudoClass.getPseudoClass("hover");
        javafx.scene.layout.VBox colonne = new javafx.scene.layout.VBox(10);
        colonne.setStyle("-fx-padding: 24;");
        for (int etape = 0; etape <= 8; etape += 2) {
            javafx.scene.control.Label lbl = new javafx.scene.control.Label(
                    etape == 0 ? "JOUER" : TerminalHover.recomposition("JOUER", etape, 8));
            lbl.setFont(TerminalTheme.mono(20));
            javafx.scene.control.Button b = new javafx.scene.control.Button();
            b.setGraphic(lbl);
            b.getStyleClass().add("btn-play");
            b.setPrefSize(240, 44);
            if (etape > 0) b.pseudoClassStateChanged(survol, true);   // état :hover forcé
            javafx.scene.control.Label leg = new javafx.scene.control.Label(
                    etape == 0 ? "  au repos" : "  survol, etape " + etape + "/8");
            leg.setFont(TerminalTheme.mono(11));
            leg.getStyleClass().add("term-info");
            colonne.getChildren().add(new javafx.scene.layout.HBox(14, b, leg));
        }
        StackPane root = new StackPane(colonne);
        root.setPrefSize(560, 380);
        root.setStyle("-fx-background-color: #05070A;");
        Scene sc = new Scene(root, 560, 380);
        sc.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        sc.getStylesheets().add(getClass().getResource("/css/terminal.css").toExternalForm());
        root.applyCss();
        root.layout();
        root.setEffect(TerminalTheme.phosphore(560, 380));
        WritableImage img = sc.snapshot(null);
        javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(img, null),
                "png", out.resolve("survol-bouton-jouer.png").toFile());
        System.out.println("[preview] survol-bouton-jouer.png");
    }

    /**
     * Mesure le <b>contraste réel</b> de chaque niveau du thème, APRÈS le filtre phosphore.
     *
     * <p>Un thème monochrome se joue sur le fil : du vert sombre sur noir devient illisible très
     * vite, et l'œil est mauvais juge sur un écran calibré différemment. On rend donc une mire —
     * un aplat par niveau, filtré exactement comme l'interface — puis on <b>relit les pixels</b> et
     * on calcule le rapport de contraste WCAG 2.1 par rapport au fond. Le résultat part dans
     * {@code contraste.txt} à côté des captures : c'est vérifiable, pas déclaratif.
     *
     * <p>Seuils visés : ≥ 4,5:1 pour tout texte, ≥ 3:1 pour un élément purement graphique.
     */
    private void mesurerContraste(Path out) throws Exception {
        String[] noms   = { "BG (fond)", "NOISE (papier peint)", "FRAME (cadres, points)",
                            "DIM (texte secondaire)", "TEXT (texte courant)",
                            "ACCENT (ce qui compte)", "HOT (crete, alerte)" };
        javafx.scene.paint.Color[] src = { TerminalTheme.BG, TerminalTheme.NOISE, TerminalTheme.FRAME,
                            TerminalTheme.DIM, TerminalTheme.TEXT, TerminalTheme.ACCENT,
                            TerminalTheme.HOT };
        double cw = 120, chh = 60;
        javafx.scene.layout.HBox mire = new javafx.scene.layout.HBox();
        for (javafx.scene.paint.Color c : src) {
            javafx.scene.shape.Rectangle r = new javafx.scene.shape.Rectangle(cw, chh, c);
            mire.getChildren().add(r);
        }
        StackPane root = new StackPane(mire);
        root.setPrefSize(cw * src.length, chh);
        root.setStyle("-fx-background-color: #000000;");
        Scene sc = new Scene(root, cw * src.length, chh);
        root.applyCss();
        root.layout();
        // MÊME filtre que celui posé sur les écrans du launcher.
        root.setEffect(TerminalTheme.phosphore(cw * src.length, chh));
        WritableImage img = sc.snapshot(null);
        javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(img, null),
                "png", out.resolve("contraste-mire.png").toFile());

        javafx.scene.image.PixelReader px = img.getPixelReader();
        javafx.scene.paint.Color fond = px.getColor((int) (cw * 0.5), (int) (chh / 2));
        StringBuilder rapport = new StringBuilder();
        rapport.append("Contraste WCAG 2.1 du theme DEV, mesure sur les pixels RENDUS\n")
               .append("(apres desaturation + multiplication par ").append(TerminalTheme.PHOSPHORE)
               .append(")\n\nreference de fond : ").append(hexOf(fond)).append("\n\n");
        for (int i = 0; i < src.length; i++) {
            javafx.scene.paint.Color c = px.getColor((int) (cw * i + cw / 2), (int) (chh / 2));
            double ratio = contraste(c, fond);
            rapport.append(String.format(java.util.Locale.ROOT, "%-26s %s   %6.2f:1   %s%n",
                    noms[i], hexOf(c), ratio,
                    i == 0 ? "-" : ratio >= 4.5 ? "texte OK" : ratio >= 3.0 ? "graphique OK (pas de texte)"
                                                             : "decoratif seulement"));
        }
        Files.writeString(out.resolve("contraste.txt"), rapport.toString());
        System.out.print(rapport);
    }

    private static String hexOf(javafx.scene.paint.Color c) {
        return String.format("#%02X%02X%02X", (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }

    /** Rapport de contraste WCAG 2.1 entre deux couleurs. */
    private static double contraste(javafx.scene.paint.Color a, javafx.scene.paint.Color b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(javafx.scene.paint.Color c) {
        return 0.2126 * lin(c.getRed()) + 0.7152 * lin(c.getGreen()) + 0.0722 * lin(c.getBlue());
    }

    private static double lin(double v) {
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /**
     * Mesure le coût d'UNE image de l'écran d'ouverture : composition de la grille + peinture du
     * canvas. Donne le chiffre qui compte réellement — « combien de processeur pour 30 images/s »
     * — sans le mélanger au démarrage de la JVM, qui domine tout le reste au lancement.
     */
    private void mesurerCout(AsciiReveal.Style style) {
        TerminalBoot boot = new TerminalBoot(style);
        StackPane root = new StackPane(boot);
        root.setPrefSize(W, H);
        new Scene(root, W, H);
        root.applyCss();
        root.layout();
        for (int i = 0; i < 200; i++) boot.renderAt(0.5 + (i % 250) * 0.01);   // chauffe le JIT
        long t0 = System.nanoTime();
        final int N = 600;
        for (int i = 0; i < N; i++) boot.renderAt(0.5 + (i % 250) * 0.01);
        double msParImage = (System.nanoTime() - t0) / 1e6 / N;
        System.out.printf(java.util.Locale.ROOT,
                "[preview] cout d'une image : %.3f ms  →  %.1f %% d'un coeur a 30 images/s%n",
                msParImage, msParImage * 30 / 10.0);
    }
    }

    private TerminalPreview() {}
}
