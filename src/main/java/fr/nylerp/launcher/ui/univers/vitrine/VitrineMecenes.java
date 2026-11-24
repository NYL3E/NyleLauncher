package fr.nylerp.launcher.ui.univers.vitrine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.nylerp.launcher.config.AppPaths;
import fr.nylerp.launcher.config.Settings;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * LA VITRINE DES MÉCÈNES dans le fond du launcher (v2) : chacun des trois premiers mécènes du mois se promène avec le Pokémon
 * qu'il a choisi, AVEC SA PEAU (monté dessus si l'espèce se monte en jeu, à pied à côté sinon) ; marcheurs DERRIÈRE les
 * coffres-Poké Balls, volants dans le ciel, pseudo au-dessus, sous l'interface. Survol d'un duo : une carte (« 1er mécène de
 * novembre », pseudo, Pokémon, pourquoi il est là, lien) et le duo ralentit ; clic : pokerayou.academy/dons dans le navigateur.
 *
 * <p>Même loi que le site et l'écran titre ({@link LoiVitrine}, chorégraphie {@code /vitrine/choregraphie.json}), mêmes
 * planches (PNG pré-rendues depuis les vrais modèles Cobblemon). Posée dans l'univers juste au-dessus du fond, avec sa
 * parallaxe ; ses coordonnées sont celles du fond (1180 × 700, agrandi de {@code zoomFond} et calé en bas à gauche).
 *
 * <p>Rien ne bouge en « animations réduites », en pause du ciel ou quand le ciel s'éteint faute de fluidité ; rien ne
 * s'affiche sans réponse du site ; un mécène n'entre qu'une fois sa planche décodée. Aucune allocation par image dans le
 * dessin (états pris dans une réserve, étiquettes rendues une fois en image).
 */
public final class VitrineMecenes extends Canvas {

    private static final String POLICE = "/fonts/site/Kanit-SemiBold.ttf";
    private final double echelle, zoomFond;
    private final GraphicsContext gc;
    private final LoiVitrine.Choreo choreo;
    private final JsonObject choreoBrut;
    private LoiVitrine.Programme programme;
    private final List<LoiVitrine.Mecene> prets = new ArrayList<>(3);
    private final Map<String, Image> etiquettes = new HashMap<>();
    private Image avantPlan;
    private double[] boiteAvant;
    private String familleKanit = "System";
    private final AnimationTimer minuterie;
    private boolean actif, eteinte;
    private long origine = -1;
    private double temps;
    private double etiquetteMinPx = 15;
    /** L'horloge du ciel (Ho-Oh) : la loi la suit pour qu'aucun volant ne traverse le ciel en même temps que Ho-Oh. */
    private java.util.function.DoubleSupplier horlogeCiel;
    private final double ombreLargeur, ombreHauteur, ombreDecalage;
    private final RadialGradient ombre;
    // ── survol (v2) ──
    public static final String LIEN = "https://pokerayou.academy/dons/";
    private final Map<LoiVitrine.Mecene, DonneesMecenes.Entree> infos = new java.util.IdentityHashMap<>();
    private final LoiVitrine.Mecene[] vusM = new LoiVitrine.Mecene[4];
    private final double[][] vusB = new double[4][4];
    private int nVus;
    private double vitesse = 1, vCible = 1;
    private long precedent = -1;
    private Image police;          // ascii.png de Minecraft (nametag du jeu)
    private int[] avances;
    private LoiVitrine.Mecene vise;
    private final javafx.scene.layout.VBox carte = new javafx.scene.layout.VBox(2);
    private final javafx.scene.control.Label cRang = new javafx.scene.control.Label(), cPseudo = new javafx.scene.control.Label(),
            cPk = new javafx.scene.control.Label(), cPhrase = new javafx.scene.control.Label(), cLien = new javafx.scene.control.Label();
    private java.util.function.Consumer<String> ouvreur = u -> { };

