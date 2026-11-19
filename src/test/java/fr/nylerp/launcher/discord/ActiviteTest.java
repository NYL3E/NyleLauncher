package fr.nylerp.launcher.discord;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ce que voient les amis du joueur, état par état, et les limites de Discord sur chaque champ.
 * Partagé tel quel avec le mod client {@code nylepresence}.
 */
class ActiviteTest {

    private static JsonObject a(Activite.Phase p, boolean fenetre, boolean pokenyle, boolean enFond, boolean details) {
        return Activite.pour(new Activite.Situation(p, fenetre, pokenyle, 1_763_500_000L, enFond, details, null));
    }

    private static String details(JsonObject a) { return a.get("details").getAsString(); }
    private static String url(JsonObject a) { return a.getAsJsonArray("buttons").get(0).getAsJsonObject().get("url").getAsString(); }

    @Test
    void texteDeChaqueEtat() {
        JsonObject lanceur = a(Activite.Phase.LANCEUR, true, false, false, false);
        assertEquals("Dans le lanceur", details(lanceur));
        assertFalse(lanceur.has("timestamps"), "pas de chrono dans le lanceur");
        assertEquals("https://www.nylerp.fr/", url(lanceur));

        assertEquals("Mise à jour du jeu…", details(a(Activite.Phase.MISE_A_JOUR, true, true, false, false)));

        JsonObject jeu = a(Activite.Phase.EN_JEU, false, true, false, false);
        assertEquals("En jeu sur Pokényle", details(jeu));
        assertEquals(1_763_500_000L, jeu.getAsJsonObject("timestamps").get("start").getAsLong());
        assertEquals("https://pokerayou.academy/", url(jeu));
        assertEquals("En jeu sur NyleRP", details(a(Activite.Phase.EN_JEU, false, false, false, false)));
        assertEquals("Sur l'écran titre", details(a(Activite.Phase.ECRAN_TITRE, false, true, false, false)), "mod du jeu");
        assertEquals("En solo", details(a(Activite.Phase.SOLO, false, true, false, false)), "mod du jeu");

        for (JsonObject o : new JsonObject[] {lanceur, jeu}) {
            assertEquals("NyleLauncher", o.get("name").getAsString());
            assertEquals(0, o.get("type").getAsInt());
            assertEquals("logo", o.getAsJsonObject("assets").get("large_image").getAsString());
            assertFalse(o.has("status_display_type"), "par défaut, Discord montre le nom de l'application");
        }
    }

    @Test
    void launcherSeulDansLaBarreAucuneActiviteSaufConfigDistante() {
        assertNull(a(Activite.Phase.LANCEUR, false, true, false, false), "PC allumé, launcher rangé : rien");
        assertNotNull(a(Activite.Phase.LANCEUR, false, true, true, false), "discordEnFond : activité dès que le processus vit");
        assertNotNull(a(Activite.Phase.EN_JEU, false, true, false, false), "en jeu, fenêtre rangée : activité");
        assertNotNull(a(Activite.Phase.MISE_A_JOUR, false, true, false, false));
        assertNotNull(a(Activite.Phase.ECRAN_TITRE, false, true, false, false), "le jeu tourne : activité");
    }

    @Test
    void varianteStatutDetailsSeulementEnJeu() {
        JsonObject jeu = a(Activite.Phase.EN_JEU, false, true, false, true);
        assertEquals("Pokényle", details(jeu));
        assertEquals("En jeu", jeu.get("state").getAsString());
        assertEquals(2, jeu.get("status_display_type").getAsInt());
        JsonObject lanceur = a(Activite.Phase.LANCEUR, true, true, false, true);
        assertEquals("Dans le lanceur", details(lanceur));
        assertFalse(lanceur.has("status_display_type"), "dans le lanceur, « Joue à Pokényle » serait faux");
    }

    @Test
    void limitesDeDiscordRespectees() {
        for (Activite.Phase p : Activite.Phase.values()) {
            for (int k = 0; k < 4; k++) {
                JsonObject o = a(p, true, (k & 1) == 1, false, (k & 2) == 2);
                int d = details(o).length();
                assertTrue(d >= 2 && d <= 128, "details de 2 à 128 caractères");
                var boutons = o.getAsJsonArray("buttons");
                assertTrue(boutons.size() <= 2);
                String label = boutons.get(0).getAsJsonObject().get("label").getAsString();
                assertTrue(!label.isEmpty() && label.length() <= 32, "libellé de bouton de 1 à 32 caractères");
                assertTrue(url(o).startsWith("https://") && url(o).length() <= 512);
                String texte = o.toString().toLowerCase();
                assertFalse(texte.contains("crack") || texte.contains("discord.gg"), "règles de l'owner");
            }
        }
    }

    @Test
    void imageDeLaConfigCleOuAdresseHttpsSinonLogo() {
        java.util.function.Function<String, String> image = v -> Activite.pour(new Activite.Situation(Activite.Phase.EN_JEU,
                false, true, 1L, false, false, v)).getAsJsonObject("assets").get("large_image").getAsString();
        assertEquals("logo", image.apply(null), "absente : la clé logo");
        assertEquals("logo_pokenyle", image.apply(" logo_pokenyle "), "clé simple du portail");
        String url = "https://nyle-mc-server.pages.dev/launcher/discord-logo.png";
        assertEquals(url, image.apply(url), "adresse https publique");
        for (String mauvais : new String[] {"", "   ", "http://nyle-mc-server.pages.dev/logo.png", "javascript:alert(1)",
                "https://", "https://a b.png", "https://x.fr/\"logo\".png", "Logo", "mp:external/abc",
                "https://x.fr/" + "a".repeat(300), "logo\n", "clé"}) {
            assertEquals("logo", image.apply(mauvais), "refusé, logo gardé : « " + mauvais + " »");
        }
    }

    @Test
    void repliRetireDAbordLeNomPuisLesChampsRecents() {
        JsonObject o = a(Activite.Phase.EN_JEU, false, true, false, true);
        JsonObject r1 = Activite.repli(o, 1);
        assertFalse(r1.has("name"));
        assertTrue(r1.has("buttons") && r1.has("status_display_type"));
        JsonObject r2 = Activite.repli(o, 2);
        assertFalse(r2.has("name") || r2.has("buttons") || r2.has("status_display_type"));
        assertEquals("Pokényle", details(r2));
        assertTrue(o.has("name") && o.has("buttons"), "l'activité d'origine n'est pas touchée");
        assertNull(Activite.repli(null, 2));
    }
}
