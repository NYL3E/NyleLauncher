package fr.nylerp.launcher.ui.terminal;

import javafx.animation.AnimationTimer;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/**
 * Écran d'ouverture du launcher <b>DEV</b> : un terminal qui démarre, écrit ses lignes de journal,
 * puis compose « NYLELAUNCHER / DEV » en ASCII art animé avant de s'effacer.
 *
 * <p><b>Canal DEV uniquement.</b> Le seul appelant est {@code LauncherApp.start()}, sous garde
 * {@code Constants.DEV}. Un joueur de production ne construit jamais cette classe.
 *
 * <p><b>Coût processeur — les trois décisions qui comptent</b> :
 * <ol>
 *   <li><b>Un seul nœud.</b> Tout l'écran est un {@code Canvas} unique peint depuis une
 *       {@link CharGrid} ; aucun layout JavaFX n'est recalculé entre deux images.</li>
 *   <li><b>Cadence bridée à {@value #FPS} images/s</b> — l'{@code AnimationTimer} est appelé à la
 *       fréquence de l'écran (60, 120, voire 144 Hz) ; on ignore les impulsions trop rapprochées.
 *       Au-delà de 30 images/s, l'œil ne gagne rien sur du texte qui scintille, mais le processeur
 *       paie plein tarif.</li>
 *   <li><b>Arrêt définitif.</b> À la fin de la séquence le timer est stoppé, le nœud retiré de la
 *       scène et le filtre clavier désenregistré : l'animation ne coûte plus rien, pas même une
 *       impulsion par image. Il ne reste aucune animation permanente sur cet écran.</li>
 * </ol>
 *
 * <p><b>Lecture au centième lancement</b> — la séquence dure {@value #TOTAL} s et <b>un clic ou une
 * touche la passe</b> : l'accueil se termine alors en {@value #SKIP_FADE} s. Le rendu ne dépend que
 * du temps écoulé (jamais du nombre d'images dessinées), donc sauter des images n'accélère ni ne
 * ralentit la séquence.
 */
public final class TerminalBoot extends Pane {

    /** Durée totale de la séquence, effacement compris. */
    public static final double TOTAL = 3.05;
    /** Durée de l'effacement quand on passe l'animation. */
    public static final double SKIP_FADE = 0.22;
    /** Cadence maximale : au-delà, on saute l'impulsion. */
    private static final int FPS = 30;

    // Repères de la séquence, en secondes.
    private static final double LOG_START   = 0.04;
    private static final double LOG_STEP    = 0.095;   // décalage entre deux lignes de journal
    private static final double LOG_SPEED   = 320.0;   // caractères par seconde
    private static final double BANNER_AT   = 0.52, BANNER_DUR = 1.20;
    private static final double DEV_AT      = 1.28, DEV_DUR    = 0.62;
    private static final double SUB_AT      = 1.85;
    private static final double BAR_AT      = 1.95, BAR_DUR    = 0.80;
    /** Extinction : écrasement vertical façon tube cathodique qu'on éteint. */
    private static final double FADE_AT     = 2.75, FADE_DUR   = 0.30;

    private final Canvas canvas = new Canvas();
    private final AsciiReveal bannerWord = new AsciiReveal("NYLELAUNCHER");
    private final AsciiReveal bannerDev  = new AsciiReveal("DEV");
    private final AsciiReveal.Style style;
    private final String[] logLines;

    private CharGrid grid;
    private double gridW = -1, gridH = -1;
    private double cellW, cellH;

    private AnimationTimer timer;
    private long startNanos = 0;
    /** Décalage appliqué quand le joueur passe l'animation : on saute directement à l'effacement. */
    private double skipShift = 0;
    private Runnable onDone;
    private boolean finished;
    /** Vrai dès que le joueur a passé la séquence : l'invite l'écrit avant l'extinction. */
    private boolean passe;

    public TerminalBoot() { this(AsciiReveal.Style.BALAYAGE_DECODE); }

