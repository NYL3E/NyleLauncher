package fr.nylerp.launcher.ui;

import fr.nylerp.launcher.config.Constants;
import fr.nylerp.launcher.config.Settings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

public class SettingsView extends BorderPane {

    public SettingsView(Runnable onBack) {
        getStyleClass().add("main-root");
        setTop(buildTopBar(onBack));
        setCenter(buildContent());
    }

    private Region buildTopBar(Runnable onBack) {
        Button back = new Button();
        back.getStyleClass().add("icon-btn");
        back.setGraphic(Icons.arrowLeft(18, Color.web("#9B9BA5")));
        back.setOnAction(e -> onBack.run());

        Label title = new Label("PARAMÈTRES");
        title.getStyleClass().add("brand");

        HBox bar = new HBox(14, back, title);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 24, 0, 20));
        bar.setPrefHeight(64);
        bar.getStyleClass().add("top-bar");
        return bar;
    }

    private Region buildContent() {
        VBox col = new VBox(56);
        col.setPadding(new Insets(48, 56, 48, 56));
        col.setMaxWidth(640);

        col.getChildren().addAll(
                memorySection(),
                modsSection(),
                aboutSection()
        );
        VBox outer = new VBox(col);
        outer.setAlignment(Pos.TOP_LEFT);
        return outer;
    }

    // ── Mémoire ─────────────────────────────────────────────────────────────

    private VBox memorySection() {
        Label h  = new Label("Mémoire");       h.getStyleClass().add("settings-h");
        Label p  = new Label("Quantité de RAM allouée à Minecraft. 4 Go suffit à 95 % des joueurs.");
        p.getStyleClass().add("settings-p");
        p.setWrapText(true);

        int startGb = Math.max(2, Math.min(16, Settings.get().ramMb / 1024));

        Label big    = new Label(String.valueOf(startGb));
        big.getStyleClass().add("ram-huge-num");
        Label unit   = new Label("Go");
        unit.getStyleClass().add("ram-huge-unit");
        Label max    = new Label("/ 16 Go");
        max.getStyleClass().add("ram-max");

        HBox header = new HBox(6, big, unit, spacer(8), max);
        header.setAlignment(Pos.BASELINE_LEFT);

        Slider slider = new Slider(2, 16, startGb);
        slider.setBlockIncrement(1);
        slider.setMajorTickUnit(2);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.getStyleClass().add("ram-slider-big");
        slider.setPrefHeight(22);
        slider.setMaxWidth(Double.MAX_VALUE);

        slider.valueProperty().addListener((obs, a, b) -> {
            int g = b.intValue();
            big.setText(String.valueOf(g));
            Settings.get().ramMb = g * 1024;
            Settings.get().save();
        });

        HBox scale = new HBox();
        scale.setAlignment(Pos.CENTER);
        for (int v = 2; v <= 16; v += 2) {
            Label t = new Label(String.valueOf(v));
            t.getStyleClass().add("ram-tick");
            HBox.setHgrow(t, Priority.ALWAYS);
            t.setMaxWidth(Double.MAX_VALUE);
            t.setAlignment(Pos.CENTER);
            scale.getChildren().add(t);
        }

        VBox v = new VBox(18, h, p, header, slider, scale);
        return v;
    }

    // ── Mods optionnels ─────────────────────────────────────────────────────

    private VBox modsSection() {
        Label h = new Label("Mods optionnels"); h.getStyleClass().add("settings-h");
        Label p = new Label("Mods que tu peux activer en plus du modpack. Ils seront téléchargés au prochain lancement.");
        p.getStyleClass().add("settings-p");
        p.setWrapText(true);

        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("mod-row");
        row.setPadding(new Insets(16, 20, 16, 20));

        VBox info = new VBox(4,
                labelCls("Litematica", "mod-title"),
                labelCls("Prévisualisation et construction assistée par schémas.", "mod-desc"));
        HBox.setHgrow(info, Priority.ALWAYS);

        CheckBox toggle = new CheckBox();
        toggle.getStyleClass().add("pill-switch");
        toggle.setSelected(Settings.get().optionalLitematica);
        toggle.selectedProperty().addListener((obs, a, b) -> {
            Settings.get().optionalLitematica = b;
            Settings.get().save();
        });

        row.getChildren().addAll(info, toggle);
        return new VBox(18, h, p, row);
    }

    // ── About ───────────────────────────────────────────────────────────────

    private VBox aboutSection() {
        Label h = new Label("À propos"); h.getStyleClass().add("settings-h");
        GridPane g = new GridPane();
        g.setHgap(40); g.setVgap(12);
        g.add(kvColumn("VERSION", "0.1.0"), 0, 0);
        g.add(kvColumn("SERVEUR", Constants.SERVER_HOST), 1, 0);
        g.add(kvColumn("LOADER", "Fabric 0.16.5"), 2, 0);
        g.add(kvColumn("MC", Constants.MC_VERSION), 3, 0);
        return new VBox(18, h, g);
    }

    private VBox kvColumn(String k, String v) {
        Label key = labelCls(k, "micro");
        Label val = labelCls(v, "about-v");
        return new VBox(6, key, val);
    }

    private static Label labelCls(String text, String cls) {
        Label l = new Label(text);
        l.getStyleClass().add(cls);
        return l;
    }

    private static Region spacer(double w) {
        Region r = new Region();
        r.setMinWidth(w); r.setPrefWidth(w); r.setMaxWidth(w);
        return r;
    }
}
