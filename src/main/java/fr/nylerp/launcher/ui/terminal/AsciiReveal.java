package fr.nylerp.launcher.ui.terminal;

/**
 * Apparition animée d'un mot en ASCII art.
 *
 * <p>La classe est <b>purement fonctionnelle</b> : {@link #stamp} écrit dans une {@link CharGrid}
 * l'état du banner à l'avancement {@code p ∈ [0,1]}, sans conserver aucun état interne. Le hasard
 * (caractères de brouillage, retards de verrouillage) vient d'un hachage de la position
 * {@code (col,row)}, pas d'un {@code Random} : deux appels au même {@code p} rendent exactement la
 * même image.
 *
 * <p><b>Pourquoi cette contrainte</b> — c'est elle qui rend l'animation capturable image par image
 * hors ligne ({@code TerminalPreview} dans les sources de test) : on prouve l'apparition en
 * regardant des instants précis de la séquence, au lieu d'affirmer qu'elle est belle. Elle rend
 * aussi l'animation insensible aux images sautées : le rendu dépend du temps écoulé, jamais du
 * nombre d'images déjà dessinées.
 */
public final class AsciiReveal {

    /** Variantes d'apparition essayées avant de trancher (voir le rapport de livraison). */
    public enum Style {
        /** Ligne de balayage verticale : révélé derrière, crête blanche, bruit devant. */
        BALAYAGE,
        /** Chaque cellule tourne sur des caractères aléatoires puis se verrouille. */
        DECODAGE,
        /** Le mot est là dès la première image mais déchiré/décalé, il se stabilise. */
        GLITCH,
        /** Frappe au clavier, ligne par ligne, curseur bloc à la tête d'écriture. */
        FRAPPE,
        /** Retenue : balayage <i>et</i> décodage — la crête passe, les cellules se verrouillent derrière. */
        BALAYAGE_DECODE
    }

    /** Caractères de brouillage — ASCII 7 bits uniquement (cf. {@link AsciiFont}). */
    private static final char[] NOISE_CHARS =
            "!<>-_\\/[]{}=+*^?$%&@#01234567abcdef".toCharArray();

    private final String[] art;
    /** Largeur de la crête lumineuse, en colonnes. */
    private static final int CREST = 3;
    /** Colonnes de bruit d'annonce DEVANT la crête. */
    private static final int LEAD  = 9;
    /** Retard maximal de verrouillage d'une cellule APRÈS le passage de la crête, en colonnes. */
    private static final double MAX_LOCK = 7.5;

    public final int width;
    public final int height;

    public AsciiReveal(String text) {
        this.art = AsciiFont.render(text);
        this.height = art.length;
        this.width = art[0].length();
    }

    /**
     * Écrit l'état du banner dans la grille.
     *
     * @param p     avancement, borné à [0,1]. {@code p >= 1} ⇒ mot final, stable.
     * @param seed  décale le hasard : deux banners voisins ne scintillent pas en phase.
     * @param color index de palette du mot une fois révélé
     */
    public void stamp(CharGrid grid, int col0, int row0, Style style, double p, long seed,
                      int color, double timeSeconds) {
        double t = Math.max(0.0, Math.min(1.0, p));
        // Garde-fou : à p = 1 le mot DOIT être entier et figé. Sans cette sortie explicite,
        // les cellules les plus à droite pouvaient rester en brouillage indéfiniment (leur
        // temps de verrouillage tombait après la fin du balayage) — le banner restait
        // définitivement sale sur son bord droit.
        if (t >= 1.0) { stampFinal(grid, col0, row0, color); return; }
        switch (style) {
            case BALAYAGE        -> balayage(grid, col0, row0, t, seed, color, false, timeSeconds);
            case BALAYAGE_DECODE -> balayage(grid, col0, row0, t, seed, color, true,  timeSeconds);
            case DECODAGE        -> decodage(grid, col0, row0, t, seed, color, timeSeconds);
            case GLITCH          -> glitch  (grid, col0, row0, t, seed, color, timeSeconds);
            case FRAPPE          -> frappe  (grid, col0, row0, t, seed, color);
        }
    }

