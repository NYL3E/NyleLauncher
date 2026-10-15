package fr.nylerp.launcher.update;

import fr.nylerp.launcher.config.ModeDeJeu;
import fr.nylerp.launcher.config.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mods optionnels par mode (15/10/2026) : Iris imposé en pokényle, Controlify optionnel en
 * pokényle SEULEMENT — en mode NyleRP l'entrée n'installe ni ne supprime rien, même si un jar du
 * même nom s'y trouve. Faux dossier de mods temporaire, aucun téléchargement (les jars voulus
 * sont posés à l'avance : présent + voulu = rien à faire).
 */
class OptionalModsModeTest {

    private static final String CONTROLIFY = "controlify-3.0.1+lts+1.21.1-fabric.jar";
    private static final String IRIS = "iris-fabric-1.8.8+mc1.21.1.jar";

    private String realHome;
    private Path mods;

    @BeforeEach
    void avant() throws Exception {
        realHome = System.getProperty("user.home");
        System.setProperty("user.home", Files.createTempDirectory("nyle-optmode-home").toString());
        reset();
        mods = Files.createTempDirectory("nyle-optmode-mods");
    }

    @AfterEach
    void apres() throws Exception {
        System.setProperty("user.home", realHome);
        reset();
    }

    private static void reset() throws Exception {
        Field f = Settings.class.getDeclaredField("current");
        f.setAccessible(true);
        f.set(null, null);
    }

    private static OptionalMods.Entry entree(String nom) {
        return OptionalMods.ENTRIES.stream().filter(e -> nom.equals(e.fileName())).findFirst()
                .orElseThrow(() -> new AssertionError("pas d'entrée " + nom));
    }

    @Test
    void controlify_bienForme() {
        OptionalMods.Entry e = entree(CONTROLIFY);
        assertEquals("https://cdn.modrinth.com/data/DOUdJVEm/versions/y9bu5RxH/controlify-3.0.1%2Blts%2B1.21.1-fabric.jar", e.url());
        assertTrue(e.sha512().matches("[0-9a-f]{128}"));
        assertTrue(e.appliesTo(ModeDeJeu.POKENYLE));
        assertFalse(e.appliesTo(ModeDeJeu.NYLERP));
        assertFalse(Settings.get().optionalControlify, "défaut : coupé");
        assertFalse(e.enabled().getAsBoolean());
        Settings.get().optionalControlify = true;
        assertTrue(e.enabled().getAsBoolean());
    }

    @Test
    void entreesHistoriques_tousModes() {
        for (String nom : new String[]{IRIS, "punchy-2.6.2-fabric-1.21.1.jar",
                "litematica-fabric-1.21-0.19.60.jar", "DistantHorizons-2.4.5-b-1.21.1-fabric-neoforge.jar"}) {
            for (ModeDeJeu m : ModeDeJeu.values()) assertTrue(entree(nom).appliesTo(m), nom + " / " + m);
        }
    }

    @Test
    void iris_imposeEnPokenyleSeulement() {
        Settings s = Settings.get();
        s.optionalIris = false;
        s.optionalDistantHorizons = false;
        s.modeDeJeu = "nylerp";
        assertFalse(entree(IRIS).enabled().getAsBoolean());
        s.modeDeJeu = "pokenyle";
        assertTrue(entree(IRIS).enabled().getAsBoolean(), "Iris doit être voulu en pokényle");
        s.modeDeJeu = "nylerp";
        s.optionalIris = true;
        assertTrue(entree(IRIS).enabled().getAsBoolean());
    }

    @Test
    void pokenyle_controlifyCoupe_estSupprime_irisGarde() throws Exception {
        Settings.get().modeDeJeu = "pokenyle";
        Files.writeString(mods.resolve(CONTROLIFY), "ancien jar du pack");
        Files.writeString(mods.resolve(IRIS), "iris");
        Files.writeString(mods.resolve("cobblemon.jar"), "pack");
        OptionalMods.applyAll(mods, ModeDeJeu.POKENYLE);
        assertFalse(Files.exists(mods.resolve(CONTROLIFY)), "Controlify coupé : retiré");
        assertTrue(Files.exists(mods.resolve(IRIS)), "Iris voulu en pokényle : gardé");
        assertTrue(Files.exists(mods.resolve("cobblemon.jar")));
    }

    @Test
    void nylerp_controlifyJamaisTouche() throws Exception {
        Settings.get().modeDeJeu = "nylerp";
        Settings.get().optionalControlify = false;
        Files.writeString(mods.resolve(CONTROLIFY), "jar du pack NyleRP");
        OptionalMods.applyAll(mods, ModeDeJeu.NYLERP);
        assertEquals("jar du pack NyleRP", Files.readString(mods.resolve(CONTROLIFY)),
                "l'optionnel pokényle a supprimé un jar du pack NyleRP");
    }

    @Test
    void nylerp_controlifyActive_rienNInstalle() throws Exception {
        Settings.get().modeDeJeu = "nylerp";
        Settings.get().optionalControlify = true;   // réglage partagé entre les modes
        OptionalMods.applyAll(mods, ModeDeJeu.NYLERP);   // aucun réseau : l'entrée est sautée
        assertFalse(Files.exists(mods.resolve(CONTROLIFY)));
    }

    /** Le nettoyage du pack (ModpackUpdater) épargne les optionnels du mode courant seulement. */
    @Test
    void nomsOptionnelsParMode() {
        assertTrue(OptionalMods.filenames(ModeDeJeu.POKENYLE).contains(CONTROLIFY));
        assertFalse(OptionalMods.filenames(ModeDeJeu.NYLERP).contains(CONTROLIFY));
        assertTrue(OptionalMods.filenames(ModeDeJeu.NYLERP).contains(IRIS));
        assertTrue(OptionalMods.filenames().contains(CONTROLIFY));
    }

    @Test
    void ui_ligneManetteEtCredit() throws Exception {
        Path src = Path.of("src/main/java/fr/nylerp/launcher/ui/SettingsView.java");
        Assumptions.assumeTrue(Files.exists(src));
        String code = Files.readString(src);
        assertTrue(code.contains("\"Manette (Controlify)\""));
        assertTrue(code.contains("Settings.get().optionalControlify = v"));
        assertTrue(code.contains("box.getChildren().add(box.getChildren().indexOf(openModsRow), controlifyRow)"),
                "ligne Manette construite mais jamais ajoutée");
        assertTrue(code.contains("Shaders : Complementary Reimagined par EminGT (complementary.dev), téléchargé depuis Modrinth"));
        assertTrue(code.contains("return new VBox(14, h, g, credit);"), "crédit construit mais jamais affiché");
    }
}
