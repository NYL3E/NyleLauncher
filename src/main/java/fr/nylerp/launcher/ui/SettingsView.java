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

    private static final String[] NAV = {"Mémoire", "Audio", "Lancement", "Mods optionnels", "Captures d'écran", "À propos"};
    private static final int SCREENSHOTS_IDX = 4;          // position de « Captures d'écran » dans NAV
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

    /** Left nav rail + ONE focused section at a time (instead of one long scroll) — clearer,
     *  calmer navigation. Each rail item swaps the content host with a soft fade; the active
     *  item is highlighted with an accent bar. All the original sections/options are preserved. */
    private Region buildBody() {
        Region[] secs = { memorySection(), audioSection(), launchSection(), modsSection(), screenshotsSection(), aboutSection() };
        java.util.List<Region> wraps = new java.util.ArrayList<>();
        for (Region s : secs) {
            VBox w = new VBox(s);
            w.setPadding(new Insets(34, 56, 48, 46));
            w.setMaxWidth(780);
            wraps.add(w);
        }

        contentHost = new StackPane();
        contentHost.setAlignment(Pos.TOP_LEFT);

        VBox nav = new VBox(4);
        nav.getStyleClass().add("settings-nav");
        nav.setPadding(new Insets(30, 14, 30, 22));
        nav.setMinWidth(214);
        nav.setPrefWidth(214);

        java.util.List<Button> items = new java.util.ArrayList<>();
        for (int i = 0; i < NAV.length; i++) {
            final int idx = i;
            Button it = new Button(NAV[i]);
            it.getStyleClass().add("settings-nav-item");
            it.setMaxWidth(Double.MAX_VALUE);
            it.setAlignment(Pos.CENTER_LEFT);
            it.setFont(Fonts.semi(14));
            it.setOnAction(e -> selectSection(idx, items, wraps));
            items.add(it);
            nav.getChildren().add(it);
        }

        ScrollPane sp = new ScrollPane(contentHost);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.getStyleClass().add("settings-scroll");
        sp.setPannable(true);
        HBox.setHgrow(sp, Priority.ALWAYS);

        Region divider = new Region();
        divider.getStyleClass().add("settings-nav-divider");
        divider.setMinWidth(1);
        divider.setMaxWidth(1);

        HBox body = new HBox(nav, divider, sp);
        body.getStyleClass().add("settings-body");

        selectSection(0, items, wraps);
        // Hôte qui empile le corps + une éventuelle lightbox d'aperçu de capture par-dessus.
        bodyStack = new StackPane(body);
        return bodyStack;
    }

    private void selectSection(int idx, java.util.List<Button> items, java.util.List<Region> wraps) {
        for (int i = 0; i < items.size(); i++) {
            items.get(i).getStyleClass().remove("active");
            if (i == idx) items.get(i).getStyleClass().add("active");
        }
        if (idx == SCREENSHOTS_IDX) reloadScreenshots();   // re-scanne le dossier à chaque ouverture
        Region w = wraps.get(idx);
        contentHost.getChildren().setAll(w);
        w.setOpacity(0);
        javafx.animation.FadeTransition ft =
                new javafx.animation.FadeTransition(javafx.util.Duration.millis(170), w);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
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

        return new VBox(14, h, p, closeRow, openRow);
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

        Button openMods = folderButton("Ouvrir le dossier des mods",
                () -> openInExplorer(fr.nylerp.launcher.config.AppPaths.gameDir().resolve("mods").toFile()));
        HBox openModsRow = new HBox(openMods);
        openModsRow.setAlignment(Pos.CENTER_LEFT);
        openModsRow.setPadding(new Insets(6, 0, 0, 0));

        return new VBox(14, h, p, litematicaRow, irisRow, dhRow, openModsRow);
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

    // ── À propos ────────────────────────────────────────────────────────────

    private VBox aboutSection() {
        Label h = new Label("À propos");
        h.setFont(Fonts.bold(20));
        h.setTextFill(Color.web("#F4F4F7"));

        GridPane g = new GridPane();
        g.setHgap(40); g.setVgap(12);
        g.add(kvColumn("VERSION", fr.nylerp.launcher.update.SelfUpdater.installedVersion()), 0, 0);
        g.add(kvColumn("SERVEUR", Constants.SERVER_HOST), 1, 0);
        g.add(kvColumn("LOADER", "Fabric"), 2, 0);
        g.add(kvColumn("MC", Constants.MC_VERSION), 3, 0);
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