    public TerminalBoot(AsciiReveal.Style style) {
        this.style = style;
        this.logLines = buildLogLines();
        getChildren().add(canvas);
        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);
        setPickOnBounds(true);
        setStyle("-fx-background-color: " + hex(TerminalTheme.BG) + ";");
        setOnMouseClicked(e -> skip());
    }

    // ── Cycle de vie ────────────────────────────────────────────────────────────────────────────

    /**
     * Superpose l'écran d'ouverture à la scène, joue la séquence puis rend la main à l'interface
     * réelle.
     *
     * <p>Mécanique : la racine courante est déplacée dans un {@code StackPane} avec l'écran par
     * dessus ; à la fin on la ressort et on la remet en racine. Passer par un empilement plutôt que
     * par une fenêtre séparée évite tout clignotement et garde la taille de fenêtre déjà calculée
     * par {@code stage.sizeToScene()}.
     */
    public static void playOn(Scene scene) {
        Parent real = scene.getRoot();
        StackPane wrap = new StackPane();
        scene.setRoot(wrap);                 // détache d'abord la vraie racine…
        TerminalBoot boot = new TerminalBoot();
        wrap.getChildren().addAll(real, boot); // …puis on la remet comme enfant, sous l'écran
        boot.play(scene, () -> {
            wrap.getChildren().remove(real);
            scene.setRoot(real);
        });
    }

    private void play(Scene scene, Runnable done) {
        this.onDone = done;
        javafx.event.EventHandler<KeyEvent> skipKey = e -> skip();
        scene.addEventFilter(KeyEvent.KEY_PRESSED, skipKey);
        requestFocus();

        final long minGap = 1_000_000_000L / FPS;
        timer = new AnimationTimer() {
            private long last = 0;
            @Override public void handle(long now) {
                if (startNanos == 0) startNanos = now;
                if (now - last < minGap) return;      // cadence bridée
                last = now;
                double t = (now - startNanos) / 1_000_000_000.0 + skipShift;
                renderAt(t);
                if (t >= TOTAL) {
                    stop();
                    if (!finished) {
                        finished = true;
                        scene.removeEventFilter(KeyEvent.KEY_PRESSED, skipKey);
                        if (onDone != null) onDone.run();
                    }
                }
            }
        };
        timer.start();
    }

    /** Passe l'animation : on saute juste avant l'extinction, qui garde sa durée courte. */
    public void skip() {
        if (finished) return;
        double now = startNanos == 0 ? 0
                : (System.nanoTime() - startNanos) / 1_000_000_000.0 + skipShift;
        double target = TOTAL - SKIP_FADE;
        if (now >= target) return;
        passe = true;                 // l'invite écrit « passer » : le clic a une réponse visible
        skipShift += target - now;
    }

    // ── Rendu ───────────────────────────────────────────────────────────────────────────────────

    @Override protected void layoutChildren() {
        double w = getWidth(), h = getHeight();
        canvas.setWidth(w);
        canvas.setHeight(h);
        // Filtre phosphore posé sur CE nœud (et pas sur un parent commun avec l'interface) :
        // deux applications imbriquées multiplieraient le vert deux fois et assombriraient tout.
        if (w > 0 && h > 0 && effetPose != h) {
            setEffect(TerminalTheme.phosphore(w, h));
            effetPose = h;
        }
    }

    /** Hauteur pour laquelle le filtre courant a été construit ; évite de le recréer par image. */
    private double effetPose = -1;

    /**
     * Dessine l'image de la séquence correspondant à l'instant {@code t} (en secondes).
     * Fonction pure du temps : c'est ce qui permet de capturer des instants précis hors ligne.
     */
    public void renderAt(double t) {
        double w = getWidth() > 0 ? getWidth() : canvas.getWidth();
        double h = getHeight() > 0 ? getHeight() : canvas.getHeight();
        if (w <= 0 || h <= 0) return;
        ensureGrid(w, h);

        // ── Extinction façon tube cathodique ───────────────────────────────────────────────────
        // Plutôt qu'un fondu, l'image s'écrase verticalement jusqu'à une raie lumineuse qui
        // s'éteint : le launcher apparaît DERRIÈRE, comme si l'écran de démarrage se refermait.
        double pFade = (t - FADE_AT) / FADE_DUR;
        if (pFade > 0) {
            double k = Math.min(1.0, pFade);
            double ecrase = 1.0 - k * k;                       // rapide au début, doux à la fin
            setScaleY(Math.max(0.006, ecrase));
            setOpacity(k > 0.80 ? Math.max(0.0, 1.0 - (k - 0.80) / 0.20) : 1.0);
        } else {
            setScaleY(1.0);
            setOpacity(1.0);
        }

        grid.clear();
        int left = 3;
        boolean occupe = false;   // vrai tant que quelque chose s'écrit : pilote le curseur

        // ── En-tête ────────────────────────────────────────────────────────────────────────────
        grid.write(left, 1, "nyle-launcher", TerminalTheme.C_DIM);
        grid.write(left + 14, 1, "// CANAL DEV", TerminalTheme.C_HOT);
        String right = java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        grid.write(grid.cols - right.length() - left, 1, right, TerminalTheme.C_FRAME);
        for (int c = left; c < grid.cols - left; c++) grid.put(c, 2, '-', TerminalTheme.C_FRAME);

        // ── Journal de démarrage, tapé ligne par ligne ─────────────────────────────────────────
        int logRow = 4;
        for (int i = 0; i < logLines.length; i++) {
            double start = LOG_START + i * LOG_STEP;
            if (t < start) { occupe = true; break; }
            int typed = (int) ((t - start) * LOG_SPEED);
            String line = logLines[i];
            int shown = Math.min(typed, line.length());
            grid.writeTyped(left, logRow + i, line, shown, TerminalTheme.C_DIM);
            // Le tag entre crochets est l'information utile : on le remonte d'un cran.
            int fin = line.indexOf(']');
            if (fin > 0 && shown > fin) {
                grid.write(left, logRow + i, line.substring(0, fin + 1), TerminalTheme.C_TEXT);
            }
            if (shown < line.length()) {
                grid.put(left + shown, logRow + i, '_', TerminalTheme.C_HOT);
                occupe = true;
                break;                    // une seule ligne en cours de frappe à la fois
            }
            // Statut final (« ok », « verrouille ») à l'intensité maximale.
            int sep = line.lastIndexOf("  ");
            if (sep > 0) grid.write(left + sep + 2, logRow + i, line.substring(sep + 2), TerminalTheme.C_ACCENT);
        }

        // ── Composition verticale ──────────────────────────────────────────────────────────────
        // Le bloc central est centré dans l'espace RESTANT entre le journal et l'invite, jamais
        // posé à une ligne fixe : la hauteur de grille dépend de la police résolue par l'OS, et un
        // repère en dur faisait déborder la barre de chargement hors de l'écran sur les polices
        // les plus hautes.
        final int BLOCK_H = bannerWord.height + 1 + bannerDev.height + 3 + 2;  // mot, DEV, sous-titre, barre
        int top = logRow + logLines.length + 1;
        int bottom = grid.rows - 3;                       // au-dessus de l'invite
        int bannerRow = top + Math.max(0, (bottom - top - BLOCK_H) / 2);

        // ── Banner ASCII ───────────────────────────────────────────────────────────────────────
        int bannerCol = Math.max(left, (grid.cols - bannerWord.width) / 2);
        double pWord = (t - BANNER_AT) / BANNER_DUR;
        if (pWord > 0) {
            if (pWord < 1) occupe = true;
            bannerWord.stamp(grid, bannerCol, bannerRow, style, pWord, 1337, TerminalTheme.C_ACCENT, t);
        }
        int devCol = bannerCol + bannerWord.width - bannerDev.width;
        int devRow = bannerRow + bannerWord.height + 1;
        double pDev = (t - DEV_AT) / DEV_DUR;
        if (pDev > 0) {
            if (pDev < 1) occupe = true;
            // « DEV » est peint à l'intensité MAXIMALE du tube : la hiérarchie se fait par
            // intensité, pas par teinte — le thème n'a qu'une seule couleur.
            bannerDev.stamp(grid, devCol, devRow, style, pDev, 4242, TerminalTheme.C_HOT, t);
            // Filet à gauche du bloc DEV : rattache visuellement les deux mots.
            if (pDev > 0.85) {
                int mid = devRow + bannerDev.height / 2;
                for (int c = bannerCol; c < devCol - 2; c++) grid.put(c, mid, '-', TerminalTheme.C_FRAME);
                grid.write(bannerCol, mid, " canal de developpement ", TerminalTheme.C_DIM);
            }
        }

        // ── Sous-titre + barre de chargement ───────────────────────────────────────────────────
        int footRow = devRow + bannerDev.height + 2;
        if (t > SUB_AT) {
            String sub = "mods en test  ·  serveur de dev  ·  aucune donnee de production";
            int typed = (int) ((t - SUB_AT) * LOG_SPEED);
            grid.writeTyped(Math.max(left, (grid.cols - sub.length()) / 2), footRow, sub,
                    typed, TerminalTheme.C_DIM);
        }
        if (t > BAR_AT) {
            double p = Math.min(1.0, (t - BAR_AT) / BAR_DUR);
            String label = p < 0.34 ? "montage du coffre de comptes"
                         : p < 0.68 ? "lecture du depot pack-dev"
                         : p < 1.00 ? "composition de l'interface"
                                    : "pret";
            int barCells = 40;
            int span = barCells + 8 + label.length();
            int barCol = Math.max(left, (grid.cols - span) / 2);
            drawBar(grid, barCol, footRow + 2, barCells, p);
            grid.write(barCol + barCells + 8, footRow + 2, label, TerminalTheme.C_DIM);
        }

        // ── Invite qui RÉAGIT ──────────────────────────────────────────────────────────────────
        // Le curseur n'est pas décoratif : bloc plein tant que quelque chose s'écrit, tiret
        // clignotant quand la machine attend, et il répond au clic en écrivant « passer ».
        int inviteRow = grid.rows - 2;
        grid.write(left, inviteRow, ">", TerminalTheme.C_ACCENT);
        if (passe) {
            grid.write(left + 2, inviteRow, "passer", TerminalTheme.C_HOT);
            grid.put(left + 9, inviteRow, '#', TerminalTheme.C_HOT);
        } else if (occupe) {
            grid.put(left + 2, inviteRow, '#', TerminalTheme.C_HOT);
        } else if (((int) (t * 2.4)) % 2 == 0) {
            grid.put(left + 2, inviteRow, '_', TerminalTheme.C_HOT);
        }
        String hint = "[ clic pour passer ]";
        grid.write(grid.cols - hint.length() - left, inviteRow, hint, TerminalTheme.C_FRAME);

        // ── Peinture ───────────────────────────────────────────────────────────────────────────
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(TerminalTheme.BG);
        g.fillRect(0, 0, w, h);
        // Halo du tube autour du banner : donne de la profondeur sans rien animer de plus.
        double haloY = (h - grid.rows * cellH) / 2.0 + (bannerRow + bannerWord.height) * cellH;
        g.setFill(new javafx.scene.paint.RadialGradient(0, 0, 0.5, haloY / h, 0.62, true,
                javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0, Color.color(1, 1, 1, 0.055)),
                new javafx.scene.paint.Stop(1, Color.TRANSPARENT)));
        g.fillRect(0, 0, w, h);
        paintScanlines(g, w, h);
        grid.paint(g, (w - grid.cols * cellW) / 2.0, (h - grid.rows * cellH) / 2.0);
        // Raie d'extinction : au moment où l'image s'écrase, une barre lumineuse traverse l'écran.
        if (pFade > 0.25) {
            double inten = Math.min(1.0, (pFade - 0.25) / 0.5);
            g.setFill(Color.color(1, 1, 1, 0.75 * inten));
            g.fillRect(0, h / 2 - 1.5, w, 3);
        }
    }

    /** Barre de chargement en blocs : {@code [########........] 62%}. */
    private static void drawBar(CharGrid grid, int col, int row, int cells, double p) {
        int filled = (int) Math.round(p * cells);
        grid.put(col, row, '[', TerminalTheme.C_DIM);
        for (int i = 0; i < cells; i++) {
            boolean on = i < filled;
            grid.put(col + 1 + i, row, on ? '#' : '.',
                     on ? (i >= filled - 2 ? TerminalTheme.C_HOT : TerminalTheme.C_ACCENT)
                        : TerminalTheme.C_NOISE);
        }
        grid.put(col + cells + 1, row, ']', TerminalTheme.C_DIM);
        String pct = String.format("%3d%%", (int) Math.round(p * 100));
        grid.write(col + cells + 3, row, pct, TerminalTheme.C_TEXT);
    }

    /**
     * Lignes de balayage façon tube cathodique. Peintes à chaque image parce qu'on efface tout le
     * canvas, mais c'est une poignée de rectangles pleins : négligeable devant le texte.
     */
    private static void paintScanlines(GraphicsContext g, double w, double h) {
        g.setFill(Color.color(0, 0, 0, 0.20));
        for (double y = 0; y < h; y += 3) g.fillRect(0, y, w, 1);
    }

    private void ensureGrid(double w, double h) {
        if (grid != null && w == gridW && h == gridH) return;
        gridW = w; gridH = h;
        // La taille de police découle de la largeur visée du banner (≈ 76 % de la fenêtre) :
        // le cadrage est donc identique sur tous les OS, quelle que soit la police à chasse fixe
        // que JavaFX résout derrière la famille logique « Monospaced ».
        double ratio = CharGrid.advanceOf(TerminalTheme.mono(100)) / 100.0;
        double targetCell = (w * 0.70) / bannerWord.width;
        double size = Math.max(9.0, Math.min(20.0, targetCell / ratio));
        javafx.scene.text.Font f = TerminalTheme.mono(size);
        cellW = CharGrid.advanceOf(f);
        cellH = Math.ceil(size * 1.30);
        grid = new CharGrid(f, (int) Math.floor(w / cellW), (int) Math.floor(h / cellH),
                            TerminalTheme.palette());
    }

    // ── Contenu du journal ──────────────────────────────────────────────────────────────────────

    /**
     * Journal de démarrage.
     *
     * <p><b>Toutes les valeurs sont mesurées, aucune n'est décorative</b> : système, version de la
     * JVM et tas réellement alloué, compte réellement actif, serveur et dépôt de pack réellement
     * visés, durée réelle écoulée depuis le lancement du processus. Un écran qui invente ses lignes
     * est joli une fois puis ment tous les jours suivants ; celui-ci est un vrai diagnostic, et
     * c'est d'ailleurs la première chose utile à demander en capture d'écran quand un testeur
     * signale un problème.
     */
    private static String[] buildLogLines() {
        String os = System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " " + System.getProperty("os.arch");
        long tasMo = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        String compte = "aucun compte";
        try {
            fr.nylerp.launcher.auth.Account a = fr.nylerp.launcher.auth.AccountStore.active();
            if (a != null) compte = a.username() + " (" + (a.isOffline() ? "offline" : "microsoft") + ")";
        } catch (Throwable ignored) { /* écran de démarrage : jamais bloquant */ }
        String srv = fr.nylerp.launcher.config.Constants.SERVER_HOST
                + ":" + fr.nylerp.launcher.config.Constants.SERVER_PORT;
        String packTag = fr.nylerp.launcher.config.Constants.MANIFEST_URL.contains("pack-dev")
                ? "pack-dev" : "pack-latest";
        long uptime = uptimeMs();
        return new String[] {
                line("os",   os,                                                   "ok"),
                line("jvm",  "java " + System.getProperty("java.version") + " · " + tasMo + " Mo", "ok"),
                line("auth", compte,                                               "ok"),
                line("net",  srv,                                                  "cible"),
                line("pack", packTag,                                              "verrouille"),
                line("ui",   "payload " + fr.nylerp.launcher.config.Constants.PAYLOAD_VERSION
                             + " · demarrage " + uptime + " ms",                   "ok"),
        };
    }

    /** Temps réellement écoulé depuis le démarrage de la JVM ; 0 si le module de gestion manque. */
    private static long uptimeMs() {
        try {
            return java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime();
        } catch (Throwable t) {
            return 0L;
        }
    }

    /** {@code [ net  ] game45-fr.hosterfy.com:20652 .............  cible} */
    private static String line(String tag, String what, String status) {
        String head = String.format(java.util.Locale.ROOT, "[ %-4s ] %s ", tag, what);
        int width = 66;
        StringBuilder sb = new StringBuilder(head);
        while (sb.length() < width - status.length() - 2) sb.append('.');
        sb.append("  ").append(status);
        return sb.toString();
    }

    private static String hex(Color c) {
        return String.format("#%02X%02X%02X", (int) (c.getRed() * 255),
                (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
    }
}
