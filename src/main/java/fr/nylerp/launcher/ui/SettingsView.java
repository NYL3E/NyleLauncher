package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.config.Constants;
import fr.nylerp.launcher.config.Settings;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class SettingsView extends BorderPane {

    private static final int THUMB_W = 212, THUMB_H = 119; // vignette 16:9 (3 colonnes dans le contenu 678 px)

    private StackPane contentHost;
    private StackPane bodyStack;                            // hôte plein-écran pour la lightbox d'aperçu
    private FlowPane shotGrid;                              // grille de vignettes (re-scannée à l'ouverture)
    private Button shotSortBtn;
    private boolean shotsNewestFirst = true;

    public SettingsView(Runnable onBack) {
        getStyleClass().add("main-root");
        setTop(buildTopBar(onBack));
        setCenter(buildBody());
    }

    private Region buildTopBar(Runnable onBack) {
        Button back = new Button();
        back.getStyleClass().add("icon-btn");
        back.setGraphic(Icons.arrowLeft(18, Color.web("#F4F4F7")));
        back.setOnAction(e -> onBack.run());

        Label title = new Label("PARAMÈTRES");
        title.setFont(Fonts.black(12));
        title.setTextFill(Color.web("#F4F4F7"));
        title.setStyle("-fx-letter-spacing: 0.22em;");

        HBox bar = new HBox(14, back, title);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 24, 0, 20));
        bar.setPrefHeight(56);
        bar.getStyleClass().add("top-bar");
        return bar;
    }

    /** UNE PAGE défilable + CATÉGORIES À GAUCHE (owner 2026-07-16 v2) : le rail est conservé mais
     *  devient une navigation par ANCRES — cliquer une catégorie fait défiler la page en douceur
     *  jusqu'à sa section, et la catégorie active suit le scroll. Tout reste visible en défilant :
     *  rien n'est caché derrière un onglet. MAINTENANCE (Réparer/Désinstaller) puis À PROPOS en bas. */
    private Region buildBody() {
        String[] names = {"Mémoire", "Audio", "Lancement", "Mods optionnels",
                "Captures d'écran", "Maintenance", "À propos"};
        Region[] secs = { memorySection(), audioSection(), launchSection(), modsSection(),
                screenshotsSection(), maintenanceSection(), aboutSection() };

        VBox page = new VBox(0);
        page.setPadding(new Insets(30, 56, 56, 46));
        page.setMaxWidth(820);
        for (int i = 0; i < secs.length; i++) {
            if (i > 0) page.getChildren().add(pageBreak());
            page.getChildren().add(secs[i]);
        }
        reloadScreenshots();   // une page = re-scan des captures à chaque ouverture des paramètres

        contentHost = new StackPane(page);
        contentHost.setAlignment(Pos.TOP_LEFT);

        ScrollPane sp = new ScrollPane(contentHost);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.getStyleClass().add("settings-scroll");
        sp.setPannable(true);
        HBox.setHgrow(sp, Priority.ALWAYS);

        // Rail gauche : ancres de défilement + surlignage qui SUIT le scroll.
        VBox nav = new VBox(4);
        nav.getStyleClass().add("settings-nav");
        nav.setPadding(new Insets(30, 14, 30, 22));
        nav.setMinWidth(214);
        nav.setPrefWidth(214);
        java.util.List<Button> items = new java.util.ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            Button it = new Button(names[i]);
            it.getStyleClass().add("settings-nav-item");
            it.setMaxWidth(Double.MAX_VALUE);
            it.setAlignment(Pos.CENTER_LEFT);
            it.setFont(Fonts.semi(14));
            it.setOnAction(e -> scrollToSection(sp, page, secs[idx]));
            items.add(it);
            nav.getChildren().add(it);
        }
        items.get(0).getStyleClass().add("active");

        // Le rail suit la position de scroll : la section la plus haute visible est « active ».
        sp.vvalueProperty().addListener((obs, o, v) -> {
            double contentH = page.getBoundsInLocal().getHeight();
            double viewH = sp.getViewportBounds().getHeight();
            double offset = v.doubleValue() * Math.max(0, contentH - viewH) + 60;
            int active = 0;
            for (int i = 0; i < secs.length; i++)
                if (secs[i].getBoundsInParent().getMinY() <= offset) active = i;
            for (int i = 0; i < items.size(); i++) {
                items.get(i).getStyleClass().remove("active");
                if (i == active) items.get(i).getStyleClass().add("active");
            }
        });

        Region divider = new Region();
        divider.getStyleClass().add("settings-nav-divider");
        divider.setMinWidth(1);
        divider.setMaxWidth(1);

        HBox body = new HBox(nav, divider, sp);
        body.getStyleClass().add("settings-body");

        // Hôte qui empile le corps + une éventuelle lightbox/confirmation par-dessus.
        bodyStack = new StackPane(body);
        return bodyStack;
    }

    /** Défilement doux (260 ms, ease-out) jusqu'au haut de la section cible. */
    private void scrollToSection(ScrollPane sp, Region page, Region target) {
        double contentH = page.getBoundsInLocal().getHeight();
        double viewH = sp.getViewportBounds().getHeight();
        double denom = Math.max(1, contentH - viewH);
        double v = Math.max(0, Math.min(1, (target.getBoundsInParent().getMinY() - 12) / denom));
        javafx.animation.Timeline tl = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(260),
                        new javafx.animation.KeyValue(sp.vvalueProperty(), v,
                                javafx.animation.Interpolator.EASE_OUT)));
        tl.play();
    }

    /** Espace + filet + espace entre deux sections de la page unique. */
    private Region pageBreak() {
        Region line = new Region();
        line.setMinHeight(1);
        line.setMaxHeight(1);
        line.setStyle("-fx-background-color: rgba(255,255,255,0.07);");
        VBox box = new VBox(line);
        box.setPadding(new Insets(30, 0, 30, 0));
        return box;
    }

    // ── Mémoire ─────────────────────────────────────────────────────────────

    private VBox memorySection() {
        Label h = new Label("Mémoire");
        h.setFont(Fonts.bold(20));
        h.setTextFill(Color.web("#F4F4F7"));

        Label p = new Label("Quantité de RAM allouée à Minecraft. 4 Go suffit à 95 % des joueurs.");
        p.setFont(Fonts.medium(13));
        p.setTextFill(Color.web("#A2A2AC"));
        p.setWrapText(true);

        int startGb = Math.max(2, Math.min(16, Settings.get().ramMb / 1024));

        // Floating number that tracks the thumb
        Label big = new Label(String.valueOf(startGb));
        big.setFont(Fonts.black(64));
        big.setTextFill(Color.web("#F4F4F7"));
        Label unit = new Label("Go");
        unit.setFont(Fonts.medium(18));
        unit.setTextFill(Color.web("#A2A2AC"));
        HBox numGroup = new HBox(6, big, unit);
        numGroup.setAlignment(Pos.BASELINE_LEFT);

        Pane numLane = new Pane(numGroup);
        numLane.setMinHeight(78);
        numLane.setPrefHeight(78);

        Label max = new Label("Max : 16 Go");
        max.setFont(Fonts.semi(11));
        max.setTextFill(Color.web("#6A6A74"));
        HBox maxRow = new HBox(max);
        maxRow.setAlignment(Pos.TOP_RIGHT);

        Slider slider = new Slider(2, 16, startGb);
        slider.setBlockIncrement(1);
        slider.setMajorTickUnit(2);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.getStyleClass().add("ram-slider-big");
        slider.setPrefHeight(22);
        slider.setMaxWidth(Double.MAX_VALUE);

        final double THUMB_R = 11; // thumb is 22px

        // Le Slider JavaFX n'a pas de portion « remplie ». On peint nous-mêmes une barre de
        // progression orange : un rail gris pleine largeur + un rail accent dont la largeur suit
        // le pouce. Bien plus lisible qu'une simple ligne grise. Le pouce natif reste par-dessus.
        Region trackBg = new Region();
        trackBg.getStyleClass().add("ram-track-bg");
        trackBg.setMinHeight(6); trackBg.setPrefHeight(6); trackBg.setMaxHeight(6);
        Region trackFill = new Region();
        trackFill.getStyleClass().add("ram-track-fill");
        trackFill.setMinHeight(6); trackFill.setPrefHeight(6); trackFill.setMaxHeight(6);
        StackPane.setAlignment(trackBg, Pos.CENTER);
        StackPane.setAlignment(trackFill, Pos.CENTER_LEFT);
        StackPane sliderStack = new StackPane(trackBg, trackFill, slider);
        sliderStack.setMaxWidth(Double.MAX_VALUE);

        Runnable reposition = () -> {
            double sliderW = slider.getWidth();
            if (sliderW <= 0) return;
            double numW = numGroup.getWidth();
            double ratio = (slider.getValue() - 2) / 14.0;
            // Thumb center travels from THUMB_R to sliderW - THUMB_R
            double thumbX = THUMB_R + ratio * (sliderW - 2 * THUMB_R);
            // La barre orange court du bord gauche jusqu'au centre du pouce.
            trackFill.setMinWidth(thumbX); trackFill.setPrefWidth(thumbX); trackFill.setMaxWidth(thumbX);
            double x = thumbX - numW / 2;
            double laneW = numLane.getWidth();
            if (laneW > 0) x = Math.max(0, Math.min(laneW - numW, x));
            numGroup.setTranslateX(x);
        };
        slider.valueProperty().addListener((o, a, b) -> {
            int g = b.intValue();
            big.setText(String.valueOf(g));
            Settings.get().ramMb = g * 1024;
            Settings.get().save();
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
            t.setFont(Fonts.medium(10));
            t.setTextFill(Color.web("#3E3E48"));
            HBox.setHgrow(t, Priority.ALWAYS);
            t.setMaxWidth(Double.MAX_VALUE);
            t.setAlignment(Pos.CENTER);
            scale.getChildren().add(t);
        }

        return new VBox(10, h, p, maxRow, numLane, sliderStack, scale);
    }

    // ── Captures d'écran ─────────────────────────────────────────────────────

    private java.nio.file.Path screenshotsDir() {
        return fr.nylerp.launcher.config.AppPaths.gameDir().resolve("screenshots");
    }

    private VBox screenshotsSection() {
        Label h = new Label("Captures d'écran");
        h.setFont(Fonts.bold(20));
        h.setTextFill(Color.web("#F4F4F7"));

        Label p = new Label("Toutes les images prises en jeu (touche F2). Clique sur une vignette pour l'agrandir.");
        p.setFont(Fonts.medium(13));
        p.setTextFill(Color.web("#A2A2AC"));
        p.setWrapText(true);

        shotSortBtn = new Button();
        shotSortBtn.getStyleClass().add("btn-ghost");
        shotSortBtn.setFont(Fonts.semi(13));
        shotSortBtn.setOnAction(e -> { shotsNewestFirst = !shotsNewestFirst; reloadScreenshots(); });

        Button openShots = new Button("Ouvrir le dossier");
        openShots.getStyleClass().add("btn-ghost");
        openShots.setFont(Fonts.semi(13));
        openShots.setGraphic(Icons.folder(14, Color.web("#F4F4F7")));
        openShots.setGraphicTextGap(8);
        openShots.setOnAction(e -> openInExplorer(screenshotsDir().toFile()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, shotSortBtn, spacer, openShots);
        actions.setAlignment(Pos.CENTER_LEFT);

        shotGrid = new FlowPane(14, 14);
        shotGrid.getStyleClass().add("shot-grid");

        VBox box = new VBox(14, h, p, actions, shotGrid);
        reloadScreenshots();
        return box;
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
        Label l = new Label("Aucune capture pour l'instant.\nAppuie sur F2 en jeu pour en prendre une — elles apparaîtront ici.");
        l.setFont(Fonts.medium(13));
        l.setTextFill(Color.web("#8A8A94"));
        l.setWrapText(true);
        VBox v = new VBox(l);
        v.getStyleClass().add("shot-empty");
        v.setPadding(new Insets(26, 22, 26, 22));
        v.setMaxWidth(Double.MAX_VALUE);
        return v;
    }

    private Region shotTile(java.io.File f) {
        Image img = new Image(f.toURI().toString(), 0, 240, true, true, true); // sous-échantillonné, async
        ImageView iv = new ImageView(img);
        iv.setFitWidth(THUMB_W);
        iv.setFitHeight(THUMB_H);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);

        StackPane card = new StackPane(iv);
        card.getStyleClass().add("shot-card");
        card.setMinSize(THUMB_W, THUMB_H);
        card.setPrefSize(THUMB_W, THUMB_H);
        card.setMaxSize(THUMB_W, THUMB_H);
        Rectangle clip = new Rectangle(THUMB_W, THUMB_H);
        clip.setArcWidth(20); clip.setArcHeight(20);
        card.setClip(clip);
        card.setOnMouseClicked(e -> openShotPreview(f));

        Label date = new Label(shotDate(f));
        date.setFont(Fonts.medium(11));
        date.setTextFill(Color.web("#8A8A94"));

        return new VBox(6, card, date);
    }

    private String shotDate(java.io.File f) {
        try {
            return new java.text.SimpleDateFormat("dd/MM/yyyy · HH:mm")
                    .format(new java.util.Date(f.lastModified()));
        } catch (Throwable t) { return ""; }
    }

    /** Lightbox plein-corps : agrandit la capture par-dessus toute la page paramètres.
     *  Clic n'importe où = fermeture. */
    private void openShotPreview(java.io.File f) {
        if (bodyStack == null) return;
        Image full = new Image(f.toURI().toString(), 0, 1600, true, true, true);
        ImageView iv = new ImageView(full);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        iv.fitWidthProperty().bind(bodyStack.widthProperty().subtract(140));
        iv.fitHeightProperty().bind(bodyStack.heightProperty().subtract(150));

        Label caption = new Label(f.getName() + "   —   " + shotDate(f));
        caption.setFont(Fonts.medium(12));
        caption.setTextFill(Color.web("#C9C9D2"));
        Label hint = new Label("Clique n'importe où pour fermer");
        hint.setFont(Fonts.medium(11));
        hint.setTextFill(Color.web("#7A7A84"));

        VBox content = new VBox(14, iv, caption, hint);
        content.setAlignment(Pos.CENTER);

        StackPane overlay = new StackPane(content);
        overlay.getStyleClass().add("shot-preview-overlay");
        overlay.setOnMouseClicked(e -> bodyStack.getChildren().remove(overlay));
        overlay.setOpacity(0);
        bodyStack.getChildren().add(overlay);

        javafx.animation.FadeTransition ft =
                new javafx.animation.FadeTransition(javafx.util.Duration.millis(140), overlay);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
    }

    // ── Audio ───────────────────────────────────────────────────────────────

    private VBox audioSection() {
        Label h = new Label("Audio du launcher");
        h.setFont(Fonts.bold(20));
        h.setTextFill(Color.web("#F4F4F7"));

        Label p = new Label("Volume de la musique et de l'ambiance crépitement de feu jouées sur l'écran d'accueil.");
        p.setFont(Fonts.medium(13));
        p.setTextFill(Color.web("#A2A2AC"));
        p.setWrapText(true);

        Region ambientRow = volumeRow(
                "Ambiance — Feu de camp",
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

        return new VBox(14, h, p, ambientRow, musicRow);
    }

    /** A single volume row: title + description + Slider 0..100 + live %.
     *  Visually paired with the optionalModRow / memorySection styling. */
    private Region volumeRow(String title, String desc, double initial,
                             java.util.function.Consumer<Double> onChange) {
        Label modTitle = new Label(title);
        modTitle.setFont(Fonts.semi(14));
        modTitle.setTextFill(Color.web("#F4F4F7"));

        Label modDesc = new Label(desc);
        modDesc.setFont(Fonts.medium(12));
        modDesc.setTextFill(Color.web("#A2A2AC"));
        modDesc.setWrapText(true);

        Label pct = new Label(String.format("%.0f%%", initial * 100.0));
        pct.setFont(Fonts.bold(16));
        pct.setTextFill(Color.web("#F4F4F7"));
        pct.setMinWidth(56);
        pct.setAlignment(Pos.CENTER_RIGHT);

        Slider slider = new Slider(0, 100, initial * 100.0);
        slider.setBlockIncrement(1);
        slider.getStyleClass().add("audio-slider");
        slider.setPrefHeight(20);
        HBox.setHgrow(slider, Priority.ALWAYS);

        slider.valueProperty().addListener((obs, a, b) -> {
            double v = b.doubleValue() / 100.0;
            pct.setText(String.format("%.0f%%", b.doubleValue()));
            onChange.accept(v);
        });

        HBox sliderRow = new HBox(16, slider, pct);
        sliderRow.setAlignment(Pos.CENTER_LEFT);

        VBox body = new VBox(10, modTitle, modDesc, sliderRow);
        body.setPadding(new Insets(16, 20, 16, 20));
        body.getStyleClass().add("mod-row");
        return body;
    }

    // ── Lancement ───────────────────────────────────────────────────────────

    private VBox launchSection() {
        Label h = new Label("Lancement");
        h.setFont(Fonts.bold(20));
        h.setTextFill(Color.web("#F4F4F7"));

        Label p = new Label("Comportement au démarrage du jeu et accès rapide à tes fichiers.");
        p.setFont(Fonts.medium(13));
        p.setTextFill(Color.web("#A2A2AC"));
        p.setWrapText(true);

        // Toggle « fermer le launcher au lancement » — même style pilule que les mods optionnels.
        HBox closeRow = optionalModRow(
                "Fermer le launcher au lancement du jeu",
                "Le launcher se ferme automatiquement une fois Minecraft démarré.",
                Settings.get().closeOnLaunch,
                v -> { Settings.get().closeOnLaunch = v; Settings.get().save(); });

        Button openFiles = folderButton("Ouvrir le dossier du jeu",
                () -> openInExplorer(fr.nylerp.launcher.config.AppPaths.gameDir().toFile()));
        HBox openRow = new HBox(openFiles);
        openRow.setAlignment(Pos.CENTER_LEFT);
        openRow.setPadding(new Insets(2, 0, 0, 0));

        // Textures légères (owner 23/07) : pack ÷2 Decocraft/Yuushya/PointBlank pour les GPU
        // anciens dont l'atlas dépasse la limite matérielle (crash/chargement infini au lancement).
        HBox lightRow = optionalModRow(
                "Textures légères (PC modestes / GPU anciens)",
                "Réduit la résolution des textures Decocraft, Yuushya et PointBlank. Coche cette option si le jeu "
                        + "crash au chargement ou charge très lentement — aucun contenu n'est retiré.",
                fr.nylerp.launcher.util.LightTextures.isEnabled(),
                v -> fr.nylerp.launcher.util.LightTextures.setEnabled(v));

        return new VBox(14, h, p, closeRow, lightRow, openRow);
    }

    /** A ghost button with a folder icon + label that opens a folder. Reused for the game + mods folders. */
    private Button folderButton(String label, Runnable action) {
        Button b = new Button(label);
        b.getStyleClass().add("btn-ghost");
        b.setFont(Fonts.semi(14));
        b.setGraphic(Icons.folder(15, Color.web("#F4F4F7")));
        b.setGraphicTextGap(9);
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
        Label h = new Label("Mods optionnels");
        h.setFont(Fonts.bold(20));
        h.setTextFill(Color.web("#F4F4F7"));

        Label p = new Label("Mods que tu peux activer en plus du modpack. Ils seront téléchargés au prochain lancement.");
        p.setFont(Fonts.medium(13));
        p.setTextFill(Color.web("#A2A2AC"));
        p.setWrapText(true);

        HBox litematicaRow = optionalModRow(
                "Litematica",
                "Prévisualisation et construction assistée par schémas.",
                Settings.get().optionalLitematica,
                v -> { Settings.get().optionalLitematica = v; Settings.get().save(); });

        HBox irisRow = optionalModRow(
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
                "Tes mains deviennent visibles avec l'objet tenu, et les coups, les déplacements et les interactions sont animés. Purement visuel — ni les dégâts ni le recul ne changent. Réglable en jeu.",
                Settings.get().optionalPunchy,
                v -> { Settings.get().optionalPunchy = v; Settings.get().save(); });

        Button openMods = folderButton("Ouvrir le dossier des mods",
                () -> openInExplorer(fr.nylerp.launcher.config.AppPaths.gameDir().resolve("mods").toFile()));
        HBox openModsRow = new HBox(openMods);
        openModsRow.setAlignment(Pos.CENTER_LEFT);
        openModsRow.setPadding(new Insets(6, 0, 0, 0));

        return new VBox(14, h, p, litematicaRow, irisRow, dhRow, punchyRow, openModsRow);
    }

    /** A single optional-mod row: title + description on the left, pill switch
     *  on the right. Centralised so each mod is one declarative call site
     *  instead of 18 lines of repeated layout. */
    private HBox optionalModRow(String title, String desc, boolean initial,
                                java.util.function.Consumer<Boolean> onChange) {
        return optionalModRow(title, desc, initial, onChange, false);
    }

    private HBox optionalModRow(String title, String desc, boolean initial,
                                java.util.function.Consumer<Boolean> onChange, boolean disabled) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("mod-row");
        row.setPadding(new Insets(16, 20, 16, 20));

        Label modTitle = new Label(title);
        modTitle.setFont(Fonts.semi(14));
        modTitle.setTextFill(Color.web(disabled ? "#6A6A72" : "#F4F4F7"));
        Label modDesc = new Label(desc);
        modDesc.setFont(Fonts.medium(12));
        modDesc.setTextFill(Color.web("#A2A2AC"));
        modDesc.setWrapText(true);
        VBox info = new VBox(4, modTitle, modDesc);
        HBox.setHgrow(info, Priority.ALWAYS);

        CheckBox toggle = new CheckBox();
        toggle.getStyleClass().add("pill-switch");
        toggle.setSelected(initial && !disabled);
        if (disabled) {
            toggle.setDisable(true);
            toggle.setOpacity(0.4);
        } else {
            toggle.selectedProperty().addListener((obs, a, b) -> onChange.accept(b));
        }

        row.getChildren().addAll(info, toggle);
        return row;
    }

    // ── Maintenance : Réparer + Désinstaller (owner 2026-07-16) ─────────────

    private VBox maintenanceSection() {
        Label h = new Label("Maintenance");
        h.setFont(Fonts.bold(20));
        h.setTextFill(Color.web("#F4F4F7"));
        Label sub = new Label("Répare une installation cassée, ou supprime le launcher et tous les fichiers du jeu.");
        sub.setFont(Fonts.regular(13));
        sub.setTextFill(Color.web("#A2A2AC"));

        // ── RÉPARER : purge les caches de vérification puis rejoue le sync complet du pack
        //    (re-hash + re-téléchargement de tout fichier manquant/altéré) + mods optionnels +
        //    servers.dat. Exactement le remède aux installs cassées (mods corrompus/dépareillés).
        Button repair = new Button("Réparer le jeu et le launcher");
        repair.getStyleClass().add("btn-ghost");
        repair.setFont(Fonts.semi(13));
        Label repairStatus = new Label("");
        repairStatus.setFont(Fonts.regular(12));
        repairStatus.setTextFill(Color.web("#A2A2AC"));
        repair.setOnAction(e -> runRepair(repair, repairStatus));
        Label repairHint = new Label("Re-vérifie chaque fichier du pack et retélécharge ce qui est manquant ou abîmé. Tes réglages, mondes et captures sont conservés.");
        repairHint.setFont(Fonts.regular(12));
        repairHint.setTextFill(Color.web("#6A6A74"));
        repairHint.setWrapText(true);

        // ── DÉSINSTALLER : DOUBLE confirmation — armement du bouton (rouge) PUIS panneau de
        //    confirmation explicite par-dessus la page. Supprime les données du jeu + du launcher
        //    et ouvre l'outil de désinstallation de l'OS.
        Button uninstall = new Button("Désinstaller le launcher…");
        uninstall.getStyleClass().add("btn-ghost");
        uninstall.setFont(Fonts.semi(13));
        uninstall.setStyle("-fx-text-fill: #FF5C5C; -fx-border-color: rgba(255,92,92,0.35);");
        final boolean[] armed = {false};
        uninstall.setOnAction(e -> {
            if (!armed[0]) {
                armed[0] = true;
                uninstall.setText("Confirmer la désinstallation ?");
                uninstall.setStyle("-fx-text-fill: #FFFFFF; -fx-background-color: #B03030; -fx-border-color: #FF5C5C;");
                // désarmement auto après 5 s sans second clic
                javafx.animation.PauseTransition pt = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(5));
                pt.setOnFinished(ev -> {
                    armed[0] = false;
                    uninstall.setText("Désinstaller le launcher…");
                    uninstall.setStyle("-fx-text-fill: #FF5C5C; -fx-border-color: rgba(255,92,92,0.35);");
                });
                pt.play();
            } else {
                showUninstallConfirm();   // 2ᵉ confirmation : panneau modal explicite
            }
        });
        Label unHint = new Label("Supprime le launcher, le jeu, les mods, les réglages et les comptes de cet ordinateur. Irréversible.");
        unHint.setFont(Fonts.regular(12));
        unHint.setTextFill(Color.web("#6A6A74"));
        unHint.setWrapText(true);

        VBox box = new VBox(12, h, sub,
                new VBox(6, repair, repairHint, repairStatus),
                new VBox(6, uninstall, unHint));
        box.setSpacing(16);
        return box;
    }

    /** Réparation asynchrone : purge hashcache + manifest + record des mods managés, puis sync
     *  complet (chaque fichier re-hashé / re-téléchargé), mods optionnels, servers.dat. */
    private void runRepair(Button btn, Label status) {
        btn.setDisable(true);
        status.setText("Réparation en cours — vérification de chaque fichier…");
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
                result = "Réparation terminée — le jeu est prêt.";
            } catch (Throwable ex) {
                result = "Réparation incomplète : " + ex.getMessage() + " — réessaie ou vérifie ta connexion.";
            }
            String finalResult = result;
            Platform.runLater(() -> { status.setText(finalResult); btn.setDisable(false); });
        }, "nyle-repair");
        t.setDaemon(true);
        t.start();
    }

    /** 2ᵉ étape de la désinstallation : panneau modal par-dessus la page, choix explicite. */
    private void showUninstallConfirm() {
        VBox card = new VBox(14);
        card.setMaxWidth(460);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.setPadding(new Insets(26));
        card.setStyle("-fx-background-color: #101014; -fx-background-radius: 14;"
                + " -fx-border-color: rgba(255,92,92,0.45); -fx-border-radius: 14; -fx-border-width: 1;");
        Label t = new Label("Tout supprimer ?");
        t.setFont(Fonts.bold(18));
        t.setTextFill(Color.web("#F4F4F7"));
        Label d = new Label("Le launcher, le jeu, les mods, les réglages et les comptes enregistrés seront "
                + "supprimés de cet ordinateur. Cette action est irréversible.");
        d.setFont(Fonts.regular(13));
        d.setTextFill(Color.web("#A2A2AC"));
        d.setWrapText(true);

        Button cancel = new Button("Annuler");
        cancel.getStyleClass().add("btn-ghost");
        cancel.setFont(Fonts.semi(13));
        Button confirm = new Button("Tout supprimer");
        confirm.setFont(Fonts.semi(13));
        confirm.setStyle("-fx-background-color: #B03030; -fx-text-fill: white; -fx-background-radius: 10;"
                + " -fx-padding: 8 18 8 18; -fx-cursor: hand;");
        HBox btns = new HBox(10, cancel, confirm);
        btns.setAlignment(Pos.CENTER_RIGHT);
        card.getChildren().addAll(t, d, btns);

        StackPane veil = new StackPane(card);
        veil.setStyle("-fx-background-color: rgba(0,0,0,0.72);");
        veil.setAlignment(Pos.CENTER);
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

    /** Supprime les données (best effort) puis ouvre l'outil de désinstallation de l'OS et quitte. */
    private void performUninstall() {
        deleteRecursive(fr.nylerp.launcher.config.AppPaths.rootDir());   // game/ state/ minecraft/ runtime/
        deleteRecursive(payloadCacheDir());                              // cache payload du bootstrap (NyleRP)
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                // Panneau « Applications installées » — l'utilisateur clique Désinstaller NyleLauncher.
                Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "ms-settings:appsfeatures"});
            } else if (os.contains("mac")) {
                // Révèle l'app dans le Finder — glisser à la corbeille termine la désinstallation.
                Runtime.getRuntime().exec(new String[]{"open", "-R", "/Applications/NyleLauncher.app"});
            }
        } catch (Throwable ignored) { }
        Platform.runLater(Platform::exit);
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

    /** Suppression récursive best-effort (les fichiers verrouillés — ex. le jar payload en cours
     *  d'exécution sous Windows — sont simplement laissés ; quelques Ko sans conséquence). */
    private static void deleteRecursive(java.nio.file.Path root) {
        try (var walk = java.nio.file.Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { java.nio.file.Files.deleteIfExists(p); } catch (Throwable ignored) { }
            });
        } catch (Throwable ignored) { }
    }

    // ── À propos ────────────────────────────────────────────────────────────

    private VBox aboutSection() {
        Label h = new Label("À propos");
        h.setFont(Fonts.bold(20));
        h.setTextFill(Color.web("#F4F4F7"));

        GridPane g = new GridPane();
        g.setHgap(40); g.setVgap(12);
        g.add(kvColumn("VERSION", fr.nylerp.launcher.update.SelfUpdater.installedVersion()), 0, 0);
        g.add(kvColumn("PAYLOAD", Constants.runningPayloadVersion()), 1, 0);
        g.add(kvColumn("SERVEUR", Constants.SERVER_HOST), 2, 0);
        g.add(kvColumn("LOADER", "Fabric"), 3, 0);
        g.add(kvColumn("MC", Constants.MC_VERSION), 4, 0);
        return new VBox(14, h, g);
    }

    private VBox kvColumn(String k, String v) {
        Label key = new Label(k);
        key.setFont(Fonts.black(10));
        key.setTextFill(Color.web("#6A6A74"));
        key.setStyle("-fx-letter-spacing: 0.20em;");
        Label val = new Label(v);
        val.setFont(Fonts.semi(14));
        val.setTextFill(Color.web("#F4F4F7"));
        return new VBox(6, key, val);
    }
}
