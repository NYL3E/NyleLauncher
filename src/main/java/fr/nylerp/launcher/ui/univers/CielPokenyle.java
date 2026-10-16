package fr.nylerp.launcher.ui.univers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.ui.kit.CourbeBezier;
import fr.nylerp.launcher.update.CheminsMedias;
import fr.nylerp.launcher.update.Medias;
import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.transform.Affine;
import javafx.stage.Screen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * LE CIEL DE POKÉNYLE — les nuées et le Ho-Oh du haut de page du site, rejoués image par image.
 *
 * <p>Tout vient de {@code pokenyle/trajets.json}, relevé par l'agent du site sur les feuilles de style
 * réelles de pokerayou.academy : groupes (loin, horde, Ho-Oh), keyframes de traversée, position et
 * taille de chaque oiseau, battement d'ailes (atlas PNG parcourus par crans), vol plané (± 0,25 rem,
 * ease-in-out, aller-retour), plumes de Ho-Oh. Les coordonnées sont en pixels 1x de la fenêtre
 * 1180 × 700 ; le canevas applique l'échelle de la fenêtre une fois pour toutes.
 *
 * <p>Ho-Oh est rare comme sur le site : il traverse 8 s après le premier affichage, puis toutes les
 * 150 s, et ses plumes ne tombent que pendant les 45 s qui suivent chaque début de cycle.
 *
 * <p>Repli : si moins de 40 images/s sont mesurées pendant les premières secondes, ou si le joueur a
 * demandé « animations réduites », le ciel s'éteint et l'écran garde le fond fixe ({@link #repliProperty()}).
 */
public final class CielPokenyle extends Canvas {

    // ── Données du site ──────────────────────────────────────────────────────────────────────────
    private record Atlas(Image image, int n, int colonnes, double lf, double hf) {}
    private record Oiseau(String atlas, double x, double y, double w, double h, double boucle, double delaiAiles,
                          double dureePlane, double delaiPlane) {}
    private record Cle(double t, double tx, double ty, double echelle, double rotation) {}
    private record Groupe(String nom, double ox, double oy, double duree, double delai, boolean avantDebut,
                          List<Cle> cles, List<Oiseau> oiseaux) {}
    private record Plume(int couleur, double dx, double dy, double tour, double echelle, double r0, double decalageY,
                         double duree, double delai) {}
    private record Eclat(double x, double y, double taille, double periode, double phase, double sens) {}

    private static final Interpolator PLANE = new CourbeBezier(0.42, 0, 0.58, 1);     // ease-in-out
    private static final Interpolator CHUTE = new CourbeBezier(0.3, 0.2, 0.5, 1);     // plumes du site
    private static final double SEUIL_IPS = 40;

    private final double echelleFenetre;
    private final GraphicsContext gcFond;
    /** Ho-Oh passe DEVANT le titre (un légendaire qui traverse l'écran) : il a son propre canevas, posé au-dessus
     *  du texte par l'univers ; il ne croise jamais le bouton (trajectoire relevée sur le site). */
    private final Canvas avant;
    private final GraphicsContext gcAvant;
    private GraphicsContext gc;
    private final Map<String, Atlas> atlas = new HashMap<>();
    private final List<Groupe> groupes = new ArrayList<>();
    private final List<Plume> plumes = new ArrayList<>();
    private final List<Eclat> eclats = new ArrayList<>();
    private final Image[] imagesPlumes = new Image[3];
    private Image imageEclat;
    private double remPx = 20.6136, cielX = -92.87, cielY = -61.46, cielL = 1546.02, cielH = 526.26;
    private double plumeCx = 24.74, plumeCy = 90.7, plumeL = 14.43, plumeH = 43.29;
    private boolean trajetsCharges;

    private final DoubleProperty parallaxeX = new SimpleDoubleProperty(0);
    private final DoubleProperty parallaxeY = new SimpleDoubleProperty(0);
    private double cibleX, cibleY;
    private final BooleanProperty repli = new SimpleBooleanProperty(false);

    private final AnimationTimer minuterie;
    private boolean actif;
    private long origine = -1;
    private double tempsAccumule;
    private double debutMesure = -1;
    private int imagesMesurees;
    private double ipsMesurees = -1;

    /** @param echelle échelle de la fenêtre (1 = 1180 × 700) */
    public CielPokenyle(double echelle) {
        super(1180 * echelle, 700 * echelle);
        this.echelleFenetre = echelle;
        this.gcFond = getGraphicsContext2D();
        this.avant = new Canvas(1180 * echelle, 700 * echelle);
        this.avant.setMouseTransparent(true);
        this.gcAvant = avant.getGraphicsContext2D();
        this.gc = gcFond;
        setMouseTransparent(true);
        minuterie = new AnimationTimer() {
            @Override public void handle(long maintenant) { image(maintenant); }
        };
        preparerEclats();
        charger();
    }

    // ── API ──────────────────────────────────────────────────────────────────────────────────────

    /** Lance (ou reprend) l'animation. Sans effet en repli ou en « animations réduites ». */
    public void demarrer() {
        if (actif || repli.get()) return;
        if (Settings.get().animationsReduites) { repli.set(true); effacer(); return; }
        actif = true;
        origine = -1;
        minuterie.start();
    }

    /** Met l'animation en pause (fenêtre rangée, autre univers) ; le temps reprend où il s'était arrêté. */
    public void arreter() {
        if (!actif) return;
        actif = false;
        minuterie.stop();
    }

    /** Position de la souris dans la fenêtre, de −1 à 1 sur chaque axe (parallaxe, lissée). */
    public void viser(double nx, double ny) {
        cibleX = Math.max(-1, Math.min(1, nx));
        cibleY = Math.max(-1, Math.min(1, ny));
    }

    /** Le canevas de Ho-Oh, à poser au-dessus du titre (souris transparente). */
    public Canvas avant() { return avant; }

    public ReadOnlyDoubleProperty parallaxeXProperty() { return parallaxeX; }
    public ReadOnlyDoubleProperty parallaxeYProperty() { return parallaxeY; }
    public ReadOnlyBooleanProperty repliProperty() { return repli; }
    /** Images/s mesurées au démarrage (−1 tant que la mesure n'est pas faite). */
    public double ipsMesurees() { return ipsMesurees; }
    /** Temps du ciel en secondes (pour les captures de test). */
    public double temps() { return tempsAccumule; }

    /** Pour les tests : place le ciel à l'instant {@code t} et dessine une image, sans minuterie. */
    public void dessinerA(double t) {
        tempsAccumule = t;
        dessiner(t);
    }

    // ── Chargement ───────────────────────────────────────────────────────────────────────────────

    private void charger() {
        lireJson(CheminsMedias.POKENYLE_TRAJETS, this::lireTrajets);
        // Écran 1x : les atlas (jusqu'à 4080 px de large) sont décodés à mi-résolution — quatre fois
        // moins de mémoire, et rien de perdu puisque l'affichage est plus petit que la moitié.
        double sortie = 1;
        try { sortie = Screen.getPrimary().getOutputScaleX(); } catch (Throwable ignored) { }
        final double facteur = sortie >= 1.5 ? 1.0 : 0.5;
        for (String nom : CheminsMedias.POKENYLE_ATLAS) {
            lireJson(CheminsMedias.atlasJson(nom), j -> {
                JsonObject o = j.getAsJsonObject();
                int n = o.get("n").getAsInt(), col = o.get("colonnes").getAsInt(), lig = o.get("lignes").getAsInt();
                double w = o.get("w").getAsDouble(), h = o.get("h").getAsDouble();
                ImagesDistantes.charger(CheminsMedias.atlasImage(nom), null, col * w * facteur, lig * h * facteur, img -> {
                    double k = img.getWidth() / (col * w);
                    atlas.put(nom, new Atlas(img, n, col, w * k, h * k));
                });
            });
        }
        String[] chemins = {CheminsMedias.POKENYLE_PLUME_2X, CheminsMedias.POKENYLE_PLUME_ROUGE_2X, CheminsMedias.POKENYLE_PLUME_ARC_2X};
        for (int i = 0; i < chemins.length; i++) {
            final int idx = i;
            ImagesDistantes.charger(chemins[i], null, 0, 0, img -> imagesPlumes[idx] = img);
        }
        ImagesDistantes.charger(CheminsMedias.POKENYLE_ECLAT_2X, null, 0, 0, img -> imageEclat = img);
    }

    private static void lireJson(String chemin, java.util.function.Consumer<JsonElement> surJson) {
        java.util.function.Consumer<Path> lire = p -> {
            try {
                JsonElement j = JsonParser.parseString(Files.readString(p));
                Platform.runLater(() -> {
                    try { surJson.accept(j); } catch (Throwable t) { System.err.println("[Ciel] " + chemin + " : " + t); }
                });
            } catch (Throwable t) {
                System.err.println("[Ciel] lecture de " + chemin + " impossible : " + t);
            }
        };
        Path p = Medias.fichier(chemin);
        if (p != null) lire.accept(p);
        else Medias.quandDisponible(chemin, lire);
    }

    private void lireTrajets(JsonElement racine) {
        JsonObject r = racine.getAsJsonObject();
        JsonObject repere = r.getAsJsonObject("repere");
        remPx = repere.get("rem_en_px").getAsDouble();
        JsonObject ciel = r.getAsJsonObject("zone_ciel").getAsJsonObject("px");
        cielX = ciel.get("x").getAsDouble();
        cielY = ciel.get("y").getAsDouble();
        cielL = ciel.get("w").getAsDouble();
        cielH = ciel.get("h").getAsDouble();
        groupes.clear();
        for (JsonElement ge : r.getAsJsonArray("groupes")) {
            JsonObject g = ge.getAsJsonObject();
            JsonArray origine = g.getAsJsonArray("origine_transform_px");
            JsonObject anim = g.getAsJsonObject("animation");
            List<Cle> cles = new ArrayList<>();
            for (JsonElement ke : g.getAsJsonArray("keyframes")) {
                JsonObject k = ke.getAsJsonObject();
                JsonArray tp = k.getAsJsonArray("translate_px");
                cles.add(new Cle(k.get("t").getAsDouble(), tp.get(0).getAsDouble(), tp.get(1).getAsDouble(),
                        k.get("scale").getAsDouble(), k.get("rotate_deg").getAsDouble()));
            }
            List<Oiseau> oiseaux = new ArrayList<>();
            for (JsonElement oe : g.getAsJsonArray("oiseaux")) {
                JsonObject o = oe.getAsJsonObject();
                JsonObject batt = o.getAsJsonObject("battement");
                JsonObject plane = o.has("plane") && o.get("plane").isJsonObject() ? o.getAsJsonObject("plane") : null;
                double boucle = o.has("duree_boucle_atlas_s") ? o.get("duree_boucle_atlas_s").getAsDouble() : batt.get("duree_s").getAsDouble();
                oiseaux.add(new Oiseau(o.get("atlas").getAsString(),
                        o.get("x_px").getAsDouble(), o.get("y_px").getAsDouble(),
                        o.get("largeur_px").getAsDouble(), o.get("hauteur_px").getAsDouble(),
                        boucle, batt.get("delai_s").getAsDouble(),
                        plane == null ? 0 : plane.get("duree_s").getAsDouble(),
                        plane == null ? 0 : plane.get("delai_s").getAsDouble()));
            }
            String remplissage = anim.has("remplissage") ? anim.get("remplissage").getAsString() : "none";
            groupes.add(new Groupe(g.get("nom").getAsString(), origine.get(0).getAsDouble(), origine.get(1).getAsDouble(),
                    anim.get("duree_s").getAsDouble(), anim.get("delai_s").getAsDouble(),
                    remplissage.equals("both") || remplissage.equals("backwards"), cles, oiseaux));
        }
        plumes.clear();
        if (r.has("plumes")) {
            JsonObject p = r.getAsJsonObject("plumes");
            JsonObject c = p.getAsJsonObject("conteneur_px");
            JsonObject t = p.getAsJsonObject("taille_px");
            plumeCx = c.get("x").getAsDouble();
            plumeCy = c.get("y").getAsDouble();
            plumeL = t.get("w").getAsDouble();
            plumeH = t.get("h").getAsDouble();
            for (JsonElement pe : p.getAsJsonArray("liste")) {
                JsonObject o = pe.getAsJsonObject();
                String coul = o.get("couleur").getAsString();
                JsonObject a = o.getAsJsonObject("animation");
                plumes.add(new Plume(coul.equals("rouge") ? 1 : coul.equals("arc") ? 2 : 0,
                        o.get("dx_rem").getAsDouble() * remPx, o.get("dy_rem").getAsDouble() * remPx,
                        o.get("tour_deg").getAsDouble(), o.get("echelle").getAsDouble(),
                        o.get("rotation_depart_deg").getAsDouble(), o.get("decalage_y_rem").getAsDouble() * remPx,
                        a.get("duree_s").getAsDouble(), a.get("delai_s").getAsDouble()));
            }
        }
        trajetsCharges = true;
    }

    /** Éclats prismatiques : quelques étoiles qui scintillent dans le ciel (tirage fixe, toujours les mêmes). */
    private void preparerEclats() {
        Random r = new Random(1216);
        double[][] zones = {{60, 470, 70, 300}, {560, 1120, 40, 260}, {250, 700, 220, 380}};
        for (int i = 0; i < 16; i++) {
            double[] z = zones[i % zones.length];
            eclats.add(new Eclat(z[0] + r.nextDouble() * (z[1] - z[0]), z[2] + r.nextDouble() * (z[3] - z[2]),
                    9 + r.nextDouble() * 13, 2.6 + r.nextDouble() * 2.4, r.nextDouble(), r.nextBoolean() ? 1 : -1));
        }
    }

    // ── Animation ────────────────────────────────────────────────────────────────────────────────

    private void image(long maintenant) {
        if (origine < 0) origine = maintenant - (long) (tempsAccumule * 1e9);
        double t = (maintenant - origine) / 1e9;
        tempsAccumule = t;
        mesurer(t);
        parallaxeX.set(parallaxeX.get() + (cibleX - parallaxeX.get()) * 0.06);
        parallaxeY.set(parallaxeY.get() + (cibleY - parallaxeY.get()) * 0.06);
        dessiner(t);
    }

    /** Mesure des images/s entre 0,6 s et 3,6 s d'animation ; sous le seuil, le ciel s'éteint. */
    private void mesurer(double t) {
        if (ipsMesurees >= 0 || t < 0.6) return;
        if (debutMesure < 0) { debutMesure = t; imagesMesurees = 0; return; }
        imagesMesurees++;
        double duree = t - debutMesure;
        if (duree >= 3.0) {
            ipsMesurees = imagesMesurees / duree;
            System.out.printf("[Ciel] %.1f images/s mesurées%n", ipsMesurees);
            if (ipsMesurees < SEUIL_IPS) {
                arreter();
                effacer();
                repli.set(true);
            }
        }
    }

    private void effacer() {
        for (GraphicsContext g : new GraphicsContext[]{gcFond, gcAvant}) {
            g.setTransform(new Affine());
            g.clearRect(0, 0, getWidth(), getHeight());
        }
    }

    private void dessiner(double t) {
        effacer();
        if (!trajetsCharges) return;
        for (GraphicsContext cible : new GraphicsContext[]{gcFond, gcAvant}) {
            gc = cible;
            gc.save();
            gc.scale(echelleFenetre, echelleFenetre);
            gc.beginPath();
            gc.rect(cielX, cielY, cielL, cielH);
            gc.closePath();
            gc.clip();
            // parallaxe : les oiseaux glissent un peu plus que le fond (profondeur)
            gc.translate(-parallaxeX.get() * 12, -parallaxeY.get() * 7);
            for (Groupe g : groupes) {
                if ("hooh".equals(g.nom()) == (cible == gcAvant)) dessinerGroupe(g, t);
            }
            gc.restore();
        }
        gc = gcFond;
        dessinerEclats(t);
    }

    private void dessinerGroupe(Groupe g, double t) {
        double local = t - g.delai();
        double p;
        if (local < 0) {
            if (!g.avantDebut()) return;          // pas encore commencé, pas de remplissage : rien
            p = 0;
        } else {
            p = (local / g.duree()) % 1.0;
        }
        Cle c = interpoler(g.cles(), p);
        gc.save();
        gc.translate(cielX + g.ox() + c.tx(), cielY + g.oy() + c.ty());
        gc.scale(c.echelle(), c.echelle());
        gc.rotate(c.rotation());
        gc.translate(-g.ox(), -g.oy());
        for (Oiseau o : g.oiseaux()) dessinerOiseau(o, t);
        if ("hooh".equals(g.nom()) && local >= 0) dessinerPlumes(local % g.duree(), t);
        gc.restore();
    }

    private static Cle interpoler(List<Cle> cles, double p) {
        if (cles.isEmpty()) return new Cle(0, 0, 0, 1, 0);
        Cle avant = cles.get(0);
        if (p <= avant.t()) return avant;
        for (int i = 1; i < cles.size(); i++) {
            Cle apres = cles.get(i);
            if (p <= apres.t()) {
                double f = (p - avant.t()) / Math.max(1e-9, apres.t() - avant.t());
                return new Cle(p, mix(avant.tx(), apres.tx(), f), mix(avant.ty(), apres.ty(), f),
                        mix(avant.echelle(), apres.echelle(), f), mix(avant.rotation(), apres.rotation(), f));
            }
            avant = apres;
        }
        return avant;
    }

    private void dessinerOiseau(Oiseau o, double t) {
        Atlas a = atlas.get(o.atlas());
        if (a == null) return;
        int idx = (int) Math.floor(frac((t - o.delaiAiles()) / o.boucle()) * a.n()) % a.n();
        double sx = (idx % a.colonnes()) * a.lf(), sy = (idx / a.colonnes()) * a.hf();
        double dy = 0;
        if (o.dureePlane() > 0) {
            double phase = (t - o.delaiPlane()) / o.dureePlane();
            long k = (long) Math.floor(phase);
            double f = phase - k;
            if ((k & 1) == 1) f = 1 - f;           // aller-retour (alternate)
            dy = -0.25 * remPx + PLANE.interpolate(0.0, 1.0, f) * 0.5 * remPx;
        }
        gc.drawImage(a.image(), sx, sy, a.lf(), a.hf(), o.x(), o.y() + dy, o.w(), o.h());
    }

    /** Plumes du site : seulement pendant les 45 s qui suivent le début du passage de Ho-Oh. */
    private void dessinerPlumes(double tempsCycle, double t) {
        if (tempsCycle > 45 || plumes.isEmpty()) return;
        for (Plume p : plumes) {
            Image img = imagesPlumes[p.couleur()] != null ? imagesPlumes[p.couleur()] : imagesPlumes[0];
            if (img == null) continue;
            double f = frac((t - p.delai()) / p.duree());
            double opacite = f <= 0.12 ? CHUTE.interpolate(0.0, 1.0, f / 0.12)
                    : CHUTE.interpolate(1.0, 0.0, (f - 0.12) / 0.88);
            double tx, ty, rot, ech;
            if (f <= 0.45) {
                double e = CHUTE.interpolate(0.0, 1.0, f / 0.45);
                tx = mix(0, p.dx() * 0.45, e);
                ty = mix(0, p.dy() * 0.35, e);
                rot = mix(p.r0(), p.r0() + p.tour() * 0.5, e);
                ech = mix(0.7, p.echelle(), e);
            } else {
                double e = CHUTE.interpolate(0.0, 1.0, (f - 0.45) / 0.55);
                tx = mix(p.dx() * 0.45, p.dx(), e);
                ty = mix(p.dy() * 0.35, p.dy(), e);
                rot = mix(p.r0() + p.tour() * 0.5, p.r0() + p.tour(), e);
                ech = p.echelle();
            }
            gc.save();
            gc.setGlobalAlpha(Math.max(0, Math.min(1, opacite)));
            gc.translate(plumeCx + plumeL / 2 + tx, plumeCy + p.decalageY() + plumeH / 2 + ty);
            gc.rotate(rot);
            gc.scale(ech, ech);
            gc.drawImage(img, -plumeL / 2, -plumeH / 2, plumeL, plumeH);
            gc.restore();
        }
    }

    private void dessinerEclats(double t) {
        if (imageEclat == null) return;
        gc.save();
        gc.scale(echelleFenetre, echelleFenetre);
        gc.translate(-parallaxeX.get() * 16, -parallaxeY.get() * 9);
        for (Eclat e : eclats) {
            double f = frac(t / e.periode() + e.phase());
            double vie = Math.sin(f * Math.PI);                 // 0 → 1 → 0
            double taille = e.taille() * (0.45 + 0.55 * vie);
            gc.save();
            gc.setGlobalAlpha(0.85 * vie * vie);
            gc.translate(e.x(), e.y() - f * 6);
            gc.rotate(e.sens() * t * 22);
            gc.drawImage(imageEclat, -taille / 2, -taille / 2, taille, taille);
            gc.restore();
        }
        gc.restore();
    }

    private static double frac(double v) { return v - Math.floor(v); }
    private static double mix(double a, double b, double f) { return a + (b - a) * f; }
}
