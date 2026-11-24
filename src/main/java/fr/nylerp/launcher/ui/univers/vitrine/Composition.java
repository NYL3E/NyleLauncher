package fr.nylerp.launcher.ui.univers.vitrine;

/**
 * LA PLANCHE FINALE D'UN MÉCÈNE (v2) : planche du Pokémon + ses deux couches de peau + SA peau, composées UNE fois. Même règle
 * que generateur/composer.py, vitrine-web.js (site) et NyleVecto : couches OPAQUES, R = u·4 + (h >> 2), G = v·4 + (h &amp; 3),
 * B = ombrage (0 : rien), couverture (h + 1) / 16 ; la couche extérieure est pondérée par l'alpha de la peau. Java pur
 * (tableaux ARGB non prémultipliés), essayable sans JavaFX.
 */
public final class Composition {
    private static final double PLAFOND = 1.1;

    private Composition() {}

    /** Peau 64 × 32 (ancien format) -> 64 × 64 comme le jeu (jambe et bras gauches = miroirs des droits) ; 64 × 64 rendue telle quelle. */
    public static int[] peau64(int[] p, int w, int h) {
        if (w == 64 && h == 64) return p;
        if (w != 64 || h != 32) return null;
        int[] n = new int[64 * 64];
        System.arraycopy(p, 0, n, 0, 64 * 32);
        int[][] blocs = {{0, 16, 16, 48}, {40, 16, 32, 48}};
        for (int[] b : blocs) {
            int sx = b[0], sy = b[1], dx = b[2], dy = b[3];
            miroir(p, n, sx + 4, sy, 4, 4, dx + 4, dy);
            miroir(p, n, sx + 8, sy, 4, 4, dx + 8, dy);
            miroir(p, n, sx, sy + 4, 4, 12, dx + 8, dy + 4);
            miroir(p, n, sx + 4, sy + 4, 4, 12, dx + 4, dy + 4);
            miroir(p, n, sx + 8, sy + 4, 4, 12, dx, dy + 4);
            miroir(p, n, sx + 12, sy + 4, 4, 12, dx + 12, dy + 4);
        }
        return n;
    }

    private static void miroir(int[] s, int[] d, int sx, int sy, int l, int h, int dx, int dy) {
        for (int y = 0; y < h; y++) for (int x = 0; x < l; x++) d[(dy + y) * 64 + dx + (l - 1 - x)] = s[(sy + y) * 64 + sx + x];
    }

    /** Compose en place dans {@code planche} (ARGB) ; {@code cint}, {@code cext} : couches de mêmes dimensions ; {@code peau} : 64 × 64. */
    public static void composer(int[] planche, int[] cint, int[] cext, int[] peau) {
        for (int pass = 0; pass < 2; pass++) {
            int[] c = pass == 0 ? cint : cext;
            boolean ext = pass == 1;
            for (int i = 0; i < planche.length; i++) {
                int q = c[i], B = q & 0xff;
                if (B == 0) continue;
                int R = (q >>> 16) & 0xff, G = (q >>> 8) & 0xff;
                int k = (G >> 2) * 64 + (R >> 2), P = peau[k];
                double s = B / 255.0 * PLAFOND;
                double al = ((((R & 3) << 2) | (G & 3)) + 1) / 16.0 * (ext ? ((P >>> 24) & 0xff) / 255.0 : 1);
                if (al <= 0) continue;
                int o = planche[i];
                double a = ((o >>> 24) & 0xff) / 255.0, na = al + a * (1 - al);
                int r = melange((P >>> 16) & 0xff, (o >>> 16) & 0xff, s, al, a, na);
                int g = melange((P >>> 8) & 0xff, (o >>> 8) & 0xff, s, al, a, na);
                int b = melange(P & 0xff, o & 0xff, s, al, a, na);
                planche[i] = ((int) Math.round(na * 255) << 24) | (r << 16) | (g << 8) | b;
            }
        }
    }

    private static int melange(int p, int o, double s, double al, double a, double na) {
        return (int) Math.min(255, Math.round((Math.min(255, p * s) * al + o * a * (1 - al)) / na));
    }

    /** Boîte des pixels opaques de chaque cellule {x0, y0, x1, y1} (repère de la cellule), null si vide : la zone de survol. */
    public static int[][] boites(int[] im, int largeur, int cw, int ch, int colonnes, int n) {
        int[][] out = new int[n][];
        for (int k = 0; k < n; k++) {
            int ox = (k % colonnes) * cw, oy = (k / colonnes) * ch, x0 = cw, y0 = ch, x1 = -1, y1 = -1;
            for (int y = 0; y < ch; y += 2) for (int x = 0; x < cw; x += 2) {
                if (((im[(oy + y) * largeur + ox + x] >>> 24) & 0xff) > 40) { x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
            }
            out[k] = x1 < 0 ? null : new int[]{x0, y0, x1 + 2, y1 + 2};
        }
        return out;
    }
}
