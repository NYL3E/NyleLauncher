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
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class SettingsView extends BorderPane {

    private static final String[] NAV = {"Mémoire", "Audio", "Lancement", "Mods optionnels", "À propos"};
    private StackPane contentHost;

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
        Region[] secs = { memorySection(), audioSection(), launchSection(), modsSection(), aboutSection() };
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
        return body;
    }

    private void selectSection(int idx, java.util.List<Button> items, java.util.List<Region> wraps) {
        for (int i = 0; i < items.size(); i++) {
            items.get(i).getStyleClass().remove("active");
            if (i == idx) items.get(i).getStyleClass().add("active");
        }
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

        Runnable reposition = () -> {
            double sliderW = slider.getWidth();
            if (sliderW <= 0) return;
            double numW = numGroup.getWidth();
            double ratio = (slider.getValue() - 2) / 14.0;
            // Thumb center travels from THUMB_R to sliderW - THUMB_R
            double thumbX = THUMB_R + ratio * (sliderW - 2 * THUMB_R);
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

        return new VBox(10, h, p, maxRow, numLane, slider, scale);
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

        Button openFiles = new Button("Ouvrir le dossier du jeu");
        openFiles.getStyleClass().add("btn-ghost");
        openFiles.setFont(Fonts.semi(14));
        openFiles.setOnAction(e -> openGameFolder());
        HBox openRow = new HBox(openFiles);
        openRow.setAlignment(Pos.CENTER_LEFT);
        openRow.setPadding(new Insets(2, 0, 0, 0));

        return new VBox(14, h, p, closeRow, openRow);
    }

    /** Ouvre le dossier du jeu (.../game) dans l'explorateur de l'OS. Sur un thread à part et
     *  totalement try/catch : ouvrir un dossier ne doit JAMAIS faire planter le launcher. */
    private void openGameFolder() {
        new Thread(() -> {
            java.io.File dir = fr.nylerp.launcher.config.AppPaths.gameDir().toFile();
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

        return new VBox(14, h, p, litematicaRow, irisRow, dhRow);
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
