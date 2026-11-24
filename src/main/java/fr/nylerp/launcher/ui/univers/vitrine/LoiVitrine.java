package fr.nylerp.launcher.ui.univers.vitrine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * LA LOI DE LA VITRINE DES MÉCÈNES — port ligne pour ligne de {@code vitrine.js} (référence de la chorégraphie, la même pour le
 * site, le launcher et l'écran titre). Java pur (Gson seulement) : rien de l'affichage ici.
 *
 * <p>Parité : mêmes tirages (générateur entier mulberry32), mêmes calculs en double, {@link StrictMath} pour les fonctions
 * transcendantes (fdlibm, comme le moteur JavaScript) ; prouvée contre la référence produite par node
 * ({@code choregraphie/reference.mjs}, 600 s à 60 Hz).
 *
 * <p>Un {@link Programme} tire une suite de passages : au plus un par couloir (sol, ciel) à la fois, donc jamais deux
 * Pokémon qui se chevauchent ; rang 1 d'abord ; longues absences ; aucun volant pendant le passage de Ho-Oh.
 * {@link Programme#etat} rend, pour un temps t (secondes), la place de chaque Pokémon visible, l'image de sa planche et son
 * étiquette. Aucune allocation par image : les états sont pris dans une réserve.
 */
public final class LoiVitrine {

    private LoiVitrine() {}

    // ── données ─────────────────────────────────────────────────────────────────────────────────────

    public record Clip(int debut, int n, double duree) {
        static Clip lire(JsonObject o) { return o == null ? null : new Clip(o.get("debut").getAsInt(), o.get("n").getAsInt(), o.get("duree").getAsDouble()); }
    }

    /** La fiche publique d'une planche (planches/v1/&lt;espece&gt;-&lt;variante&gt;.json, ou le catalogue). */
    public static final class Fiche {
        public String espece, variante, allure, fichierPng, sha256Png;
        public int largeurCellule, hauteurCellule, colonnes, lignes;
        public Clip principal, arret, pause, depart, demitour;
        public double ancreX, ancreY, etiquetteX, etiquetteY, surech, vitesse, phaseArret, fondu, hauteurAffichee;
        public boolean pauseValide;

        public static Fiche lire(JsonObject o) {
            Fiche f = new Fiche();
            f.espece = o.get("espece").getAsString();
            f.variante = o.get("variante").getAsString();
            f.allure = o.get("allure").getAsString();
            JsonArray c = o.getAsJsonArray("cellule");
            f.largeurCellule = c.get(0).getAsInt();
            f.hauteurCellule = c.get(1).getAsInt();
            f.colonnes = o.get("colonnes").getAsInt();
            f.lignes = o.get("lignes").getAsInt();
            JsonObject cl = o.getAsJsonObject("clips");
            f.principal = Clip.lire(cl.getAsJsonObject("principal"));
            f.arret = Clip.lire(cl.has("arret") ? cl.getAsJsonObject("arret") : null);
            f.pause = Clip.lire(cl.has("pause") ? cl.getAsJsonObject("pause") : null);
            f.depart = Clip.lire(cl.has("depart") ? cl.getAsJsonObject("depart") : null);
            f.demitour = Clip.lire(cl.has("demitour") ? cl.getAsJsonObject("demitour") : null);
            f.ancreX = o.getAsJsonArray("ancre").get(0).getAsDouble();
            f.ancreY = o.getAsJsonArray("ancre").get(1).getAsDouble();
            f.etiquetteX = o.getAsJsonArray("etiquette").get(0).getAsDouble();
            f.etiquetteY = o.getAsJsonArray("etiquette").get(1).getAsDouble();
            f.surech = o.get("surech").getAsDouble();
            f.vitesse = o.has("vitesse") && !o.get("vitesse").isJsonNull() ? o.get("vitesse").getAsDouble() : 0;
            f.phaseArret = o.get("phase_arret").getAsDouble();
            f.fondu = o.get("fondu").getAsDouble();
            f.hauteurAffichee = o.get("hauteur_affichee").getAsDouble();
            f.pauseValide = o.has("pause") && o.get("pause").getAsBoolean();
            if (o.has("png")) {
                f.fichierPng = o.getAsJsonObject("png").get("fichier").getAsString();
                f.sha256Png = o.getAsJsonObject("png").get("sha256").getAsString();
            }
            return f;
        }

        boolean marche() { return "marche".equals(allure); }
    }

    /** Un mécène en vitrine : rang, pseudo, fiche de sa planche, et ce que l'affichage y accroche (sa texture). */
    public static final class Mecene {
        public final int rang;
        public final String pseudo;
        public final Fiche fiche;
        public final Object image;

        public Mecene(int rang, String pseudo, Fiche fiche, Object image) { this.rang = rang; this.pseudo = pseudo; this.fiche = fiche; this.image = image; }
    }

    /** La chorégraphie (choregraphie.json) : ce que la loi en lit. */
    public static final class Choreo {
        double[][] sol;
        double solEchelle, solInclinaison, solSuivi, cielY0, cielY1, arc0, arc1, inclinaison, hoohDebut, hoohPeriode, hoohDuree;
        double premier0, premier1, ecart0, ecart1, simultane, decalage0, decalage1, flane, stationnaire, pause0, pause1;
        double traverseeSol0, traverseeSol1, traverseeCiel0, traverseeCiel1, cadence0, cadence1, avance, repos, visiteMarche;
        // v2 : voies (plusieurs mécènes ensemble) et vol stationnaire des volants
        double pauseCiel0, pauseCiel1, voieSolDy, voieSolEchelle = 1, voieCielDy;
        int voiesSol = 1, voiesCiel = 1;
        public double etiquetteEcart, etiquetteFondu, etiquetteTaille, etiquetteMargeX, etiquetteMargeY, etiquetteBord, etiquetteZone;
        final double[][] rangs = {{1, 0, 0}, {1, 0, 0}, {1, 0, 0}, {1, 0, 0}};   // [rang] = {échelle, sol, ciel}
        final double[][] parts = new double[4][];   // [nombre de mécènes] = part du temps d'écran de chacun, par rang
        public final JsonObject brut;
        /** Taille des Pokémon, réglage unique (1 = la taille de conception) ; le catalogue du site peut la changer. */
        public double echelle = 1;

        private Choreo(JsonObject brut) { this.brut = brut; }

        public static Choreo lire(JsonObject o) {
            Choreo c = new Choreo(o);
            JsonArray l = o.getAsJsonObject("sol").getAsJsonArray("ligne");
            c.sol = new double[l.size()][2];
            for (int i = 0; i < l.size(); i++) { c.sol[i][0] = l.get(i).getAsJsonArray().get(0).getAsDouble(); c.sol[i][1] = l.get(i).getAsJsonArray().get(1).getAsDouble(); }
            JsonObject so = o.getAsJsonObject("sol");
            c.echelle = o.has("echelle") ? o.get("echelle").getAsDouble() : 1;
            c.solInclinaison = so.has("inclinaison") ? so.get("inclinaison").getAsDouble() : 0;
            c.solSuivi = so.has("suivi") ? so.get("suivi").getAsDouble() : 0;
            c.solEchelle = so.has("echelle") && so.get("echelle").getAsDouble() != 0 ? so.get("echelle").getAsDouble() : 1;
            JsonObject ci = o.getAsJsonObject("ciel");
            c.cielY0 = paire(ci, "y")[0]; c.cielY1 = paire(ci, "y")[1];
            c.arc0 = paire(ci, "arc")[0]; c.arc1 = paire(ci, "arc")[1];
            c.inclinaison = ci.get("inclinaison").getAsDouble();
            JsonObject h = o.getAsJsonObject("hooh");
            c.hoohDebut = h.get("debut").getAsDouble(); c.hoohPeriode = h.get("periode").getAsDouble(); c.hoohDuree = h.get("duree").getAsDouble();
            JsonObject r = o.getAsJsonObject("rythme");
            c.premier0 = paire(r, "premier")[0]; c.premier1 = paire(r, "premier")[1];
            c.ecart0 = paire(r, "ecart")[0]; c.ecart1 = paire(r, "ecart")[1];
            c.simultane = r.get("simultane").getAsDouble();
            c.decalage0 = paire(r, "decalage")[0]; c.decalage1 = paire(r, "decalage")[1];
            c.flane = r.get("flane").getAsDouble();
            c.stationnaire = r.get("stationnaire").getAsDouble();
            c.pause0 = paire(r, "pause")[0]; c.pause1 = paire(r, "pause")[1];
            c.traverseeSol0 = paire(r, "traversee_sol")[0]; c.traverseeSol1 = paire(r, "traversee_sol")[1];
            c.traverseeCiel0 = paire(r, "traversee_ciel")[0]; c.traverseeCiel1 = paire(r, "traversee_ciel")[1];
            c.cadence0 = paire(r, "cadence")[0]; c.cadence1 = paire(r, "cadence")[1];
            c.avance = r.get("avance").getAsDouble();
            c.repos = r.get("repos").getAsDouble();
            c.visiteMarche = r.has("visite_marche") ? r.get("visite_marche").getAsDouble() : 12;
            c.pauseCiel0 = r.has("pause_ciel") ? paire(r, "pause_ciel")[0] : c.pause0; c.pauseCiel1 = r.has("pause_ciel") ? paire(r, "pause_ciel")[1] : c.pause1;
            c.voiesSol = r.has("voies_sol") ? r.get("voies_sol").getAsInt() : 1; c.voiesCiel = r.has("voies_ciel") ? r.get("voies_ciel").getAsInt() : 1;
            c.voieSolDy = r.has("voie_sol_dy") ? r.get("voie_sol_dy").getAsDouble() : 0;
            c.voieSolEchelle = r.has("voie_sol_echelle") && r.get("voie_sol_echelle").getAsDouble() != 0 ? r.get("voie_sol_echelle").getAsDouble() : 1;
            c.voieCielDy = r.has("voie_ciel_dy") ? r.get("voie_ciel_dy").getAsDouble() : 0;
            JsonObject e = o.getAsJsonObject("etiquette");
            c.etiquetteEcart = e.get("ecart").getAsDouble();
            c.etiquetteFondu = e.get("fondu").getAsDouble();
            c.etiquetteTaille = e.get("taille").getAsDouble();
            c.etiquetteMargeX = e.getAsJsonArray("marge").get(0).getAsDouble();
            c.etiquetteMargeY = e.getAsJsonArray("marge").get(1).getAsDouble();
            c.etiquetteBord = e.get("bord").getAsDouble();
            c.etiquetteZone = e.get("zone").getAsDouble();
            if (o.has("rangs")) {
                JsonObject g = o.getAsJsonObject("rangs");
                for (int k = 1; k <= 3; k++) if (g.has(String.valueOf(k))) {
                    JsonObject q = g.getAsJsonObject(String.valueOf(k));
                    c.rangs[k] = new double[]{q.get("echelle").getAsDouble(), q.get("sol").getAsDouble(), q.get("ciel").getAsDouble()};
                }
                JsonObject od = g.getAsJsonObject("part");
                for (int n = 1; n <= 3; n++) if (od.has(String.valueOf(n))) {
                    JsonArray a = od.getAsJsonArray(String.valueOf(n));
                    c.parts[n] = new double[a.size()];
                    for (int i = 0; i < a.size(); i++) c.parts[n][i] = a.get(i).getAsDouble();
                }
            }
            return c;
        }

        private static double[] paire(JsonObject o, String k) { JsonArray a = o.getAsJsonArray(k); return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble()}; }

        /** La ligne des pieds (polyligne du repère), prolongée à plat hors de ses bouts. */
        public double solY(double x) {
            double[][] p = sol;
            if (x <= p[0][0]) return p[0][1];
            for (int i = 1; i < p.length; i++) if (x <= p[i][0]) { double f = (x - p[i - 1][0]) / (p[i][0] - p[i - 1][0]); return p[i - 1][1] + (p[i][1] - p[i - 1][1]) * f; }
            return p[p.length - 1][1];
        }
    }

    /** L'étendue visible du repère dans un lieu et où s'arrêter. */
    public static final class Lieu {
        public double x0 = 0, x1 = 1180, pauseSol0 = 880, pauseSol1 = 1110, pauseCiel0 = 520, pauseCiel1 = 980;
        /** Zones d'arrêt d'une visite (entrée et sortie par ce bord) ; null = pas de visite de ce côté. */
        public double[] visiteDroite = {900, 1080}, visiteGauche = {60, 180};
        /** Rectangles [x, y, l, h] du repère où une étiquette s'efface (logo, boutons, menu). */
        public double[][] interdits = new double[0][];
        /** Ho-Oh traverse-t-il le ciel de ce lieu, et l'écart de son horloge : temps du ciel − temps de la loi. */
        public boolean hooh = true;
        public double hoohDecalage = 0;
        /** La bande du ciel de ce lieu (ligne des pieds des volants) ; NaN = la commune (choreo.ciel.y). */
        public double cielY0 = Double.NaN, cielY1 = Double.NaN;
        /** L'étiquette agrandie pour sa taille minimale en pixels réels ({@code etiquette_min_px}) : posée par le lieu. */
        public double etiquetteEchelle = 1;
        /** Zone couverte à gauche (menu de l'écran titre, bouton du launcher) : les marcheurs restent à sa droite ; NaN = aucune. */
        public double marcheMin = Double.NaN;
        /** Voies au sol du lieu (0 : celles de la chorégraphie) ; un lieu étroit n'en a qu'une. */
        public int voiesSol;

        public static Lieu lire(JsonObject o, Lieu base) {
            Lieu l = new Lieu();
            if (base != null) { l.x0 = base.x0; l.x1 = base.x1; l.pauseSol0 = base.pauseSol0; l.pauseSol1 = base.pauseSol1; l.pauseCiel0 = base.pauseCiel0; l.pauseCiel1 = base.pauseCiel1; l.visiteDroite = base.visiteDroite; l.visiteGauche = base.visiteGauche; l.interdits = base.interdits; l.hooh = base.hooh; l.hoohDecalage = base.hoohDecalage; l.cielY0 = base.cielY0; l.cielY1 = base.cielY1; l.marcheMin = base.marcheMin; l.voiesSol = base.voiesSol; }
            if (o == null) return l;
            if (o.has("x0")) l.x0 = o.get("x0").getAsDouble();
            if (o.has("x1")) l.x1 = o.get("x1").getAsDouble();
            if (o.has("pause_sol")) { l.pauseSol0 = o.getAsJsonArray("pause_sol").get(0).getAsDouble(); l.pauseSol1 = o.getAsJsonArray("pause_sol").get(1).getAsDouble(); }
            if (o.has("pause_ciel")) { l.pauseCiel0 = o.getAsJsonArray("pause_ciel").get(0).getAsDouble(); l.pauseCiel1 = o.getAsJsonArray("pause_ciel").get(1).getAsDouble(); }
            if (o.has("visite_droite")) l.visiteDroite = zone(o.get("visite_droite"));
            if (o.has("visite_gauche")) l.visiteGauche = zone(o.get("visite_gauche"));
            if (o.has("hooh")) l.hooh = o.get("hooh").getAsBoolean();
            if (o.has("etiquette_echelle")) l.etiquetteEchelle = o.get("etiquette_echelle").getAsDouble();
            if (o.has("marche_min") && !o.get("marche_min").isJsonNull()) l.marcheMin = o.get("marche_min").getAsDouble();
            if (o.has("voies_sol")) l.voiesSol = o.get("voies_sol").getAsInt();
            if (o.has("ciel_y")) { l.cielY0 = o.getAsJsonArray("ciel_y").get(0).getAsDouble(); l.cielY1 = o.getAsJsonArray("ciel_y").get(1).getAsDouble(); }
            if (o.has("hooh_decalage")) l.hoohDecalage = o.get("hooh_decalage").getAsDouble();
            if (o.has("interdits")) {
                JsonArray a = o.getAsJsonArray("interdits");
                l.interdits = new double[a.size()][4];
                for (int i = 0; i < a.size(); i++) for (int j = 0; j < 4; j++) l.interdits[i][j] = a.get(i).getAsJsonArray().get(j).getAsDouble();
            }
            return l;
        }

        private static double[] zone(JsonElement e) {
            if (e == null || e.isJsonNull()) return null;
            JsonArray a = e.getAsJsonArray();
            return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble()};
        }
    }

    /** Un Pokémon visible à l'instant demandé (réutilisé d'une image à l'autre). */
    public static final class Etat {
        public Mecene mecene;
        public boolean sol;
        public double x, y, echelle, angle, etiquetteX, etiquetteY, etiquetteOpacite;
        public int dir, image, voie;
    }

    // ── hasard entier (mulberry32, identique à la référence) ────────────────────────────────────────

    public static final class Hasard {
        private int a;

        public Hasard(int graine) { this.a = graine; }

        public double suivant() {
            a += 0x6D2B79F5;
            int t = (a ^ (a >>> 15)) * (1 | a);
            t = (t + ((t ^ (t >>> 7)) * (61 | t))) ^ t;
            return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0;
        }

        public double entre(double a, double b) { return a + (b - a) * suivant(); }
    }

    static double[] rang(Choreo C, int r) { return r >= 1 && r <= 3 ? C.rangs[r] : C.rangs[0]; }

    /** L'étiquette s'efface près des bords du cadre visible et sur les zones interdites (taille estimée, la même partout). */
    /** Le pré est-il trop étroit (zone couverte) pour qu'un marcheur y passe ? */
    static boolean solFerme(Lieu l) { return !Double.isNaN(l.marcheMin) && l.x1 - l.marcheMin < 250; }
    static boolean borneG(Lieu l) { return !Double.isNaN(l.marcheMin) && l.marcheMin > l.x0 + 1; }

    static double etiquetteLibre(Choreo C, Lieu lieu, String pseudo, double ex, double ey) {
        double ke = lieu.etiquetteEchelle;   // l'étiquette agrandie pour sa taille minimale en pixels réels (posée par le lieu)
        double demiL = ((pseudo.length() * 0.6 + 1.25) * C.etiquetteTaille * ke) / 2 + C.etiquetteMargeX * ke, h = (C.etiquetteTaille * 1.18 + 2 * C.etiquetteMargeY) * ke;
        double f = lisse((Math.min(ex - lieu.x0, lieu.x1 - ex) - demiL) / C.etiquetteBord);
        for (double[] z : lieu.interdits) {
            double dx = Math.max(Math.max(z[0] - (ex + demiL), (ex - demiL) - (z[0] + z[2])), 0), dy = Math.max(Math.max(z[1] - ey, (ey - h) - (z[1] + z[3])), 0);
            f = Math.min(f, lisse(Math.max(dx, dy) / C.etiquetteZone));
        }
        return f;
    }

    static double lisse(double x) { x = x < 0 ? 0 : (x > 1 ? 1 : x); return x * x * (3 - 2 * x); }
    static double freinage(double u, double T) { double x = Math.min(1, Math.max(0, u / T)); return T * (x - x * x * x + x * x * x * x / 2); }
    static double acceleration(double u, double T) { double x = Math.min(1, Math.max(0, u / T)); return T * (x * x * x - x * x * x * x / 2); }
    static double mod(double a, double b) { return ((a % b) + b) % b; }
    static int imageBoucle(Clip c, double t) { int n = c.n(); int k = (int) StrictMath.floor(mod(t, c.duree()) / c.duree() * n); if (k >= n) k = n - 1; return c.debut() + k; }
    static int imageFondu(Clip c, double u, double T) { int k = (int) StrictMath.floor(u / T * c.n()); if (k < 0) k = 0; if (k >= c.n()) k = c.n() - 1; return c.debut() + k; }

    // ── passages ─────────────────────────────────────────────────────────────────────────────────────

    private static final int MARCHE = 0, ARRET = 1, PAUSE = 2, DEPART = 3, DEMITOUR = 4, SURPLACE = 5;

    private static final class Segment {
        final int type, dir;
        final double t0, t1, x0, phase, base;

        Segment(int type, double t0, double t1, double x0, double phase, int dir, double base) {
            this.type = type; this.t0 = t0; this.t1 = t1; this.x0 = x0; this.phase = phase; this.dir = dir; this.base = base;
        }
    }

    private static final class Passage {
        boolean sol;
        Mecene m;
        int dir;
        double s, v, r, debut, fin, xa, xb, yA, yB, bombe, ondA, ondP, ondPhase, dy;
        int voie;
        final List<Segment> segs = new ArrayList<>(6);
    }

    /** voie : 0 (devant) ou 1 (plus haut sur la colline, un peu plus petit). */
    private static Passage passageSol(Choreo C, Lieu lieu, Mecene m, double debut, Hasard h, int voie) {
        int v1 = voie != 0 ? 1 : 0;
        Fiche F = m.fiche;
        double[] G = rang(C, m.rang);
        double s = 1 / F.surech * C.solEchelle * G[0] * C.echelle * (v1 != 0 ? C.voieSolEchelle : 1);
        int dir = h.suivant() < 0.5 ? 1 : -1;
        double demiL = F.largeurCellule * s * 0.5 + 8;
        double D = (lieu.x1 - lieu.x0) + 2 * demiL, v = F.vitesse * s;
        double T = h.entre(C.traverseeSol0, C.traverseeSol1);
        Clip cp = F.principal, DT = F.demitour;
        double TB = F.fondu;
        boolean borne = borneG(lieu);
        boolean visite = (borne || D / (v * C.cadence1) > C.traverseeSol1) && F.pauseValide && F.pause != null && DT != null;
        if (visite && dir > 0 && (lieu.visiteGauche == null || borne)) dir = -1;
        double xa = dir > 0 ? lieu.x0 - demiL : lieu.x1 + demiL, xb = dir > 0 ? lieu.x1 + demiL : lieu.x0 - demiL;
        Passage p = new Passage();
        double t = debut, x = xa, phase, r;
        if (visite) {
            r = C.cadence1;
            double[] z = dir > 0 ? lieu.visiteGauche : lieu.visiteDroite;
            // l'arrêt : dans la zone du lieu, mais à au plus visiteMarche (12 s) de marche du bord et 200 unités du bord visible,
            // et à 60 au moins (il reste à l'écran)
            double xz = h.entre(z[0], z[1]), prof = Math.max(60, Math.min(200, C.visiteMarche * v * r - demiL));
            double xs = dir > 0 ? Math.max(lieu.x0 + 60, Math.min(xz, lieu.x0 + prof)) : Math.min(lieu.x1 - 60, Math.max(xz, lieu.x1 - prof));
            if (borne) xs = Math.max(xs, lieu.marcheMin + demiL);            // jamais sur la zone couverte
            double d1 = Math.abs(xs - xa);
            phase = mod(F.phaseArret - d1 / v, cp.duree());
            double t1 = t + d1 / v / r;
            p.segs.add(new Segment(MARCHE, t, t1, x, phase, dir, 0)); t = t1; x = xs;
            p.segs.add(new Segment(ARRET, t, t + TB / r, x, 0, dir, 0)); t += TB / r; x += dir * v * TB / 2;
            Clip cr = F.pause;
            double dp1 = h.entre(C.pause0, C.pause1);
            double fin1 = StrictMath.ceil((TB + dp1) / cr.duree()) * cr.duree();
            p.segs.add(new Segment(PAUSE, t, t + (fin1 - TB), x, 0, dir, TB)); t += fin1 - TB;
            p.segs.add(new Segment(DEMITOUR, t, t + DT.duree(), x, 0, dir, 0)); t += DT.duree();
            double dp2 = h.entre(C.pause0 * 0.5, C.pause1 * 0.5);
            double fin2 = StrictMath.ceil((DT.duree() + dp2) / cr.duree()) * cr.duree();
            dir = -dir;
            p.segs.add(new Segment(PAUSE, t, t + (fin2 - DT.duree()), x, 0, dir, DT.duree())); t += fin2 - DT.duree();
            p.segs.add(new Segment(DEPART, t, t + TB / r, x, 0, dir, 0)); t += TB / r; x += dir * v * TB / 2;
            p.segs.add(new Segment(MARCHE, t, t + Math.abs(xa - x) / v / r, x, mod(F.phaseArret + TB, cp.duree()), dir, 0));
            p.sol = true; p.m = m; p.dir = -dir; p.s = s; p.v = v; p.r = r; p.debut = debut; p.fin = p.segs.get(p.segs.size() - 1).t1; p.xa = xa; p.xb = xa; p.dy = G[1] + (v1 != 0 ? C.voieSolDy : 0); p.voie = v1;
            return p;
        }
        r = Math.min(C.cadence1, Math.max(C.cadence0, D / (v * T)));
        boolean flane = F.pauseValide && F.pause != null && h.suivant() < C.flane;
        double xs = flane ? h.entre(lieu.pauseSol0, lieu.pauseSol1) : 0;
        if (flane) {
            double d1 = Math.abs(xs - xa);
            phase = mod(F.phaseArret - d1 / v, cp.duree());
            double t1 = t + d1 / v / r;
            p.segs.add(new Segment(MARCHE, t, t1, x, phase, dir, 0));
            t = t1; x = xs;
            double tA = t + TB / r;
            p.segs.add(new Segment(ARRET, t, tA, x, 0, dir, 0)); t = tA; x += dir * v * TB / 2;
            Clip cr = F.pause;
            double dp = h.entre(C.pause0, C.pause1);
            double fin = StrictMath.ceil((TB + dp) / cr.duree()) * cr.duree();
            double tP = t + (fin - TB);
            p.segs.add(new Segment(PAUSE, t, tP, x, 0, dir, TB)); t = tP;
            double tD = t + TB / r;
            p.segs.add(new Segment(DEPART, t, tD, x, 0, dir, 0)); t = tD; x += dir * v * TB / 2;
            phase = mod(F.phaseArret + TB, cp.duree());
        } else phase = h.entre(0, cp.duree());
        double d2 = Math.abs(xb - x);
        p.segs.add(new Segment(MARCHE, t, t + d2 / v / r, x, phase, dir, 0));
        p.sol = true; p.m = m; p.dir = dir; p.s = s; p.v = v; p.r = r; p.debut = debut; p.fin = p.segs.get(p.segs.size() - 1).t1; p.xa = xa; p.xb = xb; p.dy = G[1] + (v1 != 0 ? C.voieSolDy : 0); p.voie = v1;
        return p;
    }

    /** voie : 0 (bande haute du ciel) ou 1 (plus bas de voie_ciel_dy). */
    private static Passage passageCiel(Choreo C, Lieu lieu, Mecene m, double debut, Hasard h, int voie) {
        int v1 = voie != 0 ? 1 : 0;
        double dyv = v1 != 0 ? C.voieCielDy : 0;
        Fiche F = m.fiche;
        double[] G = rang(C, m.rang);
        double s = 1 / F.surech * G[0] * C.echelle;
        int dir = h.suivant() < 0.5 ? 1 : -1;
        double demiL = F.largeurCellule * s * 0.5 + 8;
        double xa = dir > 0 ? lieu.x0 - demiL : lieu.x1 + demiL, xb = dir > 0 ? lieu.x1 + demiL : lieu.x0 - demiL;
        double D = Math.abs(xb - xa), T = h.entre(C.traverseeCiel0, C.traverseeCiel1), v = D / T;
        double Y0 = Double.isNaN(lieu.cielY0) ? C.cielY0 : lieu.cielY0, Y1 = Double.isNaN(lieu.cielY0) ? C.cielY1 : lieu.cielY1;   // la bande du lieu, sinon la commune
        double yA = h.entre(Y0, Y1) + G[2] + dyv, yB = h.entre(Y0, Y1) + G[2] + dyv, bombe = h.entre(C.arc0, C.arc1);
        double ondA = h.entre(4, 9), ondP = h.entre(5, 8), ondPhase = h.entre(0, 6.283);
        // il reste un moment SUR PLACE : vol stationnaire du modèle (clip de pause, fondus) s'il en a un, sinon son battement
        // de vol continu, sans avancer (surplace)
        boolean plane = h.suivant() < C.stationnaire, aPause = F.pauseValide && F.pause != null;
        double xs = plane ? h.entre(lieu.pauseCiel0, lieu.pauseCiel1) : 0, TB = F.fondu;
        Clip cp = F.principal;
        Passage p = new Passage();
        double t = debut, x = xa;
        double phase0 = h.entre(0, cp.duree());
        if (plane && !aPause) {
            double d1 = Math.abs(xs - xa), t1 = t + d1 / v, dp = h.entre(C.pauseCiel0, C.pauseCiel1);
            p.segs.add(new Segment(MARCHE, t, t1, x, phase0, dir, 0)); t = t1; x = xs;
            double ph = mod(phase0 + (t1 - debut), cp.duree());
            p.segs.add(new Segment(SURPLACE, t, t + dp, x, ph, dir, 0)); t += dp;
            p.segs.add(new Segment(MARCHE, t, t + Math.abs(xb - x) / v, x, mod(ph + dp, cp.duree()), dir, 0));
        } else if (plane) {
            double d1 = Math.abs(xs - xa);
            double t1 = t + d1 / v;
            p.segs.add(new Segment(MARCHE, t, t1, x, phase0, dir, 0)); t = t1; x = xs;
            double ph = mod(phase0 + (t1 - debut), cp.duree()), att = mod(F.phaseArret - ph, cp.duree());
            if (att > 1e-6) { p.segs.add(new Segment(MARCHE, t, t + att, x, ph, dir, 0)); t += att; x += dir * v * att; }
            p.segs.add(new Segment(ARRET, t, t + TB, x, 0, dir, 0)); t += TB; x += dir * v * TB / 2;
            Clip cr = F.pause;
            double dp = h.entre(C.pauseCiel0, C.pauseCiel1);
            double fin = StrictMath.ceil((TB + dp) / cr.duree()) * cr.duree();
            p.segs.add(new Segment(PAUSE, t, t + fin - TB, x, 0, dir, TB)); t += fin - TB;
            p.segs.add(new Segment(DEPART, t, t + TB, x, 0, dir, 0)); t += TB; x += dir * v * TB / 2;
            p.segs.add(new Segment(MARCHE, t, t + Math.abs(xb - x) / v, x, mod(F.phaseArret + TB, cp.duree()), dir, 0));
        } else p.segs.add(new Segment(MARCHE, t, t + D / v, x, phase0, dir, 0));
        p.sol = false; p.voie = v1; p.m = m; p.dir = dir; p.s = s; p.v = v; p.r = 1; p.debut = debut; p.fin = p.segs.get(p.segs.size() - 1).t1;
        p.xa = xa; p.xb = xb; p.yA = yA; p.yB = yB; p.bombe = bombe; p.ondA = ondA; p.ondP = ondP; p.ondPhase = ondPhase;
        return p;
    }

    private static boolean etatPassage(Choreo C, Lieu lieu, Passage p, double t, Etat e) {
        if (t < p.debut || t >= p.fin) return false;
        Fiche F = p.m.fiche;
        double TB = F.fondu;
        Segment sg = p.segs.get(0);
        for (Segment g : p.segs) if (t >= g.t0) sg = g;
        double u = t - sg.t0, x, r = p.r, ua = u * r, v = p.v, tourne = 1;
        int dir = sg.dir, image;
        if (sg.type == MARCHE) { x = sg.x0 + dir * v * ua; image = imageBoucle(F.principal, sg.phase + ua); }
        else if (sg.type == SURPLACE) { x = sg.x0; image = imageBoucle(F.principal, sg.phase + u); }
        else if (sg.type == ARRET) { x = sg.x0 + dir * v * freinage(ua, TB); image = imageFondu(F.arret, ua, TB); }
        else if (sg.type == PAUSE) { x = sg.x0; image = imageBoucle(F.pause, sg.base + u); }
        else if (sg.type == DEMITOUR) { x = sg.x0; image = imageFondu(F.demitour, u, F.demitour.duree()); tourne = 1 - 2 * lisse(u / F.demitour.duree()); }
        else { x = sg.x0 + dir * v * acceleration(ua, TB); image = imageFondu(F.depart, ua, TB); }
        double y, angle = 0;
        if (p.sol) {
            // les pieds sur le profil relevé de la colline ; le corps suit la pente (en partie, borné)
            y = C.solY(x) + p.dy;
            double pente = (C.solY(x + 4) - C.solY(x - 4)) / 8;
            angle = Math.max(-C.solInclinaison, Math.min(C.solInclinaison, StrictMath.atan(pente) * 180 / Math.PI * C.solSuivi));
        } else {
            double f = (x - p.xa) / (p.xb - p.xa);
            y = p.yA + (p.yB - p.yA) * f - p.bombe * StrictMath.sin(Math.PI * f) + p.ondA * StrictMath.sin(t / p.ondP * 6.283185307179586 + p.ondPhase);
            double df = 1e-3, f2 = Math.min(1, f + df);
            double y2 = p.yA + (p.yB - p.yA) * f2 - p.bombe * StrictMath.sin(Math.PI * f2);
            double den = (p.xb - p.xa) * (f2 - f);
            if (den == 0) den = 1;
            double pente = (y2 - (p.yA + (p.yB - p.yA) * f - p.bombe * StrictMath.sin(Math.PI * f))) / den;
            angle = Math.max(-C.inclinaison, Math.min(C.inclinaison, StrictMath.atan(pente) * 180 / Math.PI * 0.6));
        }
        double s = p.s;
        e.mecene = p.m; e.sol = p.sol; e.voie = p.voie; e.x = x; e.y = y; e.dir = dir; e.image = image; e.echelle = s; e.angle = angle;
        e.etiquetteX = x + dir * tourne * (F.etiquetteX - F.ancreX) * s;
        e.etiquetteY = y - (F.ancreY - F.etiquetteY) * s - C.etiquetteEcart;
        double entre = Math.min(t - p.debut, p.fin - t);
        e.etiquetteOpacite = lisse(entre / C.etiquetteFondu) * etiquetteLibre(C, lieu, p.m.pseudo, e.etiquetteX, e.etiquetteY);
        return true;
    }

    // ── le programme ─────────────────────────────────────────────────────────────────────────────────

    public static final class Programme {
        private final Choreo c;
        private final Lieu lieu;
        private final Hasard h;
        private final List<Mecene> mecenes = new ArrayList<>(3);
        private final List<Passage> passages = new ArrayList<>(8);
        private final Etat[] reserve = {new Etat(), new Etat(), new Etat(), new Etat(), new Etat(), new Etat()};
        private double prochain;
        // VOIES : jusqu'à voiesSol marcheurs et voiesCiel volants ensemble ; un mécène n'est jamais deux fois à l'écran
        private final double[] libreSol = new double[2], libreCiel = new double[2], enScene = new double[4];
        private final double[] cumul = new double[4];
        private int dernier;

        public Programme(Choreo c, Lieu lieu, int graine) {
            this.c = c; this.lieu = lieu; this.h = new Hasard(graine);
            this.prochain = h.entre(c.premier0, c.premier1);
        }

        /** Les mécènes prêts (planche décodée), triés par rang ; pris en compte au prochain passage tiré. */
        public void fixer(List<Mecene> liste) {
            mecenes.clear();
            mecenes.addAll(liste);
            mecenes.sort((a, b) -> Integer.compare(a.rang, b.rang));
        }

        public Lieu lieu() { return lieu; }

        /** Le suivant : le plus en retard sur sa part du temps d'écran (choreo.rangs.part), jamais le même deux fois de suite
         *  (sauf seul) ; à égalité, le meilleur rang. Les trois premiers passages sont donc 1, 2, 3. */
        private Mecene suivant(boolean seulSol, double t0) {
            int n = mecenes.size();
            double[] part = n <= 3 ? c.parts[n] : null;
            Mecene best = null; double bv = 0;
            for (int i = 0; i < n; i++) {
                Mecene m = mecenes.get(i);
                if (n > 1 && m.rang == dernier) continue;
                if (!Double.isNaN(t0) && enScene[m.rang] > t0) continue;
                if (seulSol && !m.fiche.marche()) continue;
                // v2 : un marcheur sans pause traverse quand même un lieu borné (derrière l'interface) : jamais absent d'un lieu
                if (m.fiche.marche() && solFerme(lieu)) continue;
                double v = cumul[m.rang] / (part != null ? part[i] : 1);
                if (best == null || v < bv - 1e-9) { best = m; bv = v; }
            }
            return best;
        }

        /** Un mécène est-il en retard sur sa part du temps d'écran ? (un marcheur ne prend le tour d'un volant bloqué que dans ce cas) */
        private boolean enRetard(Mecene m) {
            int n = mecenes.size();
            double[] part = n <= 3 ? c.parts[n] : null;
            double tot = 0;
            for (Mecene x : mecenes) tot += cumul[x.rang];
            if (tot <= 0) return true;
            return cumul[m.rang] / tot < (part != null ? part[mecenes.indexOf(m)] : 1.0 / n);
        }

        private double fenetreHooh(double t0, double t1) {
            double d = lieu.hoohDecalage;
            if (!lieu.hooh) return -1;
            t0 += d; t1 += d;
            int k0 = (int) StrictMath.floor((t0 - c.hoohDebut) / c.hoohPeriode);
            for (int k = Math.max(0, k0); k <= k0 + 1; k++) {
                double a = c.hoohDebut + k * c.hoohPeriode, b = a + c.hoohDuree;
                if (t1 > a && t0 < b) return b - d;
            }
            return -1;
        }

        /** La voie libre la plus tôt d'un couloir (à égalité : la voie 0). */
        private int voie(boolean sol) {
            double[] L = sol ? libreSol : libreCiel;
            int n = Math.max(1, Math.min(2, sol ? (lieu.voiesSol > 0 ? lieu.voiesSol : c.voiesSol) : c.voiesCiel)), v = 0;
            for (int i = 1; i < n; i++) if (L[i] < L[v] - 1e-9) v = i;
            return v;
        }

        private void planifier(double t) {
            while (!mecenes.isEmpty() && prochain <= t + c.avance) {
                // jamais un passage commencé dans le passé (planches prêtes tard, horloge reprise) : il entrerait au milieu
                double t0 = Math.max(prochain, t);
                Mecene m = suivant(false, t0);
                if (m == null) {                                             // tous à l'écran, ou pré fermé : au plus tôt après
                    double f = t + c.ecart0;
                    for (Mecene x : mecenes) if (enScene[x.rang] > t0) f = Math.min(f, enScene[x.rang]);
                    prochain = Math.max(t + 0.5, f);
                    break;
                }
                boolean sol = m.fiche.marche();
                int vs = voie(true), vc = voie(false);
                Passage p = sol ? passageSol(c, lieu, m, Math.max(t0, libreSol[vs]), h, vs)
                        : passageCiel(c, lieu, m, Math.max(t0, libreCiel[vc]), h, vc);
                if (!sol) {
                    double f = fenetreHooh(p.debut, p.fin);
                    if (f >= 0) {   // Ho-Oh traverse le ciel : un marcheur prend le tour s'il y en a un, sinon le volant attend
                        Mecene w = suivant(true, t0);
                        if (w != null && enRetard(w)) { m = w; sol = true; p = passageSol(c, lieu, m, Math.max(t0, libreSol[vs]), h, vs); }
                        else p = passageCiel(c, lieu, m, f + 2, h, vc);
                    }
                }
                passages.add(p);
                cumul[m.rang] += p.fin - p.debut; dernier = m.rang; enScene[m.rang] = p.fin;
                if (sol) libreSol[vs] = p.fin + c.ecart0; else libreCiel[vc] = p.fin + c.ecart0;
                // le suivant entre peu après (plusieurs mécènes ensemble), sinon après une absence
                Mecene autre = suivant(false, p.debut + c.decalage1);
                // (dé raté : il entre quand même pendant ce passage, plus tard : un long passage ne reste jamais seul jusqu'au bout)
                if (autre != null) { double k = h.suivant() < c.simultane ? 1 : 3; prochain = p.debut + h.entre(c.decalage0, c.decalage1) * k; }
                else prochain = p.fin + Math.min(c.ecart1, Math.max(c.ecart0, (p.fin - p.debut) * c.repos * h.entre(0.8, 1.2)));
            }
            while (!passages.isEmpty() && passages.get(0).fin < t - 1) passages.remove(0);
        }

        /** Les Pokémon visibles au temps t (secondes) : écrits dans la réserve, rend leur nombre (au plus 6). */
        public int etat(double t, Etat[] sortie) {
            planifier(t);
            int n = 0;
            for (Passage p : passages) {
                if (n >= sortie.length) break;
                if (etatPassage(c, lieu, p, t, sortie[n])) n++;
            }
            // ordre de peinture : la voie du fond d'abord (marcheurs : voie 1 puis 0), puis le ciel ; stable, sans allocation
            for (int i = 1; i < n; i++) {
                Etat x = sortie[i];
                int j = i - 1;
                while (j >= 0 && cle(sortie[j]) > cle(x)) { sortie[j + 1] = sortie[j]; j--; }
                sortie[j + 1] = x;
            }
            return n;
        }

        private static int cle(Etat e) { return e.sol ? 1 - e.voie : 2 + (1 - e.voie); }

        public Etat[] reserve() { return reserve; }
    }

    /** Lecture commode d'un tableau JSON de mécènes (ex. l'entrée de la référence). */
    public static List<Mecene> mecenes(JsonArray a) {
        List<Mecene> l = new ArrayList<>();
        for (JsonElement e : a) {
            JsonObject o = e.getAsJsonObject();
            l.add(new Mecene(o.get("rang").getAsInt(), o.get("pseudo").getAsString(), Fiche.lire(o.getAsJsonObject("fiche")), null));
        }
        return l;
    }
}
