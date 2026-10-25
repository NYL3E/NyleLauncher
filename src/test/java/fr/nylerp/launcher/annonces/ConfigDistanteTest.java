package fr.nylerp.launcher.annonces;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Config distante : lecture tolérante, ETag/304, copie locale hors ligne, rappels de changement. */
@Timeout(60)
class ConfigDistanteTest {

    private static final String CONFIG = """
            { "v": 1, "maintenant": "2026-10-16T14:33:19+02:00", "maj": "2026-10-16T14:28:27+02:00",
              "config": { "pokenyleOuverture": "2026-12-14T18:00:00+01:00", "pokenyleOuvertureLibelle": "mi-décembre",
                "pokenyleOuvert": false, "pokenyleCrack": false, "messageCrackPokenyle": "Bientôt pour les cracks.",
                "discordActif": true, "discordUrl": "https://discord.gg/nyle", "testeursPokenyle": ["nyl3e", "mrcedriic"],
                "cleInconnue": 42 } }
            """;

    private FauxServeur serveur;
    private Path dossier;

    @BeforeEach
    void preparer() throws Exception {
        serveur = new FauxServeur();
        dossier = Files.createTempDirectory("nyle-config");
    }

    @AfterEach
    void fermer() { serveur.close(); }

    private ConfigDistante nouvelle(URI uri) {
        return new ConfigDistante(new PointAcces("config", uri, new ClientLecture(),
                new CopieLocale(dossier.resolve("config-cache.json")), new HorlogeServeur()));
    }

    @Test
    void sansReseauNiCopieLesDefautsDecriventUnPokenylePasOuvert() {
        ConfigDistante c = nouvelle(URI.create("http://127.0.0.1:1/api/launcher/config"));
        assertFalse(c.rafraichir());
        assertFalse(c.ouvertPour("quelquun"));
        assertTrue(c.ouvertPour("NYL3E"), "repli : la liste intégrée des testeurs");
        assertTrue(c.ouvertPour("Khuunaa"), "repli : khuunaa fait partie de la liste intégrée");
        assertFalse(c.ouvertPour("Khuunaa_"), "repli : un pseudo voisin n'y est pas");
        assertTrue(c.ouverture().isEmpty());
        assertEquals("mi-décembre", c.libelleOuverture());
        assertFalse(c.crackAutorise());
        assertFalse(c.discordActif());
        assertEquals("https://discord.gg/nyle", c.discordUrl());
    }

    @Test
    void litLaConfigEtLesTesteursSansTenirCompteDeLaCasse() {
        serveur.pour("config").corps = CONFIG;
        ConfigDistante c = nouvelle(serveur.uri("config"));
        assertTrue(c.rafraichir());
        assertTrue(c.ouvertPour("MrCedriic "));
        assertFalse(c.ouvertPour("fell4eli"), "la liste distante remplace le repli");
        assertEquals(ZonedDateTime.of(2026, 12, 14, 18, 0, 0, 0, ZoneId.of("Europe/Paris")), c.ouverture().orElseThrow());
        assertEquals("Bientôt pour les cracks.", c.messageCrack());
        assertTrue(c.discordActif());
    }

    @Test
    void ouvertATousQuandPokenyleOuvert() {
        serveur.pour("config").corps = CONFIG.replace("\"pokenyleOuvert\": false", "\"pokenyleOuvert\": true");
        ConfigDistante c = nouvelle(serveur.uri("config"));
        c.rafraichir();
        assertTrue(c.ouvertPour("nimporte"));
        assertTrue(c.ouvertPour(null), "ouvert à tous, même sans pseudo");
    }

    @Test
    void valeursInvalidesRetombentUneAUneSurLeDefaut() {
        serveur.pour("config").corps = """
                { "v": 1, "config": { "pokenyleOuverture": "pas une date", "pokenyleOuvert": "oui",
                  "pokenyleCrack": true, "discordActif": true, "discordUrl": "http://pirate.example/x",
                  "pokenyleOuvertureLibelle": "  ", "testeursPokenyle": "nyl3e" } }
                """;
        ConfigDistante c = nouvelle(serveur.uri("config"));
        assertTrue(c.rafraichir());
        assertTrue(c.ouverture().isEmpty());
        assertFalse(c.ouvertPour("x"), "booléen au mauvais type → défaut false");
        assertTrue(c.crackAutorise(), "les clés valides passent quand même");
        assertEquals("https://discord.gg/nyle", c.discordUrl(), "lien non Discord refusé");
        assertEquals("mi-décembre", c.libelleOuverture());
        assertTrue(c.ouvertPour("nyl3e"), "liste invalide → repli intégré");
    }

    @Test
    void etag304NeChangeRienEtNeRappellePas() {
        FauxServeur.Reponse r = serveur.pour("config");
        r.corps = CONFIG;
        r.etag = "\"abc123\"";
        ConfigDistante c = nouvelle(serveur.uri("config"));
        AtomicInteger rappels = new AtomicInteger();
        c.surChangement(rappels::incrementAndGet);
        assertTrue(c.rafraichir());
        assertEquals(1, rappels.get(), "premier état lu = changement depuis les défauts");
        assertTrue(c.rafraichir());
        assertTrue(c.rafraichir());
        assertEquals(2, serveur.nonModifies("config"), "If-None-Match envoyé, 304 reçu");
        assertEquals(1, rappels.get(), "un 304 ne déclenche aucun rappel");
        r.corps = CONFIG.replace("\"discordActif\": true", "\"discordActif\": false");
        r.etag = "\"def456\"";
        assertTrue(c.rafraichir());
        assertFalse(c.discordActif());
        assertEquals(2, rappels.get(), "vrai changement → un rappel");
    }

    @Test
    void horsLigneLaCopieLocaleEstRelue() {
        FauxServeur.Reponse r = serveur.pour("config");
        r.corps = CONFIG;
        r.etag = "\"abc123\"";
        nouvelle(serveur.uri("config")).rafraichir();
        assertTrue(Files.exists(dossier.resolve("config-cache.json")));
        serveur.close();
        ConfigDistante horsLigne = nouvelle(serveur.uri("config"));
        assertTrue(horsLigne.discordActif(), "copie lue dès la construction, avant tout réseau");
        assertFalse(horsLigne.rafraichir());
        assertTrue(horsLigne.ouvertPour("mrcedriic"), "l'échec réseau garde la copie");
    }

    @Test
    void erreur502GardeLaDerniereCopieValide() {
        FauxServeur.Reponse r = serveur.pour("config");
        r.corps = CONFIG;
        ConfigDistante c = nouvelle(serveur.uri("config"));
        c.rafraichir();
        r.statut = 502;
        r.corps = "{\"erreur\":\"source indisponible\"}";
        assertFalse(c.rafraichir());
        assertTrue(c.discordActif());
        r.statut = 200;
        r.corps = "pas du json";
        assertFalse(c.rafraichir(), "corps illisible = échec");
        assertTrue(c.discordActif());
    }
}
