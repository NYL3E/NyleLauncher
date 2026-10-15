package fr.nylerp.launcher.update;

import com.sun.net.httpserver.HttpServer;
import fr.nylerp.launcher.config.ModeDeJeu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Shaders par défaut du mode pokényle (15/10/2026) : réglages Iris UNE SEULE FOIS par instance,
 * jamais par-dessus un choix du joueur, et le zip Complementary posé sans jamais toucher aux
 * packs du joueur. Tout se passe dans un dossier temporaire.
 */
class ShaderDefaultsTest {

    private Path game;
    private HttpServer server;

    @BeforeEach
    void avant() throws Exception {
        game = Files.createTempDirectory("nyle-shaders-test");
    }

    @AfterEach
    void apres() {
        if (server != null) server.stop(0);
    }

    private Path props() { return game.resolve("config/iris.properties"); }
    private Path txt() { return game.resolve("shaderpacks/" + ShaderDefaults.PACK_FILE + ".txt"); }
    private Path marker() { return game.resolve(ShaderDefaults.MARKER); }

    private Properties lire() throws Exception {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(props())) { p.load(in); }
        return p;
    }

    private void ecrire(String contenu) throws Exception {
        Files.createDirectories(props().getParent());
        Files.writeString(props(), contenu, StandardCharsets.ISO_8859_1);
    }

    // ── Réglages Iris ───────────────────────────────────────────────────────────────────────

    @Test
    void fichierAbsent_packChoisiProfilEtMarqueur() throws Exception {
        ShaderDefaults.applyIrisDefaultsOnce(game, ModeDeJeu.POKENYLE);
        Properties p = lire();
        assertEquals(ShaderDefaults.PACK_FILE, p.getProperty("shaderPack"));
        assertEquals("true", p.getProperty("enableShaders"));
        assertEquals("true", p.getProperty("disableUpdateMessage"));
        assertEquals(ShaderDefaults.PROFILE_TXT, Files.readString(txt(), StandardCharsets.ISO_8859_1));
        assertTrue(Files.exists(marker()));
    }

    @Test
    void profilExact() {
        assertEquals("SHADOW_QUALITY=1\nshadowDistance=96.0\nLIGHTSHAFT_QUALI_DEFINE=1\n"
                + "CLOUD_QUALITY=1\nBLOCK_REFLECT_QUALITY=1\nENTITY_SHADOW=-1\n", ShaderDefaults.PROFILE_TXT);
    }

    /** Cas réel de l'owner (12/10) : pack vide, shaders « activés », autres clés à garder. */
    @Test
    void shaderPackVide_casDeLOwner() throws Exception {
        ecrire("#This file stores configuration options for Iris, such as the currently active shaderpack\n"
                + "allowUnknownShaders=false\ncolorSpace=SRGB\ndisableUpdateMessage=false\n"
                + "enableDebugOptions=false\nenableShaders=true\nmaxShadowRenderDistance=32\n"
                + "shaderPack=\n");
        ShaderDefaults.applyIrisDefaultsOnce(game, ModeDeJeu.POKENYLE);
        Properties p = lire();
        assertEquals(ShaderDefaults.PACK_FILE, p.getProperty("shaderPack"));
        assertEquals("true", p.getProperty("enableShaders"));
        assertEquals("true", p.getProperty("disableUpdateMessage"));
        assertEquals("false", p.getProperty("allowUnknownShaders"), "clé du joueur perdue");
        assertEquals("SRGB", p.getProperty("colorSpace"));
        assertEquals("false", p.getProperty("enableDebugOptions"));
        assertEquals("32", p.getProperty("maxShadowRenderDistance"));
        assertTrue(Files.exists(marker()));
    }

    @Test
    void cleShaderPackAbsente() throws Exception {
        ecrire("colorSpace=SRGB\n");
        ShaderDefaults.applyIrisDefaultsOnce(game, ModeDeJeu.POKENYLE);
        assertEquals(ShaderDefaults.PACK_FILE, lire().getProperty("shaderPack"));
        assertEquals("SRGB", lire().getProperty("colorSpace"));
    }

    @Test
    void autrePackChoisi_intouche() throws Exception {
        String avant = "enableShaders=true\nshaderPack=BSL_v8.4.zip\ncolorSpace=SRGB\n";
        ecrire(avant);
        ShaderDefaults.applyIrisDefaultsOnce(game, ModeDeJeu.POKENYLE);
        assertEquals(avant, Files.readString(props(), StandardCharsets.ISO_8859_1), "choix du joueur écrasé");
        assertTrue(Files.exists(marker()), "marqueur posé quand même : on ne réessaiera plus");
    }

    /** Le joueur a coupé les shaders avec K (Iris écrit enableShaders=false) : ça doit tenir. */
    @Test
    void deuxiemePassage_intoucheMemeSiShadersCoupes() throws Exception {
        ShaderDefaults.applyIrisDefaultsOnce(game, ModeDeJeu.POKENYLE);
        String coupe = "enableShaders=false\nshaderPack=\n";   // même avec un pack revenu à vide
        ecrire(coupe);
        Files.writeString(txt(), "SHADOW_QUALITY=2\n");
        ShaderDefaults.applyIrisDefaultsOnce(game, ModeDeJeu.POKENYLE);
        assertEquals(coupe, Files.readString(props(), StandardCharsets.ISO_8859_1));
        assertEquals("SHADOW_QUALITY=2\n", Files.readString(txt()));
    }

    @Test
    void txtExistant_intouche() throws Exception {
        Files.createDirectories(txt().getParent());
        Files.writeString(txt(), "SHADOW_QUALITY=3\nWATER_REFLECT_QUALITY=1\n");
        ShaderDefaults.applyIrisDefaultsOnce(game, ModeDeJeu.POKENYLE);
        assertEquals("SHADOW_QUALITY=3\nWATER_REFLECT_QUALITY=1\n", Files.readString(txt()));
        assertEquals(ShaderDefaults.PACK_FILE, lire().getProperty("shaderPack"));
    }

    @Test
    void modeNyleRP_rienDuTout() throws Exception {
        ShaderDefaults.applyIrisDefaultsOnce(game, ModeDeJeu.NYLERP);
        assertFalse(Files.exists(props()));
        assertFalse(Files.exists(txt()));
        assertFalse(Files.exists(marker()));
    }

    // ── Le zip ──────────────────────────────────────────────────────────────────────────────

    private String servir(byte[] corps) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/pack.zip", ex -> {
            ex.sendResponseHeaders(200, corps.length);
            try (var out = ex.getResponseBody()) { out.write(corps); }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/pack.zip";
    }

    @Test
    void zipAltere_refuseEtRienLaisse() throws Exception {
        Path dir = game.resolve("shaderpacks");
        assertFalse(ShaderDefaults.ensurePack(dir, servir("pas le bon zip".getBytes())));
        assertFalse(Files.exists(dir.resolve(ShaderDefaults.PACK_FILE)), "zip non vérifié installé");
        try (var l = Files.list(dir)) {
            assertEquals(0, l.count(), "fichier temporaire laissé dans shaderpacks/");
        }
    }

    @Test
    void zipDejaPresent_jamaisRemplaceNiSupprime() throws Exception {
        Path dir = Files.createDirectories(game.resolve("shaderpacks"));
        Files.writeString(dir.resolve(ShaderDefaults.PACK_FILE), "version du joueur");
        Files.writeString(dir.resolve("BSL_v8.4.zip"), "pack du joueur");
        assertTrue(ShaderDefaults.ensurePack(dir, "http://127.0.0.1:1/injoignable"));
        assertEquals("version du joueur", Files.readString(dir.resolve(ShaderDefaults.PACK_FILE)));
        assertEquals("pack du joueur", Files.readString(dir.resolve("BSL_v8.4.zip")));
    }

    @Test
    void serveurInjoignable_pasDException() throws Exception {
        Path dir = game.resolve("shaderpacks");
        assertFalse(ShaderDefaults.ensurePack(dir, "http://127.0.0.1:1/injoignable"));
        assertFalse(Files.exists(dir.resolve(ShaderDefaults.PACK_FILE)));
    }

    /** Le vrai zip r5.9.3 (copie locale de l'enquête, jamais versionnée : licence). */
    @Test
    void vraiZip_installeIntact() throws Exception {
        String src = System.getenv("NYLE_COMPLEMENTARY_ZIP");
        Assumptions.assumeTrue(src != null && Files.exists(Path.of(src)), "NYLE_COMPLEMENTARY_ZIP non fourni");
        byte[] zip = Files.readAllBytes(Path.of(src));
        Path dir = game.resolve("shaderpacks");
        assertTrue(ShaderDefaults.ensurePack(dir, servir(zip)));
        Path target = dir.resolve(ShaderDefaults.PACK_FILE);
        assertEquals(ShaderDefaults.PACK_SIZE, Files.size(target));
        assertEquals(ShaderDefaults.PACK_SHA512, OptionalMods.sha512Hex(target));
    }
}