    /** Mot final, sans effet — état stable de fin d'animation. */
    public void stampFinal(CharGrid grid, int col0, int row0, int color) {
        for (int r = 0; r < height; r++) {
            String line = art[r];
            for (int c = 0; c < width; c++) {
                if (line.charAt(c) != ' ') grid.put(col0 + c, row0 + r, '#', color);
            }
        }
    }

    // ── Variantes ───────────────────────────────────────────────────────────────────────────────

    /**
     * Balayage : une crête lumineuse traverse le mot de gauche à droite. Derrière elle le mot est
     * acquis ; devant, les cellules qui deviendront des pleins clignotent en bruit — c'est ce qui
     * donne la sensation que les lettres « se matérialisent » plutôt qu'elles n'apparaissent.
     *
     * @param decode quand vrai, une cellule dépassée par la crête ne se fige pas tout de suite :
     *               elle tourne encore quelques dixièmes sur des caractères aléatoires. Mélange les
     *               deux effets, c'est la variante retenue.
     */
    private void balayage(CharGrid grid, int col0, int row0, double p, long seed,
                          int color, boolean decode, double time) {
        // La course de la crête couvre la traîne d'annonce (LEAD) ET le retard de verrouillage
        // le plus long (MAX_LOCK) : à p = 1 la dernière cellule a fini de se verrouiller.
        double head = p * (width + LEAD + CREST + MAX_LOCK) - LEAD;
        for (int r = 0; r < height; r++) {
            String line = art[r];
            for (int c = 0; c < width; c++) {
                boolean solid = line.charAt(c) != ' ';
                double d = head - c;                    // >0 : la crête est déjà passée
                if (d < -LEAD) continue;                // pas encore atteint
                if (d < 0) {                            // devant la crête : bruit d'annonce
                    if (!solid) continue;
                    if (blink(c, r, seed, time, 22)) continue;   // scintillement
                    grid.put(col0 + c, row0 + r, noiseChar(c, r, seed, time, 18), TerminalTheme.C_NOISE);
                    continue;
                }
                if (d <= CREST) {                       // crête : tout s'allume, plein ou pas
                    char ch = solid ? '#' : '|';
                    grid.put(col0 + c, row0 + r, ch, TerminalTheme.C_HOT);
                    continue;
                }
                if (!solid) continue;
                if (decode) {
                    // Temps de verrouillage propre à la cellule, compté APRÈS le passage de la crête.
                    double lockAfter = 1.5 + (MAX_LOCK - 1.5) * rand01(c, r, seed + 77);
                    if (d < CREST + lockAfter) {
                        grid.put(col0 + c, row0 + r, noiseChar(c, r, seed, time, 26), TerminalTheme.C_ACCENT);
                        continue;
                    }
                }
                grid.put(col0 + c, row0 + r, '#', color);
            }
        }
        // Faisceau vertical : la crête dépasse d'une ligne au-dessus et en dessous du mot, ce qui
        // la fait lire comme un scanner qui traverse l'écran et non comme un simple dégradé.
        int hc = (int) Math.round(head);
        if (hc >= -CREST && hc <= width + CREST) {
            for (int c = Math.max(0, hc - CREST); c <= hc && c < width; c++) {
                grid.put(col0 + c, row0 - 1, '|', TerminalTheme.C_HOT);
                grid.put(col0 + c, row0 + height, '|', TerminalTheme.C_HOT);
            }
        }
    }

