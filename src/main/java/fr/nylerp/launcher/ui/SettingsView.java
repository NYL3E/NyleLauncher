package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.config.Constants;
import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.fond.DemarrageAuto;
import fr.nylerp.launcher.fond.Fond;
import fr.nylerp.launcher.ui.kit.CarteVerre;
import fr.nylerp.launcher.ui.kit.ChromeFenetre;
import fr.nylerp.launcher.ui.kit.Interrupteur;
import fr.nylerp.launcher.ui.kit.Jetons;
import fr.nylerp.launcher.ui.kit.Polices;
import fr.nylerp.launcher.ui.kit.TitreEclats;
import fr.nylerp.launcher.ui.reglages.Elements;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Paramètres du launcher, refaits dans le design du site Pokényle (refonte 2026-10, lot L6).
 *
 * <p>Même organisation que la version du 16/07 validée par l'owner : UNE page défilable, catégories à
 * gauche qui servent d'ancres (clic = défilement doux, la catégorie active suit le défilement), tout
 * reste visible en défilant. Les titres de section sont en Northura blanc au style Aniimo, posés sur
 * le ciel ; le contenu est dans des cartes de verre claires. Toutes les options d'avant sont gardées
 * avec le même comportement ; s'y ajoutent « Démarrage et notifications », « Téléchargements » et
 * « Animations ».
 *
 * <p>Point d'entrée inchangé : {@code new SettingsView(onBack)}, {@code onBack} étant appelé par le
 * bouton retour. La vue apporte ses propres feuilles ({@code kit.css} puis {@link Elements#CSS}) :
 * elle se rend correctement quelle que soit la scène qui l'accueille.
 */
public class SettingsView extends BorderPane {

    private StackPane bodyStack;                            // hôte plein-corps pour l'agrandissement et la confirmation
    private FlowPane shotGrid;                              // grille de vignettes (re-scannée à l'ouverture)
    private Button shotSortBtn;
    private boolean shotsNewestFirst = true;
    private javafx.event.EventHandler<KeyEvent> echap;

    public SettingsView(Runnable onBack) {
        Polices.charger();
        getStylesheets().add(SettingsView.class.getResource("/css/kit.css").toExternalForm());
        getStylesheets().add(SettingsView.class.getResource(Elements.CSS).toExternalForm());
        getStyleClass().add("reglages-racine");
        setTop(buildTopBar(onBack));
        setCenter(buildBody());
    }

    private static double rem(double r) { return Jetons.rem(r); }

    private Region buildTopBar(Runnable onBack) {
        Button back = new Button();
        back.getStyleClass().add("reglages-retour");
        back.setGraphic(Icons.arrowLeft(rem(0.85), Jetons.ENCRE));
        double d = Math.max(rem(2.05), 40);            // jamais sous 40 px : cible de clic confortable
        back.setMinSize(d, d);
        back.setPrefSize(d, d);
        back.setMaxSize(d, d);
        back.setPickOnBounds(true);
        back.setOnAction(e -> onBack.run());
        back.setId("reglages-retour");
        Tooltip.install(back, new Tooltip("Retour (Échap)"));
        // Échap = retour, tant qu'aucune surcouche (agrandissement, confirmation) n'est ouverte.
        sceneProperty().addListener((o, ancienne, nouvelle) -> {
            if (ancienne != null) ancienne.removeEventFilter(KeyEvent.KEY_PRESSED, echap);
            if (nouvelle != null) nouvelle.addEventFilter(KeyEvent.KEY_PRESSED, echap);
        });
        this.echap = e -> {
            if (e.getCode() != KeyCode.ESCAPE) return;
            e.consume();
            if (bodyStack != null && bodyStack.getChildren().size() > 1) {   // agrandissement / confirmation : Échap le ferme d'abord
                bodyStack.getChildren().remove(bodyStack.getChildren().size() - 1);
            } else {
                onBack.run();
            }
        };

        TitreEclats title = new TitreEclats("Paramètres", TitreEclats.Style.SECONDAIRE, rem(1.6), null);

        HBox bar = new HBox(rem(0.85), back, title);
        bar.setAlignment(Pos.CENTER_LEFT);
        // la barre de la fenêtre (poignée de déplacement) recouvre le haut de l'écran et capte les clics :
        // le bouton Retour doit commencer SOUS elle, sinon sa partie haute est morte.
        bar.setPadding(new Insets(ChromeFenetre.hauteurBarre() + rem(0.15), rem(1.4), rem(0.5), rem(1.2)));
        return bar;
    }

    /** UNE PAGE défilable + CATÉGORIES À GAUCHE : le rail est une navigation par ANCRES — cliquer une
     *  catégorie fait défiler la page en douceur jusqu'à sa section, et la catégorie active suit le
     *  défilement. Rien n'est caché derrière un onglet. MAINTENANCE puis À PROPOS en bas. */
    private Region buildBody() {
        String[] names = {"Mémoire", "Audio", "Lancement", "Démarrage", "Téléchargements", "Animations",
                "Mods optionnels", "Captures d'écran", "Maintenance", "À propos"};
        Region[] secs = { memorySection(), audioSection(), launchSection(), startupSection(), downloadsSection(),
                animationsSection(), modsSection(), screenshotsSection(), maintenanceSection(), aboutSection() };

        VBox page = new VBox(rem(1.9));
        page.setId("reglages-page");
        page.setPadding(new Insets(rem(0.6), rem(2.2), rem(2.6), rem(1.2)));
        page.setMaxWidth(rem(54));
        page.getChildren().addAll(secs);
        reloadScreenshots();   // une page = re-scan des captures à chaque ouverture des paramètres

        StackPane contentHost = new StackPane(page);
        contentHost.setAlignment(Pos.TOP_LEFT);

        ScrollPane sp = new ScrollPane(contentHost);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.getStyleClass().add("reglages-defilement");
        sp.setPannable(true);
        HBox.setHgrow(sp, Priority.ALWAYS);

        // Rail gauche : ancres de défilement + surlignage qui SUIT le défilement.
        VBox nav = new VBox(rem(0.32));
        nav.setPadding(new Insets(rem(0.7), rem(0.6), rem(1.4), rem(1.2)));
        double navW = rem(10.4);
        nav.setMinWidth(navW);
        nav.setPrefWidth(navW);
        java.util.List<Button> items = new java.util.ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            Button it = new Button(names[i]);
            it.getStyleClass().add("reglages-nav");
            it.setMaxWidth(Double.MAX_VALUE);
            it.setAlignment(Pos.CENTER_LEFT);
            it.setFont(Polices.extra(rem(0.7)));
            it.setPadding(new Insets(rem(0.42), rem(0.9), rem(0.42), rem(0.9)));
            it.setOnAction(e -> scrollToSection(sp, page, secs[idx]));
            items.add(it);
            nav.getChildren().add(it);
        }
        items.get(0).getStyleClass().add("actif");

        // Le rail suit la position de défilement : la section la plus haute visible est « active ».
        sp.vvalueProperty().addListener((obs, o, v) -> {
            double contentH = page.getBoundsInLocal().getHeight();
            double viewH = sp.getViewportBounds().getHeight();
            double offset = v.doubleValue() * Math.max(0, contentH - viewH) + rem(3);
            int active = 0;
            for (int i = 0; i < secs.length; i++)
                if (secs[i].getBoundsInParent().getMinY() <= offset) active = i;
            for (int i = 0; i < items.size(); i++) {
                items.get(i).getStyleClass().remove("actif");
                if (i == active) items.get(i).getStyleClass().add("actif");
            }
        });

        HBox body = new HBox(nav, sp);

        // Hôte qui empile le corps + une éventuelle photo agrandie ou confirmation par-dessus.
        bodyStack = new StackPane(body);
        return bodyStack;
    }

    /** Défilement doux (260 ms) jusqu'au haut de la section ; immédiat en « animations réduites ». */
    private void scrollToSection(ScrollPane sp, Region page, Region target) {
        double contentH = page.getBoundsInLocal().getHeight();
        double viewH = sp.getViewportBounds().getHeight();
        double denom = Math.max(1, contentH - viewH);
        double v = Math.max(0, Math.min(1, (target.getBoundsInParent().getMinY() - rem(0.6)) / denom));
        if (Settings.get().animationsReduites) { sp.setVvalue(v); return; }
        javafx.animation.Timeline tl = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(260),
                        new javafx.animation.KeyValue(sp.vvalueProperty(), v, Jetons.SORTIE)));
        tl.play();
    }

    /** Une section : titre Northura sur le ciel, puis son contenu. */
    private VBox section(String titre, javafx.scene.Node... contenu) {
        VBox v = new VBox(rem(0.75));
        v.getChildren().add(Elements.titreSection(titre));
        v.getChildren().addAll(contenu);
        return v;
    }

    // ── Mémoire ─────────────────────────────────────────────────────────────

    private VBox memorySection() {
        // Mémoire « Auto » (15/10/2026) : le curseur affiche la valeur choisie pour ce PC ; le
        // déplacer repasse en manuel et garde la valeur choisie.
        long physMb = fr.nylerp.launcher.util.SystemRam.totalMb();
        int autoMb = Settings.autoRamMb(physMb);
        int autoGb = autoMb / 1024;
        int startGb = Settings.get().ramAuto ? autoGb
                : Math.max(2, Math.min(16, Settings.get().ramMb / 1024));

        // Nombre flottant qui suit la pastille.
        Label big = new Label(String.valueOf(startGb));
        big.setFont(Polices.noir(rem(2.6)));
        big.setTextFill(Jetons.ENCRE);
        Label unit = new Label("Go");
        unit.setFont(Polices.extra(rem(0.95)));
        unit.setTextFill(Jetons.ENCRE_2);
        HBox numGroup = new HBox(rem(0.3), big, unit);
        numGroup.setAlignment(Pos.BASELINE_LEFT);

        Pane numLane = new Pane(numGroup);
        numLane.setMinHeight(rem(3.4));
        numLane.setPrefHeight(rem(3.4));

        Label max = Elements.note("Max : 16 Go");
        HBox maxRow = new HBox(max);
        maxRow.setAlignment(Pos.TOP_RIGHT);

        Slider slider = new Slider(2, 16, startGb);
        slider.setBlockIncrement(1);
        slider.setMajorTickUnit(2);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.getStyleClass().add("reglages-curseur");
        slider.setPrefHeight(22);
        slider.setMaxWidth(Double.MAX_VALUE);

        final double THUMB_R = 9; // pastille de 18 px (reglages.css)

        // Le Slider JavaFX n'a pas de portion « remplie » : un rail pleine largeur + un rail nacré dont
        // la largeur suit la pastille. La pastille native reste par-dessus.
        Region trackBg = new Region();
        trackBg.getStyleClass().add("reglages-rail");
        trackBg.setMinHeight(7); trackBg.setPrefHeight(7); trackBg.setMaxHeight(7);
        Region trackFill = new Region();
        trackFill.getStyleClass().add("reglages-rail-plein");
        trackFill.setMinHeight(7); trackFill.setPrefHeight(7); trackFill.setMaxHeight(7);
        StackPane.setAlignment(trackBg, Pos.CENTER);
        StackPane.setAlignment(trackFill, Pos.CENTER_LEFT);
        StackPane sliderStack = new StackPane(trackBg, trackFill, slider);
        sliderStack.setMaxWidth(Double.MAX_VALUE);

        Runnable reposition = () -> {
            double sliderW = slider.getWidth();
            if (sliderW <= 0) return;
            double numW = numGroup.getWidth();
            double ratio = (slider.getValue() - 2) / 14.0;
            // Le centre de la pastille va de THUMB_R à sliderW - THUMB_R.
            double thumbX = THUMB_R + ratio * (sliderW - 2 * THUMB_R);
            trackFill.setMinWidth(thumbX); trackFill.setPrefWidth(thumbX); trackFill.setMaxWidth(thumbX);
            double x = thumbX - numW / 2;
            double laneW = numLane.getWidth();
            if (laneW > 0) x = Math.max(0, Math.min(laneW - numW, x));
            numGroup.setTranslateX(x);
        };
        // Ligne « Automatique ». Le drapeau évite qu'un déplacement du curseur PAR le code (retour en
        // Auto) ne soit pris pour un choix du joueur.
        final boolean[] programmatic = {false};
        int autoEffGb = fr.nylerp.launcher.launch.MinecraftLauncher.clampHeapMb(autoMb, physMb) / 1024;
        String autoDesc = physMb > 0
                ? autoEffGb + " Go pour ce PC (" + Math.round(physMb / 1024.0) + " Go de RAM). Déplace le curseur pour choisir toi-même."
                : autoEffGb + " Go (mémoire du PC non détectée). Déplace le curseur pour choisir toi-même.";
        Interrupteur autoToggle = Elements.interrupteur(Settings.get().ramAuto, v -> {
            Settings.get().ramAuto = v;
            if (v) {
                programmatic[0] = true;
                slider.setValue(autoGb);
                programmatic[0] = false;
            } else {
                // Sortie de l'Auto par l'interrupteur : on garde ce que le curseur affiche.
                Settings.get().ramMb = (int) Math.round(slider.getValue()) * 1024;
            }
            Settings.get().save();
        }, false);
        HBox autoRow = Elements.ligne("Automatique", autoDesc, autoToggle, false);

        slider.valueProperty().addListener((o, a, b) -> {
            int g = b.intValue();
            big.setText(String.valueOf(g));
            if (!programmatic[0]) {
                // Le joueur choisit : on quitte l'Auto et on garde SA valeur.
                Settings.get().ramAuto = false;
                Settings.get().ramMb = g * 1024;
                Settings.get().save();
                if (autoToggle.isActif()) autoToggle.setActif(false);
            }
            reposition.run();
        });
        slider.widthProperty().addListener((o, a, b) -> reposition.run());
        numGroup.widthProperty().addListener((o, a, b) -> reposition.run());
        numLane.widthProperty().addListener((o, a, b) -> reposition.run());
        Platform.runLater(reposition);

        HBox scale = new HBox();
        scale.setAlignment(Pos.CENTER);
        for (int v = 2; v <= 16; v += 2) {
            Label t = new Label(String.valueOf(v));
            t.setFont(Polices.gras(rem(0.52)));
            t.setTextFill(Jetons.ENCRE_3);
            HBox.setHgrow(t, Priority.ALWAYS);
            t.setMaxWidth(Double.MAX_VALUE);
            t.setAlignment(Pos.CENTER);
            scale.getChildren().add(t);
        }

        VBox curseur = new VBox(rem(0.4), maxRow, numLane, sliderStack, scale);
        curseur.setPadding(new Insets(rem(0.4), 0, rem(0.3), 0));
        CarteVerre carte = Elements.carte(
                "Quantité de RAM allouée à Minecraft. En automatique, le launcher la choisit selon la mémoire de ton PC.",
                autoRow, curseur);
        return section("Mémoire", carte);
    }

    // ── Captures d'écran ─────────────────────────────────────────────────────

    private java.nio.file.Path screenshotsDir() {
        return fr.nylerp.launcher.config.AppPaths.gameDir().resolve("screenshots");
    }

    private double thumbW() { return rem(11); }
    private double thumbH() { return rem(11) * 9 / 16; }   // vignette 16:9

    private VBox screenshotsSection() {
        shotSortBtn = Elements.boutonClair("", null);
        shotSortBtn.setOnAction(e -> { shotsNewestFirst = !shotsNewestFirst; reloadScreenshots(); });

        Button openShots = Elements.boutonClair("Ouvrir le dossier", Icons.folder(14, Jetons.ENCRE_2));
        openShots.setOnAction(e -> openInExplorer(screenshotsDir().toFile()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(rem(0.5), shotSortBtn, spacer, openShots);
        actions.setAlignment(Pos.CENTER_LEFT);

        shotGrid = new FlowPane(rem(0.75), rem(0.75));

        VBox contenu = new VBox(rem(0.8), actions, shotGrid);
        contenu.setPadding(new Insets(rem(0.3), 0, rem(0.3), 0));
        reloadScreenshots();
        return section("Captures d'écran", Elements.carte(
                "Toutes les images prises en jeu (touche F2). Clique sur une vignette pour l'agrandir.", contenu));
    }

    /** Re-scanne le dossier screenshots et reconstruit la grille. Les images sont chargées en
     *  arrière-plan (backgroundLoading) et sous-échantillonnées : aucun gel de l'UI même avec
     *  des centaines de captures. */
    private void reloadScreenshots() {
        if (shotGrid == null) return;
        shotSortBtn.setText(shotsNewestFirst ? "Trier · plus récentes" : "Trier · plus anciennes");
        shotGrid.getChildren().clear();

        java.io.File dir = screenshotsDir().toFile();
        java.io.File[] arr = dir.listFiles((d, name) -> {
            String n = name.toLowerCase();
            return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
        });
        if (arr == null || arr.length == 0) { shotGrid.getChildren().add(emptyShots()); return; }

        java.util.List<java.io.File> files = new java.util.ArrayList<>(java.util.Arrays.asList(arr));
        files.sort(java.util.Comparator.comparingLong(java.io.File::lastModified));
        if (shotsNewestFirst) java.util.Collections.reverse(files);
        for (java.io.File f : files) shotGrid.getChildren().add(shotTile(f));
    }

    private Region emptyShots() {
        Label l = Elements.texteLigne("Aucune capture pour l'instant.\nAppuie sur F2 en jeu pour en prendre une : elles apparaîtront ici.");
        VBox v = new VBox(l);
        v.getStyleClass().add("reglages-vide");
        v.setPadding(new Insets(rem(1.2), rem(1.1), rem(1.2), rem(1.1)));
        v.setMaxWidth(Double.MAX_VALUE);
        v.setPrefWidth(rem(34));
        return v;
    }

    private Region shotTile(java.io.File f) {
        double w = thumbW(), h = thumbH();
        Image img = new Image(f.toURI().toString(), 0, 240, true, true, true); // sous-échantillonné, async
        ImageView iv = new ImageView(img);
        iv.setFitWidth(w);
        iv.setFitHeight(h);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);

        StackPane card = new StackPane(iv);
        card.getStyleClass().add("reglages-vignette");
        card.setMinSize(w, h);
        card.setPrefSize(w, h);
        card.setMaxSize(w, h);
        Rectangle clip = new Rectangle(w, h);
        clip.setArcWidth(rem(1.2)); clip.setArcHeight(rem(1.2));
        card.setClip(clip);
        card.setOnMouseClicked(e -> openShotPreview(f));
        card.setOnMouseEntered(e -> { card.setScaleX(1.025); card.setScaleY(1.025); });
        card.setOnMouseExited(e -> { card.setScaleX(1); card.setScaleY(1); });

        return new VBox(rem(0.3), card, Elements.note(shotDate(f)));
    }

    private String shotDate(java.io.File f) {
        try {
            return new java.text.SimpleDateFormat("dd/MM/yyyy · HH:mm")
                    .format(new java.util.Date(f.lastModified()));
        } catch (Throwable t) { return ""; }
    }

    /** Agrandit la capture par-dessus toute la page des paramètres. Clic n'importe où = fermeture. */
    private void openShotPreview(java.io.File f) {
        if (bodyStack == null) return;
        Image full = new Image(f.toURI().toString(), 0, 1600, true, true, true);
        ImageView iv = new ImageView(full);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        iv.fitWidthProperty().bind(bodyStack.widthProperty().subtract(140));
        iv.fitHeightProperty().bind(bodyStack.heightProperty().subtract(150));

        Label caption = new Label(f.getName() + "   ·   " + shotDate(f));
        caption.setFont(Polices.extra(rem(0.62)));
        caption.setTextFill(Color.rgb(255, 255, 255, 0.92));
        Label hint = new Label("Clique n'importe où pour fermer");
        hint.setFont(Polices.gras(rem(0.56)));
        hint.setTextFill(Color.rgb(255, 255, 255, 0.6));

        VBox content = new VBox(rem(0.7), iv, caption, hint);
        content.setAlignment(Pos.CENTER);

        StackPane overlay = new StackPane(content);
        overlay.getStyleClass().add("reglages-voile-photo");
        overlay.setOnMouseClicked(e -> bodyStack.getChildren().remove(overlay));
        bodyStack.getChildren().add(overlay);
        if (Settings.get().animationsReduites) return;
        overlay.setOpacity(0);
        javafx.animation.FadeTransition ft =
                new javafx.animation.FadeTransition(javafx.util.Duration.millis(140), overlay);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
    }

    // ── Audio ───────────────────────────────────────────────────────────────

    private VBox audioSection() {
        Region ambientRow = volumeRow(
                "Ambiance · feu de camp",
                "Crépitement de feu en boucle. Couche sub-musique discrète.",
                Settings.get().ambientVolume,
                v -> {
                    Settings.get().ambientVolume = v;
                    Settings.get().save();
                    MainView.setLiveAmbientVolume(v);
                });

        Region musicRow = volumeRow(
                "Musique",
                "Boucle musicale principale, posée par-dessus l'ambiance.",
                Settings.get().musicVolume,
                v -> {
                    Settings.get().musicVolume = v;
                    Settings.get().save();
                    MainView.setLiveMusicVolume(v);
                });

        return section("Audio du launcher", Elements.carte(
                "Volume de la musique et de l'ambiance jouées sur l'écran d'accueil.", ambientRow, musicRow));
    }

    /** Une ligne de volume : intitulé + explication + curseur 0..100 + pourcentage en direct. */
    private Region volumeRow(String title, String desc, double initial,
                             java.util.function.Consumer<Double> onChange) {
        Label pct = new Label(String.format("%.0f %%", initial * 100.0));
        pct.setFont(Polices.extra(rem(0.82)));
        pct.setTextFill(Jetons.ENCRE);
        pct.setMinWidth(rem(3));
        pct.setAlignment(Pos.CENTER_RIGHT);

        Slider slider = new Slider(0, 100, initial * 100.0);
        slider.setBlockIncrement(1);
        slider.getStyleClass().add("reglages-curseur");
        slider.setPrefHeight(22);
        slider.setMaxWidth(Double.MAX_VALUE);

        final double THUMB_R = 9;
        Region trackBg = new Region();
        trackBg.getStyleClass().add("reglages-rail");
        trackBg.setMinHeight(7); trackBg.setPrefHeight(7); trackBg.setMaxHeight(7);
        Region trackFill = new Region();
        trackFill.getStyleClass().add("reglages-rail-plein");
        trackFill.setMinHeight(7); trackFill.setPrefHeight(7); trackFill.setMaxHeight(7);
        StackPane.setAlignment(trackBg, Pos.CENTER);
        StackPane.setAlignment(trackFill, Pos.CENTER_LEFT);
        StackPane sliderStack = new StackPane(trackBg, trackFill, slider);
        HBox.setHgrow(sliderStack, Priority.ALWAYS);
        Runnable remplir = () -> {
            double sw = slider.getWidth();
            if (sw <= 0) return;
            double x = THUMB_R + (slider.getValue() / 100.0) * (sw - 2 * THUMB_R);
            trackFill.setMinWidth(x); trackFill.setPrefWidth(x); trackFill.setMaxWidth(x);
        };

        slider.valueProperty().addListener((obs, a, b) -> {
            double v = b.doubleValue() / 100.0;
            pct.setText(String.format("%.0f %%", b.doubleValue()));
            onChange.accept(v);
            remplir.run();
        });
        slider.widthProperty().addListener((o, a, b) -> remplir.run());
        Platform.runLater(remplir);

        HBox sliderRow = new HBox(rem(0.8), sliderStack, pct);
        sliderRow.setAlignment(Pos.CENTER_LEFT);

        VBox body = new VBox(rem(0.25), Elements.titreLigne(title, false), Elements.texteLigne(desc), sliderRow);
        body.setPadding(new Insets(rem(0.6), 0, rem(0.6), 0));
        VBox.setMargin(sliderRow, new Insets(rem(0.35), 0, 0, 0));
        return body;
    }

    // ── Lancement ───────────────────────────────────────────────────────────

    private VBox launchSection() {
        HBox closeRow = Elements.ligne(
                "Fermer le launcher au lancement du jeu",
                "Une fois Minecraft démarré, le launcher se ferme, ou se range dans la barre système si tu l'y gardes.",
                Elements.interrupteur(Settings.get().closeOnLaunch,
                        v -> { Settings.get().closeOnLaunch = v; Settings.get().save(); }, false),
                false);

        // Textures légères (owner 23/07) : pack ÷2 Decocraft/Yuushya/PointBlank pour les GPU
        // anciens dont l'atlas dépasse la limite matérielle (crash/chargement infini au lancement).
        HBox lightRow = Elements.ligne(
                "Textures légères (PC modestes, GPU anciens)",
                "Réduit la résolution des textures Decocraft, Yuushya et PointBlank. Coche cette option si le jeu "
                        + "crash au chargement ou charge très lentement : aucun contenu n'est retiré.",
                Elements.interrupteur(fr.nylerp.launcher.util.LightTextures.isEnabled(),
                        v -> fr.nylerp.launcher.util.LightTextures.setEnabled(v), false),
                false);

        Button openFiles = folderButton("Ouvrir le dossier du jeu",
                () -> openInExplorer(fr.nylerp.launcher.config.AppPaths.gameDir().toFile()));
        HBox openRow = new HBox(openFiles);
        openRow.setAlignment(Pos.CENTER_LEFT);
        openRow.setPadding(new Insets(rem(0.6), 0, rem(0.2), 0));

        return section("Lancement", Elements.carte(
                "Comportement au démarrage du jeu et accès rapide à tes fichiers.", closeRow, lightRow, openRow));
    }

    // ── Démarrage et notifications (refonte 2026-10) ──────────────────────────────

    private VBox startupSection() {
        boolean mac = fr.nylerp.launcher.update.OptionalMods.isMac();
        Interrupteur auto = Elements.interrupteur(Settings.get().demarrageAuto, v -> {
            Settings.get().demarrageAuto = v;
            Settings.get().save();
            Fond.appliquerReglages();
        }, false);
        auto.setId("reglage-demarrageAuto");
        Interrupteur garder = Elements.interrupteur(Settings.get().fondActif, v -> {
            Settings.get().fondActif = v;
            Settings.get().save();
            Fond.appliquerReglages();
        }, false);
        garder.setId("reglage-fondActif");
        Interrupteur annonces = Elements.interrupteur(Settings.get().notifications, v -> {
            Settings.get().notifications = v;
            Settings.get().save();
            Fond.appliquerReglages();
        }, false);
        annonces.setId("reglage-notifications");

        return section("Démarrage et notifications", Elements.carte(
                "Le launcher peut rester discrètement dans la barre système pour te prévenir des ouvertures et des événements.",
                Elements.ligne("Lancer au démarrage de l'ordinateur",
                        "Le launcher s'ouvre en fond, sans fenêtre, quand tu allumes ton ordinateur.", auto, false),
                Elements.ligne("Garder le launcher dans la barre système à la fermeture",
                        mac ? "Fermer la fenêtre le range dans la barre des menus, en haut de l'écran, au lieu de le quitter."
                            : "Fermer la fenêtre le range près de l'horloge, dans les icônes cachées, au lieu de le quitter.",
                        garder, false),
                Elements.ligne("Annonces",
                        "Ouvertures, événements et nouveautés, affichés dans un coin de l'écran.", annonces, false)));
    }

    // ── Téléchargements ─────────────────────────────────────────────────────

    private VBox downloadsSection() {
        Interrupteur pre = Elements.interrupteur(Settings.get().preTelechargement, v -> {
            Settings.get().preTelechargement = v;
            Settings.get().save();
        }, false);
        pre.setId("reglage-preTelechargement");
        return section("Téléchargements", Elements.carte(null,
                Elements.ligne("Pré-télécharger les mises à jour en fond",
                        "Les mises à jour du jeu se téléchargent pendant que le launcher est dans la barre système : "
                                + "« Jouer » démarre tout de suite.", pre, false)));
    }

    // ── Animations ──────────────────────────────────────────────────────────

    private VBox animationsSection() {
        Interrupteur reduites = Elements.interrupteur(Settings.get().animationsReduites, v -> {
            Settings.get().animationsReduites = v;
            Settings.get().save();
        }, false);
        reduites.setId("reglage-animationsReduites");
        return section("Animations", Elements.carte(null,
                Elements.ligne("Animations réduites",
                        "Remplace le ciel animé par une image fixe et limite les mouvements. Utile sur les ordinateurs modestes.",
                        reduites, false)));
    }

    /** Bouton clair avec une icône de dossier, qui ouvre un dossier. Sert au jeu et aux mods. */
    private Button folderButton(String label, Runnable action) {
        Button b = Elements.boutonClair(label, Icons.folder(14, Jetons.ENCRE_2));
        b.setOnAction(e -> action.run());
        return b;
    }

    /** Ouvre un dossier dans l'explorateur de l'OS. Sur un thread à part et totalement try/catch :
     *  ouvrir un dossier ne doit JAMAIS faire planter le launcher. Le dossier est créé s'il manque. */
    private void openInExplorer(java.io.File dir) {
        new Thread(() -> {
            try { if (!dir.exists()) dir.mkdirs(); } catch (Throwable ignored) {}
            try {
                if (java.awt.Desktop.isDesktopSupported()
                        && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                    java.awt.Desktop.getDesktop().open(dir);
                    return;
                }
            } catch (Throwable ignored) { /* on tente le fallback ProcessBuilder ci-dessous */ }
            try {
                String os = System.getProperty("os.name", "").toLowerCase();
                String[] cmd = os.contains("win")
                        ? new String[]{"explorer", dir.getAbsolutePath()}
                        : os.contains("mac")
                            ? new String[]{"open", dir.getAbsolutePath()}
                            : new String[]{"xdg-open", dir.getAbsolutePath()};
                new ProcessBuilder(cmd).start();
            } catch (Throwable ignored) { /* jamais throw */ }
        }, "open-game-folder").start();
    }

    // ── Mods optionnels ─────────────────────────────────────────────────────

    private VBox modsSection() {
        HBox litematicaRow = optionalModRow(
                "Litematica",
                "Prévisualisation et construction assistée par schémas.",
                Settings.get().optionalLitematica,
                v -> { Settings.get().optionalLitematica = v; Settings.get().save(); });

        // Pokényle (15/10/2026) : Iris est toujours installé et Complementary actif par défaut
        // (OptionalMods, ShaderDefaults) : la ligne est donc verrouillée sur « activé », et dit
        // comment couper les shaders : en jeu, touche K.
        boolean pokenyle = fr.nylerp.launcher.config.ModeDeJeu.courant()
                == fr.nylerp.launcher.config.ModeDeJeu.POKENYLE;
        HBox irisRow = pokenyle
                ? optionalModRow(
                        "Iris (shaders)",
                        "Toujours installé sur pokényle, avec les shaders Complementary Reimagined. "
                                + "Pour couper ou rallumer les shaders : touche K en jeu.",
                        true, v -> { }, true)
                : optionalModRow(
                        "Iris (shaders)",
                        "Active le support des shader packs et le rendu compatible Iris. Recommandé GPU dédié.",
                        Settings.get().optionalIris,
                        v -> { Settings.get().optionalIris = v; Settings.get().save(); });

        boolean dhMac = fr.nylerp.launcher.update.OptionalMods.isMac();
        HBox dhRow = optionalModRow(
                "Distant Horizons",
                dhMac ? "Indisponible sur Mac : le rendu OpenGL/Metal d'Apple fige le jeu (PC uniquement)."
                      : "Affiche les chunks éloignés en LOD. Active Iris automatiquement (requis pour le rendu fluide). ~1 GB de RAM en plus.",
                Settings.get().optionalDistantHorizons && !dhMac,
                v -> { Settings.get().optionalDistantHorizons = v; Settings.get().save(); },
                dhMac);

        HBox punchyRow = optionalModRow(
                "Punchy! (animations)",
                "Tes mains deviennent visibles avec l'objet tenu, et les coups, les déplacements et les interactions sont animés. Purement visuel : ni les dégâts ni le recul ne changent. Réglable en jeu.",
                Settings.get().optionalPunchy,
                v -> { Settings.get().optionalPunchy = v; Settings.get().save(); });

        Button openMods = folderButton("Ouvrir le dossier des mods",
                () -> openInExplorer(fr.nylerp.launcher.config.AppPaths.gameDir().resolve("mods").toFile()));
        HBox openModsRow = new HBox(openMods);
        openModsRow.setAlignment(Pos.CENTER_LEFT);
        openModsRow.setPadding(new Insets(rem(0.6), 0, rem(0.2), 0));

        java.util.List<javafx.scene.Node> lignes = new java.util.ArrayList<>(
                java.util.List.of(litematicaRow, irisRow, dhRow, punchyRow));
        if (pokenyle) {
            // Manette : optionnel du mode pokényle seulement (OptionalMods, Controlify).
            lignes.add(optionalModRow(
                    "Manette (Controlify)",
                    "Permet de jouer à la manette. Laisse-le coupé si tu joues au clavier et à la souris : "
                            + "il interroge les manettes en permanence et pèse sur la fluidité.",
                    Settings.get().optionalControlify,
                    v -> { Settings.get().optionalControlify = v; Settings.get().save(); }));
        }
        lignes.add(openModsRow);
        return section("Mods optionnels", Elements.carte(
                "Mods que tu peux activer en plus du modpack. Ils seront téléchargés au prochain lancement.",
                lignes.toArray(new javafx.scene.Node[0])));
    }

    /** Une ligne de mod optionnel : intitulé + explication à gauche, interrupteur à droite. */
    private HBox optionalModRow(String title, String desc, boolean initial,
                                java.util.function.Consumer<Boolean> onChange) {
        return optionalModRow(title, desc, initial, onChange, false);
    }

    private HBox optionalModRow(String title, String desc, boolean initial,
                                java.util.function.Consumer<Boolean> onChange, boolean disabled) {
        return Elements.ligne(title, desc, Elements.interrupteur(initial, onChange, disabled), disabled);
    }

    // ── Maintenance : Réparer + Désinstaller (owner 2026-07-16) ─────────────

    private VBox maintenanceSection() {
        // RÉPARER : purge les caches de vérification puis rejoue le sync complet du pack (re-hash +
        // re-téléchargement de tout fichier manquant/altéré) + mods optionnels + servers.dat.
        Button repair = Elements.boutonSombre("Réparer le jeu et le launcher");
        Label repairStatus = Elements.note("");
        repair.setOnAction(e -> runRepair(repair, repairStatus));
        Label repairHint = Elements.texteLigne("Re-vérifie chaque fichier du pack et retélécharge ce qui est manquant ou abîmé. Tes réglages, mondes et captures sont conservés.");

        // DÉSINSTALLER : DOUBLE confirmation : armement du bouton (rouge) PUIS panneau de confirmation
        // explicite par-dessus la page. Supprime les données du jeu + du launcher, retire le démarrage
        // automatique et ouvre l'outil de désinstallation de l'OS.
        Button uninstall = Elements.boutonDanger("Désinstaller le launcher…");
        uninstall.setId("reglages-desinstaller");
        final boolean[] armed = {false};
        uninstall.setOnAction(e -> {
            if (!armed[0]) {
                armed[0] = true;
                uninstall.setText("Confirmer la désinstallation ?");
                uninstall.getStyleClass().add("arme");
                // désarmement auto après 5 s sans second clic
                javafx.animation.PauseTransition pt = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(5));
                pt.setOnFinished(ev -> {
                    armed[0] = false;
                    uninstall.setText("Désinstaller le launcher…");
                    uninstall.getStyleClass().remove("arme");
                });
                pt.play();
            } else {
                showUninstallConfirm();   // 2ᵉ confirmation : panneau modal explicite
            }
        });
        Label unHint = Elements.texteLigne("Supprime le launcher, le jeu, les mods, les réglages et les comptes de cet ordinateur, "
                + "et retire son lancement au démarrage. Irréversible.");

        VBox reparer = new VBox(rem(0.4), repair, repairHint, repairStatus);
        reparer.setPadding(new Insets(rem(0.6), 0, rem(0.6), 0));
        VBox desinstaller = new VBox(rem(0.4), uninstall, unHint);
        desinstaller.setPadding(new Insets(rem(0.6), 0, rem(0.4), 0));
        return section("Maintenance", Elements.carte(
                "Répare une installation cassée, ou supprime le launcher et tous les fichiers du jeu.",
                reparer, desinstaller));
    }

    /** Réparation asynchrone : purge hashcache + manifest + record des mods managés, puis sync
     *  complet (chaque fichier re-hashé / re-téléchargé), mods optionnels, servers.dat. */
    private void runRepair(Button btn, Label status) {
        btn.setDisable(true);
        status.setText("Réparation en cours : vérification de chaque fichier…");
        Thread t = new Thread(() -> {
            String result;
            try {
                java.nio.file.Path st = fr.nylerp.launcher.config.AppPaths.launcherState();
                java.nio.file.Files.deleteIfExists(st.resolve("hashcache.json"));
                java.nio.file.Files.deleteIfExists(st.resolve("manifest.json"));
                java.nio.file.Files.deleteIfExists(fr.nylerp.launcher.config.AppPaths.gameDir().resolve(".nyle_managed_mods"));
                fr.nylerp.launcher.update.ServerListSanitizer.sweep();
                new fr.nylerp.launcher.update.ModpackUpdater(new fr.nylerp.launcher.update.ModpackUpdater.Listener() {
                    @Override public void onStatus(String line) {
                        Platform.runLater(() -> status.setText(line));
                    }
                    @Override public void onProgress(int done, int total, long bytesDone, long bytesTotal) {
                        Platform.runLater(() -> status.setText("Téléchargement " + done + " / " + total + "…"));
                    }
                }).sync();
                fr.nylerp.launcher.update.OptionalMods.applyAll();
                result = "Réparation terminée : le jeu est prêt.";
            } catch (Throwable ex) {
                result = "Réparation incomplète : " + ex.getMessage() + ". Réessaie ou vérifie ta connexion.";
            }
            String finalResult = result;
            Platform.runLater(() -> { status.setText(finalResult); btn.setDisable(false); });
        }, "nyle-repair");
        t.setDaemon(true);
        t.start();
    }

    /** 2ᵉ étape de la désinstallation : panneau modal par-dessus la page, choix explicite. */
    private void showUninstallConfirm() {
        Label t = new Label("Tout supprimer ?");
        t.setFont(Polices.noir(rem(0.95)));
        t.setTextFill(Jetons.ENCRE);
        Label d = Elements.intro("Le launcher, le jeu, les mods, les réglages et les comptes enregistrés seront "
                + "supprimés de cet ordinateur, et le launcher ne se lancera plus au démarrage. "
                + "Cette action est irréversible.");

        Button cancel = Elements.boutonClair("Annuler", null);
        Button confirm = Elements.boutonDanger("Tout supprimer");
        confirm.getStyleClass().add("arme");
        HBox btns = new HBox(rem(0.5), cancel, confirm);
        btns.setAlignment(Pos.CENTER_RIGHT);

        VBox contenu = new VBox(rem(0.75), t, d, btns);
        CarteVerre card = new CarteVerre(CarteVerre.Variante.OPAQUE, contenu);
        card.setMaxWidth(rem(24));
        card.setMaxHeight(Region.USE_PREF_SIZE);

        StackPane veil = new StackPane(card);
        veil.getStyleClass().add("reglages-voile-modal");
        veil.setAlignment(Pos.CENTER);
        veil.setId("reglages-confirmation");
        bodyStack.getChildren().add(veil);
        cancel.setOnAction(e -> bodyStack.getChildren().remove(veil));
        confirm.setOnAction(e -> {
            confirm.setDisable(true);
            cancel.setDisable(true);
            d.setText("Suppression en cours…");
            Thread th = new Thread(this::performUninstall, "nyle-uninstall");
            th.setDaemon(true);
            th.start();
        });
    }

    /** Retire le démarrage automatique, supprime les données (best effort), ouvre l'outil de
     *  désinstallation de l'OS et quitte (icône de barre comprise). */
    private void performUninstall() {
        // Démarrage automatique : par l'API du fond, PUIS de façon synchrone sur ce fil, pour que
        // l'entrée soit partie avant l'arrêt du processus (la synchronisation du fond est asynchrone).
        Fond.retirerDemarrageAuto();
        try { DemarrageAuto.pourCeSysteme().desactiver(); } catch (Throwable ignored) { }
        deleteRecursive(fr.nylerp.launcher.config.AppPaths.rootDir());   // game/ state/ minecraft/ runtime/
        deleteRecursive(payloadCacheDir());                              // cache payload du bootstrap (NyleRP)
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                // Panneau « Applications installées » : l'utilisateur clique Désinstaller NyleLauncher.
                Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "ms-settings:appsfeatures"});
            } else if (os.contains("mac")) {
                // Révèle l'app dans le Finder : glisser à la corbeille termine la désinstallation.
                Runtime.getRuntime().exec(new String[]{"open", "-R", "/Applications/NyleLauncher.app"});
            }
        } catch (Throwable ignored) { }
        // Fond.quitter et pas Platform.exit : l'icône de barre système (AWT) garderait sinon le
        // processus en vie après la suppression de ses fichiers.
        Platform.runLater(Fond::quitter);
    }

    private static java.nio.file.Path payloadCacheDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String l = System.getenv("LOCALAPPDATA");
            return java.nio.file.Path.of(l != null ? l : home + "\\AppData\\Local", "NyleRP");
        }
        if (os.contains("mac")) return java.nio.file.Path.of(home, "Library", "Application Support", "NyleRP");
        return java.nio.file.Path.of(home, ".nylerp");
    }

    /** Suppression récursive best-effort (les fichiers verrouillés, ex. le jar payload en cours
     *  d'exécution sous Windows, sont simplement laissés ; quelques Ko sans conséquence). */
    private static void deleteRecursive(java.nio.file.Path root) {
        try (var walk = java.nio.file.Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { java.nio.file.Files.deleteIfExists(p); } catch (Throwable ignored) { }
            });
        } catch (Throwable ignored) { }
    }

    // ── À propos ────────────────────────────────────────────────────────────

    private VBox aboutSection() {
        GridPane g = new GridPane();
        g.setHgap(rem(2)); g.setVgap(rem(0.6));
        g.add(kvColumn("VERSION", fr.nylerp.launcher.update.SelfUpdater.installedVersion()), 0, 0);
        g.add(kvColumn("PAYLOAD", Constants.runningPayloadVersion()), 1, 0);
        g.add(kvColumn("SERVEUR", Constants.serverHost()), 2, 0);
        g.add(kvColumn("LOADER", "Fabric"), 3, 0);
        g.add(kvColumn("MC", Constants.MC_VERSION), 4, 0);
        g.setPadding(new Insets(rem(0.3), 0, rem(0.6), 0));

        // Crédit obligatoire (Complementary License Agreement 1.7, § 1.2.a) : le pack est actif
        // par défaut en mode pokényle.
        Label credit = Elements.note("Shaders : Complementary Reimagined par EminGT (complementary.dev), téléchargé depuis Modrinth");
        VBox credits = new VBox(credit);
        credits.setPadding(new Insets(rem(0.6), 0, rem(0.2), 0));
        return section("À propos", Elements.carte(null, g, credits));
    }

    private VBox kvColumn(String k, String v) {
        Label key = new Label(k);
        key.setFont(Polices.noir(rem(0.52)));
        key.setTextFill(Color.web("#8fa1c2"));
        key.setStyle("-fx-letter-spacing: 0.20em;");
        Label val = new Label(v);
        val.setFont(Polices.extra(rem(0.74)));
        val.setTextFill(Jetons.ENCRE);
        return new VBox(rem(0.3), key, val);
    }
}