    /** @param echelle échelle de la fenêtre (1 = 1180 × 700) ; @param zoomFond agrandissement du fond (calé en bas à gauche) */
    public VitrineMecenes(double echelle, double zoomFond) {
        super(1180 * echelle, 700 * echelle);
        this.echelle = echelle;
        this.zoomFond = zoomFond;
        this.gc = getGraphicsContext2D();
        setMouseTransparent(true);
        gc.setImageSmoothing(true);
        JsonObject c;
        try (InputStream in = VitrineMecenes.class.getResourceAsStream("/vitrine/choregraphie.json")) {
            c = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            c = null;
        }
        this.choreoBrut = c;
        this.choreo = c == null ? null : LoiVitrine.Choreo.lire(c);
        JsonObject O = c == null ? null : c.getAsJsonObject("sol").getAsJsonObject("ombre");
        ombreLargeur = O == null ? 0.55 : O.get("largeur").getAsDouble();
        ombreHauteur = O == null ? 0.09 : O.get("hauteur").getAsDouble();
        ombreDecalage = O != null && O.has("decalage") ? O.get("decalage").getAsDouble() : 0;
        double oa = O == null ? 0.16 : O.get("alpha").getAsDouble();
        ombre = new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(18, 32, 8, oa)), new Stop(0.55, Color.rgb(18, 32, 8, oa * 0.55)), new Stop(1, Color.rgb(18, 32, 8, 0)));
        try (InputStream in = VitrineMecenes.class.getResourceAsStream(POLICE)) {
            Font f = in == null ? null : Font.loadFont(in, 14);
            if (f != null) familleKanit = f.getFamily();
        } catch (Exception ignore) { }
        construireCarte();
        minuterie = new AnimationTimer() {
            @Override public void handle(long maintenant) { image(maintenant); }
        };
        if (choreo != null) {
            JsonObject l = choreo.brut.getAsJsonObject("lieux");
            LoiVitrine.Lieu lieu = LoiVitrine.Lieu.lire(l.getAsJsonObject("launcher"), LoiVitrine.Lieu.lire(l.getAsJsonObject("defaut"), null));
            lieu.x0 = 0;
            lieu.x1 = 1180 / zoomFond;                                   // le fond agrandi ne montre que la gauche du repère
            JsonObject lj = l.getAsJsonObject("launcher");
            etiquetteMinPx = lj != null && lj.has("etiquette_min_px") ? lj.get("etiquette_min_px").getAsDouble() : 15;
            programme = new LoiVitrine.Programme(choreo, lieu, new Random().nextInt());
            lire();
            // fraîcheur : toutes les 10 min ; le passage en cours finit avec l'ancienne donnée, le suivant prend la nouvelle
            javafx.animation.Timeline t = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.minutes(10), e -> lire()));
            t.setCycleCount(javafx.animation.Animation.INDEFINITE);
            t.play();
        }
    }

    private void lire() {
        if (eteinte) return;
        DonneesMecenes.charger(AppPaths.rootDir().resolve("vitrine")).thenAccept(r -> { if (r != null) Platform.runLater(() -> appliquer(r)); });
    }

    /** Une réponse fraîche : la liste des mécènes est remplacée ; une planche déjà décodée est reprise telle quelle. */
    private void appliquer(DonneesMecenes.Resultat r) {
        boiteAvant = r.boiteAvantPlan();
        if (police == null && r.police() != null) {
            Image pi = new Image(r.police().toUri().toString(), false);
            if (!pi.isError()) { police = pi; etiquettes.clear(); }
        }
        choreo.echelle = r.echelle();                                   // la taille des Pokémon, réglée par le site
        List<LoiVitrine.Mecene> anciens = new ArrayList<>(prets);
        prets.clear();
        programme.fixer(prets);
        Runnable suite = () -> { for (DonneesMecenes.Entree e : r.mecenes()) charger(e, anciens); };
        if (avantPlan != null) suite.run();
        else {
            Image ap = new Image(r.avantPlan().toUri().toString(), 0, 0, true, true, true);
            quandPrete(ap, () -> { avantPlan = ap; suite.run(); });
        }
    }

    private void charger(DonneesMecenes.Entree e, List<LoiVitrine.Mecene> anciens) {
        // la planche est déjà COMPOSÉE avec la peau (fil de fond, DonneesMecenes) : rien à attendre
        LoiVitrine.Mecene m = new LoiVitrine.Mecene(e.rang(), e.pseudo(), e.fiche(), e.image());
        infos.put(m, e);
        ajouter(m);
    }

    private void ajouter(LoiVitrine.Mecene m) {
        prets.removeIf(x -> { if (x.rang == m.rang && x != m) { infos.remove(x); return true; } return false; });
        prets.add(m);
        programme.fixer(prets);
    }

    private static void quandPrete(Image im, Runnable apres) {
        Runnable v = () -> { if (!im.isError()) apres.run(); };
        if (im.getProgress() >= 1) Platform.runLater(v);
        else im.progressProperty().addListener((o, a, b) -> { if (b.doubleValue() >= 1) Platform.runLater(v); });
    }

    // ── cycle de vie (suit le ciel) ─────────────────────────────────────────────────────────────────

    public void demarrer() {
        if (actif || eteinte || programme == null) return;
        if (Settings.get().animationsReduites) { effacer(); return; }
        actif = true;
        origine = -1;
        minuterie.start();
    }

    public void arreter() {
        if (!actif) return;
        actif = false;
        precedent = -1;
        cacherCarte();
        minuterie.stop();
    }

    /** Le ciel s'est éteint faute de fluidité : la vitrine aussi, pour la session. */
    public void eteindre() { eteinte = true; arreter(); effacer(); }

    private void effacer() { gc.clearRect(0, 0, getWidth(), getHeight()); }

    private void image(long maintenant) {
        // l'horloge de la vitrine : celle de l'écran, ralentie en douceur tant que le pointeur est sur un duo
        if (precedent < 0) precedent = maintenant;
        double dt = Math.max(0, Math.min(0.1, (maintenant - precedent) / 1e9));
        precedent = maintenant;
        vitesse += (vCible - vitesse) * Math.min(1, dt * 10);            // survol : arrêt en ~0,3 s
        if (Math.abs(vitesse - vCible) < 0.01) vitesse = vCible;
        temps += dt * vitesse;
        if (horlogeCiel != null && programme != null) programme.lieu().hoohDecalage = horlogeCiel.getAsDouble() - temps;
        dessiner(temps);
    }

    /** Branche l'horloge du ciel animé (celle de son Ho-Oh). */
    public void horlogeCiel(java.util.function.DoubleSupplier h) { this.horlogeCiel = h; }

    /** Pour les captures : dessine l'instant {@code t} sans minuterie. */
    public void dessinerA(double t) { temps = t; dessiner(t); }

    private void dessiner(double t) {
        effacer();
        if (programme == null) return;
        double k = echelle * zoomFond, oy = getHeight() - 700 * k;
        // l'étiquette ne descend jamais sous etiquette_min_px pixels (police) ; la loi en tient compte (bords, zones interdites)
        double ke = Math.max(1, etiquetteMinPx / (choreo.etiquetteTaille * k));
        programme.lieu().etiquetteEchelle = ke;
        LoiVitrine.Etat[] r = programme.reserve();
        int n = programme.etat(t, r);
        nVus = 0;
        if (n == 0) { if (vise != null) cacherCarte(); return; }
        boolean marcheur = false;
        for (int i = 0; i < n; i++) {
            LoiVitrine.Etat e = r[i];
            LoiVitrine.Fiche F = e.mecene.fiche;
            Image im = (Image) e.mecene.image;
            double X = e.x * k, Y = oy + e.y * k, s = e.echelle * k;
            // arrêté (survol) : il joue sur place, au temps réel, son repos ou son vol stationnaire (sinon sa boucle principale)
            if (vitesse < 0.15) {
                LoiVitrine.Clip c = F.pauseValide && F.pause != null ? F.pause : F.principal;
                double tr = System.nanoTime() / 1e9;
                e.image = c.debut() + Math.min(c.n() - 1, (int) ((((tr % c.duree()) + c.duree()) % c.duree()) / c.duree() * c.n()));
            }
            int col = e.image % F.colonnes, lig = e.image / F.colonnes;
            if (e.sol) {
                marcheur = true;
                // OMBRE PORTÉE (v2) : de la largeur réelle de l'image (boîte opaque), au pied, sur la pente, à l'opposé du soleil
                DonneesMecenes.Entree en0 = infos.get(e.mecene);
                int[] b0 = en0 == null || en0.boites() == null || e.image >= en0.boites().length ? null : en0.boites()[e.image];
                double lw = b0 != null ? (b0[2] - b0[0]) * s : F.hauteurAffichee * k * 0.8, cx0 = b0 != null ? ((b0[0] + b0[2]) / 2.0 - F.ancreX) * s * e.dir : 0;
                double rx = lw * ombreLargeur * 0.5, ry = Math.max(2, rx * ombreHauteur), ox0 = X + cx0 + rx * ombreDecalage;
                gc.save();
                gc.translate(ox0, Y);
                if (e.angle != 0) gc.rotate(e.angle);
                gc.setFill(ombre);                                         // dégradé proportionnel : épouse l'ovale, créé une fois
                gc.fillOval(-rx, -ry, rx * 2, ry * 2);
                gc.restore();
            }
            gc.save();
            gc.translate(X, Y);
            if (e.angle != 0) gc.rotate(e.angle);
            gc.scale(e.dir * s, s);
            gc.drawImage(im, col * F.largeurCellule, lig * F.hauteurCellule, F.largeurCellule, F.hauteurCellule,
                    -F.ancreX, -F.ancreY, F.largeurCellule, F.hauteurCellule);
            gc.restore();
            // boîte réelle (pixels opaques) de cette image : la zone de survol, sans allocation
            DonneesMecenes.Entree inf = infos.get(e.mecene);
            int[] bb = inf == null || inf.boites() == null || e.image >= inf.boites().length ? null : inf.boites()[e.image];
            if (bb != null && nVus < vusM.length) {
                double xa = (bb[0] - F.ancreX) * s * e.dir, xb = (bb[2] - F.ancreX) * s * e.dir;
                vusM[nVus] = e.mecene;
                double[] v = vusB[nVus++];
                v[0] = X + Math.min(xa, xb); v[1] = Y + (bb[1] - F.ancreY) * s; v[2] = X + Math.max(xa, xb); v[3] = Y + (bb[3] - F.ancreY) * s;
            }
        }
        if (marcheur && avantPlan != null) gc.drawImage(avantPlan, boiteAvant[0] * k, oy + boiteAvant[1] * k, boiteAvant[2] * k, boiteAvant[3] * k);
        for (int i = 0; i < n; i++) {
            LoiVitrine.Etat e = r[i];
            if (e.etiquetteOpacite <= 0.003) continue;
            Image et = etiquette(e.mecene, k * ke);
            gc.setGlobalAlpha(e.etiquetteOpacite);
            gc.drawImage(et, e.etiquetteX * k - et.getWidth() / 2, oy + e.etiquetteY * k - et.getHeight());
            gc.setGlobalAlpha(1);
        }
    }

    // ── la carte du survol ───────────────────────────────────────────────────────────────────────────


    /** Ce qu'ouvre un clic sur un duo (le navigateur du système ; remplaçable pour les essais). */
    public void ouvreur(java.util.function.Consumer<String> o) { this.ouvreur = o; }

    /** La carte (à poser par l'univers AU-DESSUS de l'interface ; non gérée : placée par la vitrine). */
    public javafx.scene.Node carte() { return carte; }

    private void construireCarte() {
        carte.setManaged(false);
        carte.setVisible(false);
        carte.setMouseTransparent(true);
        carte.setPadding(new javafx.geometry.Insets(12 * echelle, 14 * echelle, 12 * echelle, 14 * echelle));
        carte.setMaxWidth(290 * echelle);
        // la carte des mécènes (même DA que le site et le jeu) : fond nuit, liseré or fin, coins arrondis, Kanit ; petit titre
        // doré « MÉCÈNE DU MOIS » + couronne du rang, pseudo en grand, son Pokémon, filet or, une ligne sobre
        carte.setStyle("-fx-background-color: linear-gradient(to bottom, rgba(22,26,38,0.96), rgba(13,16,24,0.96)); -fx-background-radius: " + 12 * echelle + ";"
                + " -fx-border-color: rgba(242,193,78,0.55); -fx-border-width: 1; -fx-border-radius: " + 12 * echelle + ";"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.38), " + 24 * echelle + ", 0, 0, " + 10 * echelle + ");");
        Object[][] l = {{cRang, 11.0, "#f2c14e"}, {cPseudo, 21.0, "#ffffff"}, {cPk, 13.5, "rgba(255,255,255,0.72)"}, {cPhrase, 12.5, "rgba(255,255,255,0.82)"}, {cLien, 11.0, "#f2c14e"}};
        for (Object[] x : l) {
            javafx.scene.control.Label lb = (javafx.scene.control.Label) x[0];
            lb.setFont(Font.font(familleKanit, (double) x[1] * echelle));
            lb.setTextFill(Color.web((String) x[2]));
            lb.setWrapText(true);
            lb.setMaxWidth(262 * echelle);
        }
        cPhrase.setText("Ses dons font vivre le serveur.");
        couronne.setContent("M1 19V6l6 6 6-11 6 11 6-6v13Z");
        couronne.setScaleX(0.6 * echelle); couronne.setScaleY(0.6 * echelle);
        javafx.scene.layout.Region pousse = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(pousse, javafx.scene.layout.Priority.ALWAYS);
        cRang.setText("MÉCÈNE DU MOIS");
        javafx.scene.layout.HBox titre = new javafx.scene.layout.HBox(5 * echelle, new javafx.scene.Group(couronne), cRang, pousse, cLien);
        titre.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        javafx.scene.layout.Region filet = new javafx.scene.layout.Region();
        filet.setMinHeight(1); filet.setPrefHeight(1); filet.setMaxHeight(1);
        filet.setStyle("-fx-background-color: linear-gradient(to right, rgba(242,193,78,0.5), rgba(242,193,78,0));");
        javafx.scene.layout.VBox.setMargin(filet, new javafx.geometry.Insets(8 * echelle, 0, 6 * echelle, 0));
        VBoxMarge(titre, 2);
        cPhrase.setStyle("-fx-font-style: italic;");
        carte.getChildren().addAll(titre, cPseudo, cPk, filet, cPhrase);
        // apparition courte (fondu + légère montée)
        apparition.setFromValue(0); apparition.setToValue(1);
        montee.setFromY(6 * echelle); montee.setToY(0);
    }

    private final javafx.scene.shape.SVGPath couronne = new javafx.scene.shape.SVGPath();
    private final javafx.animation.FadeTransition apparition = new javafx.animation.FadeTransition(javafx.util.Duration.millis(140), carte);
    private final javafx.animation.TranslateTransition montee = new javafx.animation.TranslateTransition(javafx.util.Duration.millis(180), carte);

    private void VBoxMarge(javafx.scene.Node n, double bas) { javafx.scene.layout.VBox.setMargin(n, new javafx.geometry.Insets(0, 0, bas * echelle, 0)); }

    private int toucher(double x, double y) {
        for (int i = nVus - 1; i >= 0; i--) { double[] b = vusB[i]; if (x >= b[0] && x <= b[2] && y >= b[1] && y <= b[3]) return i; }
        return -1;
    }

    /**
     * Le pointeur bouge sur l'univers ({@code x}, {@code y} : coordonnées de la vitrine, translation de parallaxe déjà retirée) ;
     * {@code sx}, {@code sy} : coordonnées dans l'univers, pour placer la carte. Rend vrai si un duo est sous le pointeur.
     */
    public boolean survoler(double x, double y, double sx, double sy, double largeurUnivers, double hauteurUnivers) {
        int i = actif ? toucher(x, y) : -1;
        if (i < 0) { cacherCarte(); return false; }
        LoiVitrine.Mecene m = vusM[i];
        if (m != vise) {
            vise = m;
            DonneesMecenes.Entree e = infos.get(m);
            Color metal = Color.web(m.rang == 1 ? "#f2c14e" : m.rang == 2 ? "#cfd6df" : "#d99a63");
            couronne.setFill(metal);
            cLien.setText(m.rang == 1 ? "1er" : m.rang + "e");
            cLien.setTextFill(metal);
            cPseudo.setText(m.pseudo);
            cPk.setText(e == null || "seul".equals(e.mode()) ? "" : ("monte".equals(e.mode()) ? "sur " : "avec ") + e.nom() + (e.shiny() ? " chromatique" : ""));
            cPk.setManaged(!cPk.getText().isEmpty()); cPk.setVisible(!cPk.getText().isEmpty());
            carte.setVisible(true);
            carte.applyCss();
            carte.autosize();
            apparition.playFromStart(); montee.playFromStart();
        }
        vCible = 0;
        double[] b = vusB[i];
        double w = carte.getWidth() > 0 ? carte.getWidth() : 260 * echelle, h = carte.getHeight() > 0 ? carte.getHeight() : 130 * echelle;
        double dx = sx - x, dy = sy - y;                                 // passage vitrine -> univers (parallaxe)
        // au-dessus du duo (sans le masquer), sinon à côté, sinon dessous ; jamais hors du cadre
        double cx = (b[0] + b[2]) / 2 + dx, top = b[1] + dy - h - 12 * echelle;
        if (top < 8 * echelle) {
            boolean droite = b[2] + dx + 12 * echelle + w < largeurUnivers - 8 * echelle;
            cx = droite ? b[2] + dx + 12 * echelle + w / 2 : b[0] + dx - 12 * echelle - w / 2;
            top = (b[1] + b[3]) / 2 + dy - h / 2;
            if (cx - w / 2 < 8 * echelle || cx + w / 2 > largeurUnivers - 8 * echelle) { cx = (b[0] + b[2]) / 2 + dx; top = b[3] + dy + 12 * echelle; }
        }
        carte.relocate(Math.max(8 * echelle, Math.min(largeurUnivers - w - 8 * echelle, cx - w / 2)), Math.max(8 * echelle, Math.min(hauteurUnivers - h - 8 * echelle, top)));
        return true;
    }

    /** Clic sur l'univers : sur un duo, ouvre pokerayou.academy/dons dans le navigateur (rend vrai). */
    public boolean cliquer(double x, double y) {
        if (!actif || toucher(x, y) < 0) return false;
        ouvreur.accept(LIEN);
        return true;
    }

    public void cacherCarte() {
        vise = null;
        vCible = 1;
        carte.setVisible(false);
    }

    /**
     * LE NAMETAG DU JEU (owner 24/11) : police bitmap de Minecraft (ascii.png), texte blanc et son ombre portée (couleur / 4),
     * fond noir à 25 % ; agrandi par l'entier {@code n} (pixels nets, écrits directement). Avance d'un glyphe = sa colonne opaque
     * la plus à droite + 2, espace = 4 : comme le jeu et comme vitrine-web.js.
     */
    private Image nametag(String s, int n) {
        javafx.scene.image.PixelReader pr = police.getPixelReader();
        int W = (int) police.getWidth(), g = W / 16;
        if (avances == null) {
            avances = new int[256];
            for (int c = 0; c < 256; c++) {
                int ox = (c % 16) * g, oy = (c / 16) * g, r = -1;
                for (int x = g - 1; x >= 0 && r < 0; x--) for (int y = 0; y < g; y++) if (((pr.getArgb(ox + x, oy + y) >>> 24) & 0xff) > 0) { r = x; break; }
                avances[c] = c == 32 ? 4 : (r < 0 ? 0 : (int) Math.round((r + 1) * 8.0 / g) + 1);
            }
        }
        int tw = 0;
        for (char ch : s.toCharArray()) tw += avances[ch & 255];
        tw = Math.max(1, tw - 1);
        int L = tw + 3, H = 11;
        int[] px = new int[L * H];
        for (int y = 0; y < 10; y++) for (int x = 0; x < tw + 2; x++) px[y * L + x] = 0x40000000;
        for (int pass = 0; pass < 2; pass++) {
            int dx = pass == 0 ? 2 : 1, coul = pass == 0 ? 0xFF3F3F3F : 0xFFFFFFFF, cx = dx;
            for (char ch : s.toCharArray()) {
                int c = ch & 255, ox = (c % 16) * g, oy = (c / 16) * g;
                for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                    if (((pr.getArgb(ox + x * g / 8, oy + y * g / 8) >>> 24) & 0xff) == 0) continue;
                    int X = cx + x, Y = dx + y;
                    if (X < L && Y < H) px[Y * L + X] = coul;
                }
                cx += avances[c];
            }
        }
        WritableImage im = new WritableImage(L * n, H * n);
        javafx.scene.image.PixelWriter pw = im.getPixelWriter();
        for (int y = 0; y < H * n; y++) for (int x = 0; x < L * n; x++) pw.setArgb(x, y, px[(y / n) * L + x / n]);
        return im;
    }

    /** L'étiquette du mécène, repli sans la police du jeu (plaque, couronne or / argent / bronze, pseudo en Kanit). */
    private Image etiquette(LoiVitrine.Mecene m, double k) {
        String cle = m.rang + "|" + m.pseudo + "|" + Math.round(k * 100);
        Image deja = etiquettes.get(cle);
        if (deja != null) return deja;
        if (police != null) {
            Image im = nametag(m.pseudo, Math.max(1, (int) Math.round(choreoBrut.getAsJsonObject("etiquette").get("taille").getAsDouble() * k / 9)));
            if (etiquettes.size() > 12) etiquettes.clear();
            etiquettes.put(cle, im);
            return im;
        }
        JsonObject E = choreoBrut.getAsJsonObject("etiquette");
        double fs = E.get("taille").getAsDouble() * k, mx = E.getAsJsonArray("marge").get(0).getAsDouble() * k, my = E.getAsJsonArray("marge").get(1).getAsDouble() * k;
        double hc = fs * 0.62, ec = fs * 0.32, wc = hc * 1.3;
        Font f = Font.font(familleKanit, fs);
        Text mesure = new Text(m.pseudo);
        mesure.setFont(f);
        mesure.setBoundsType(TextBoundsType.LOGICAL);
        double lt = mesure.getLayoutBounds().getWidth();
        double w = Math.ceil(mx * 2 + wc + ec + lt) + 2, h = Math.ceil(fs * 1.18 + my * 2) + 2;
        Canvas c = new Canvas(w, h);
        GraphicsContext g = c.getGraphicsContext2D();
        var p = E.getAsJsonArray("plaque");
        g.setFill(Color.rgb(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt(), p.get(3).getAsDouble()));
        g.fillRect(1, 1, w - 2, h - 2);
        var col = E.getAsJsonObject("couronne").getAsJsonArray(String.valueOf(Math.min(3, Math.max(1, m.rang))));
        double x = 1 + mx, y = (h - hc) / 2 - fs * 0.04;
        g.setFill(new LinearGradient(0, y, 0, y + hc, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web(col.get(0).getAsString())), new Stop(1, Color.web(col.get(1).getAsString()))));
        g.fillPolygon(new double[]{x, x, x + wc * 0.27, x + wc * 0.5, x + wc * 0.73, x + wc, x + wc},
                new double[]{y + hc, y + hc * 0.28, y + hc * 0.58, y, y + hc * 0.58, y + hc * 0.28, y + hc}, 7);
        g.setFont(f);
        g.setTextBaseline(javafx.geometry.VPos.CENTER);
        double tx = 1 + mx + wc + ec, ty = h / 2 + fs * 0.04;
        g.setFill(Color.rgb(0, 0, 0, 0.45));
        g.fillText(m.pseudo, tx, ty + Math.max(1, fs * 0.08));
        g.setFill(Color.WHITE);
        g.fillText(m.pseudo, tx, ty);
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        WritableImage im = c.snapshot(sp, null);
        if (etiquettes.size() > 12) etiquettes.clear();
        etiquettes.put(cle, im);
        return im;
    }
}