    /** Décodage : chaque cellule tourne sur des caractères aléatoires puis se verrouille. */
    private void decodage(CharGrid grid, int col0, int row0, double p, long seed,
                          int color, double time) {
        for (int r = 0; r < height; r++) {
            String line = art[r];
            for (int c = 0; c < width; c++) {
                if (line.charAt(c) == ' ') continue;
                double start = 0.10 * rand01(c, r, seed + 5);
                double lock  = start + 0.35 + 0.45 * (c / (double) width) + 0.18 * rand01(c, r, seed);
                if (p < start) continue;
                if (p < lock) {
                    grid.put(col0 + c, row0 + r, noiseChar(c, r, seed, time, 24),
                             p < lock - 0.10 ? TerminalTheme.C_NOISE : TerminalTheme.C_ACCENT);
                } else if (p < lock + 0.06) {
                    grid.put(col0 + c, row0 + r, '#', TerminalTheme.C_HOT);
                } else {
                    grid.put(col0 + c, row0 + r, '#', color);
                }
            }
        }
    }

    /** Glitch : le mot est entier dès le départ mais déchiré ; les déchirures s'amortissent. */
    private void glitch(CharGrid grid, int col0, int row0, double p, long seed,
                        int color, double time) {
        double amp = (1.0 - p) * (1.0 - p) * 7.0;
        for (int r = 0; r < height; r++) {
            String line = art[r];
            // Décalage horizontal propre à la ligne, qui saute quelques fois par seconde.
            int tick = (int) (time * 11) + r * 31;
            int shift = (int) Math.round((rand01(tick, r, seed) * 2 - 1) * amp);
            boolean torn = rand01(tick, r, seed + 3) < 0.22 * (1.0 - p);
            for (int c = 0; c < width; c++) {
                if (line.charAt(c) == ' ') continue;
                int cc = c + shift;
                if (torn) {
                    grid.put(col0 + cc, row0 + r, noiseChar(c, r, seed, time, 30), TerminalTheme.C_HOT);
                } else {
                    grid.put(col0 + cc, row0 + r, '#',
                             p > 0.92 ? color : (rand01(c, r, seed + tick) < 0.08
                                     ? TerminalTheme.C_HOT : color));
                }
            }
        }
    }

    /** Frappe : le mot est tapé ligne par ligne, curseur bloc à la tête d'écriture. */
    private void frappe(CharGrid grid, int col0, int row0, double p, long seed, int color) {
        double typed = p * (width * height);
        for (int r = 0; r < height; r++) {
            String line = art[r];
            int upTo = (int) Math.max(0, Math.min(width, typed - (long) r * width));
            for (int c = 0; c < upTo; c++) {
                if (line.charAt(c) == ' ') continue;
                grid.put(col0 + c, row0 + r, '#', color);
            }
            if (upTo > 0 && upTo < width) {
                grid.put(col0 + upTo, row0 + r, '_', TerminalTheme.C_HOT);
            }
        }
    }

    // ── Hasard reproductible ────────────────────────────────────────────────────────────────────

    /** Hachage entier sans état : même (col,row,seed) ⇒ même valeur, sur toutes les machines. */
    private static int hash(int c, int r, long seed) {
        int h = (int) (seed ^ 0x9E3779B9L);
        h ^= c * 0x27D4EB2D;
        h = (h ^ (h >>> 15)) * 0x85EBCA6B;
        h ^= r * 0x165667B1;
        h = (h ^ (h >>> 13)) * 0xC2B2AE35;
        return h ^ (h >>> 16);
    }

    private static double rand01(int c, int r, long seed) {
        return (hash(c, r, seed) >>> 8) / (double) (1 << 24);
    }

    /** Caractère de brouillage qui change {@code hz} fois par seconde pour la cellule donnée. */
    private static char noiseChar(int c, int r, long seed, double time, int hz) {
        int tick = (int) (time * hz);
        int h = hash(c, r, seed + tick * 2654435761L);
        return NOISE_CHARS[Math.floorMod(h, NOISE_CHARS.length)];
    }

    /** Scintillement : vrai ⇒ la cellule est éteinte sur cette image. */
    private static boolean blink(int c, int r, long seed, double time, int hz) {
        int tick = (int) (time * hz);
        return rand01(c + tick, r, seed + 11) < 0.45;
    }
}
