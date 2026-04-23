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

    public SettingsView(Runnable onBack) {
        getStyleClass().add("main-root");
        setTop(buildTopBar(onBack));
        setCenter(buildScroll());
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

    private Region buildScroll() {
        VBox col = new VBox(36);
        col.setPadding(new Insets(32, 56, 48, 56));
        col.setMaxWidth(720);
        col.getChildren().addAll(
                memorySection(),
                modsSection(),
                aboutSection()
        );

        ScrollPane sp = new ScrollPane(col);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.getStyleClass().add("settings-scroll");
        sp.setPannable(true);
        return sp;
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

    // ── Mods optionnels ─────────────────────────────────────────────────────

    private VBox modsSection() {
        Label h = new Label("Mods optionnels");
        h.setFont(Fonts.bold(20));
        h.setTextFill(Color.web("#F4F4F7"));

        Label p = new Label("Mods que tu peux activer en plus du modpack. Ils seront téléchargés au prochain lancement.");
        p.setFont(Fonts.medium(13));
        p.setTextFill(Color.web("#A2A2AC"));
        p.setWrapText(true);

        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("mod-row");
        row.setPadding(new Insets(16, 20, 16, 20));

        Label modTitle = new Label("Litematica");
        modTitle.setFont(Fonts.semi(14));
        modTitle.setTextFill(Color.web("#F4F4F7"));
        Label modDesc = new Label("Prévisualisation et construction assistée par schémas.");
        modDesc.setFont(Fonts.medium(12));
        modDesc.setTextFill(Color.web("#A2A2AC"));
        VBox info = new VBox(4, modTitle, modDesc);
        HBox.setHgrow(info, Priority.ALWAYS);

        CheckBox toggle = new CheckBox();
        toggle.getStyleClass().add("pill-switch");
        toggle.setSelected(Settings.get().optionalLitematica);
        toggle.selectedProperty().addListener((obs, a, b) -> {
            Settings.get().optionalLitematica = b;
            Settings.get().save();
        });

        row.getChildren().addAll(info, toggle);
        return new VBox(14, h, p, row);
    }

    // ── À propos ────────────────────────────────────────────────────────────

    private VBox aboutSection() {
        Label h = new Label("À propos");
        h.setFont(Fonts.bold(20));
        h.setTextFill(Color.web("#F4F4F7"));

        GridPane g = new GridPane();
        g.setHgap(40); g.setVgap(12);
        g.add(kvColumn("VERSION", Constants.APP_VERSION), 0, 0);
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
