package fr.nylerp.launcher.annonces;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Annonces : ciblage, une seule fois par id, fenêtre de dates à l'heure du serveur (jamais celle
 * du PC), réglage « Notifications », cadence (3 par cycle, espacées) et liens https seulement.
 */
@Timeout(60)
class ServiceAnnoncesTest {

    private static final ZonedDateTime T = ZonedDateTime.of(2026, 12, 14, 17, 0, 0, 0, ZoneOffset.UTC);
    private static final Duration ESPACEMENT = Duration.ofMillis(250);

    private FauxServeur serveur;
    private Path dossier;
    private ScheduledExecutorService planif;
    private final List<String[]> cartes = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> instants = Collections.synchronizedList(new ArrayList<>());
    private Set<String> joues = Set.of("nylerp");
    private ServiceAnnonces.TypeCompte compte = ServiceAnnonces.TypeCompte.CRACK;
    private final AtomicBoolean notifications = new AtomicBoolean(true);

    @BeforeEach
    void preparer() throws Exception {
        serveur = new FauxServeur();
        serveur.pour("annonces").date = T;
        dossier = Files.createTempDirectory("nyle-annonces");
        planif = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterEach
    void fermer() {
        planif.shutdownNow();
        serveur.close();
    }

    private ServiceAnnonces service() {
        HorlogeServeur h = new HorlogeServeur();
        return new ServiceAnnonces(
                new PointAcces("annonces", serveur.uri("annonces"), new ClientLecture(),
                        new CopieLocale(dossier.resolve("annonces-cache.json")), h),
                h, new VuesAnnonces(dossier.resolve("annonces-vues.json")),
                () -> joues, () -> compte, notifications::get,
                (titre, texte, cible) -> { cartes.add(new String[]{titre, texte, cible}); instants.add(System.nanoTime()); },
                planif, ESPACEMENT);
    }

    private static String annonce(String id, String univers, String compte, String debut, String fin, int priorite, String action) {
        return """
                {"id":"%s","titre":"Titre %s","texte":"Texte %s","univers":"%s","compte":"%s","debut":"%s","fin":%s,
                 "action":%s,"priorite":%d,"maj":"2026-10-16T14:34:18+02:00"}"""
                .formatted(id, id, id, univers, compte, debut, fin == null ? "null" : "\"" + fin + "\"",
                        action == null ? "{\"type\":\"aucune\",\"valeur\":null,\"libelle\":null}" : action, priorite);
    }

    private void publier(String... annonces) {
        serveur.pour("annonces").corps = "{\"v\":1,\"maintenant\":\"2026-10-16T14:34:18+02:00\",\"annonces\":["
                + String.join(",", annonces) + "]}";
    }

    private static String iso(ZonedDateTime z) { return z.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME); }

    private void attendreCartes(int n) throws InterruptedException {
        long fin = System.currentTimeMillis() + 10_000;
        while (cartes.size() < n && System.currentTimeMillis() < fin) Thread.sleep(20);
        Thread.sleep(ESPACEMENT.toMillis() + 150); // laisser passer une éventuelle carte de trop
    }

    @Test
    void ciblageParUniversEtParCompte() {
        Annonce pk = Annonce.depuis(JsonParser.parseString(annonce("a", "pokenyle", "premium", iso(T), null, 0, null)));
        Annonce tous = Annonce.depuis(JsonParser.parseString(annonce("b", "tous", "tous", iso(T), null, 0, null)));
        Annonce crack = Annonce.depuis(JsonParser.parseString(annonce("c", "nylerp", "crack", iso(T), null, 0, null)));
        assertTrue(ServiceAnnonces.cibleCorrespond(tous, Set.of(), ServiceAnnonces.TypeCompte.AUCUN));
        assertFalse(ServiceAnnonces.cibleCorrespond(pk, Set.of("nylerp"), ServiceAnnonces.TypeCompte.PREMIUM), "univers jamais joué");
        assertFalse(ServiceAnnonces.cibleCorrespond(pk, Set.of("pokenyle"), ServiceAnnonces.TypeCompte.CRACK), "mauvais compte");
        assertTrue(ServiceAnnonces.cibleCorrespond(pk, Set.of("pokenyle"), ServiceAnnonces.TypeCompte.PREMIUM));
        assertFalse(ServiceAnnonces.cibleCorrespond(crack, Set.of("nylerp"), ServiceAnnonces.TypeCompte.AUCUN), "sans compte : « tous » seulement");
        assertTrue(ServiceAnnonces.cibleCorrespond(crack, Set.of("nylerp"), ServiceAnnonces.TypeCompte.CRACK));
    }

    @Test
    void chaqueAnnonceNEstMontreeQuUneFoisMemeApresRedemarrage() throws Exception {
        serveur.pour("annonces").etag = "\"e1\"";
        publier(annonce("une", "tous", "tous", iso(T.minusHours(1)), null, 0, null));
        ServiceAnnonces s = service();
        assertTrue(s.rafraichir());
        attendreCartes(1);
        assertEquals(1, cartes.size());
        assertTrue(s.rafraichir());
        attendreCartes(1);
        assertEquals(1, cartes.size(), "deuxième cycle (304) : rien de plus");
        assertEquals(1, serveur.nonModifies("annonces"));
        publier(annonce("une", "tous", "tous", iso(T.minusHours(1)), null, 9, null).replace("Titre une", "Titre modifié"));
        serveur.pour("annonces").etag = "\"e2\"";
        service().rafraichir(); // nouveau launcher, même dossier d'état
        attendreCartes(1);
        assertEquals(1, cartes.size(), "un id déjà vu ne revient pas, même modifié");
        assertTrue(Files.readString(dossier.resolve("annonces-vues.json")).contains("\"une\""));
    }

    @Test
    void fenetreDeDatesAuFuseauDeParis() throws Exception {
        // T = 17:00 UTC = 18:00 à Paris (UTC+1 en décembre)
        publier(
                annonce("debut-pile", "tous", "tous", "2026-12-14T18:00:00+01:00", null, 5, null),
                annonce("debut-futur", "tous", "tous", "2026-12-14T18:00:30+01:00", null, 5, null),
                annonce("fin-pile", "tous", "tous", "2026-12-14T10:00:00+01:00", "2026-12-14T18:00:00+01:00", 5, null),
                annonce("fin-future", "tous", "tous", "2026-12-14T10:00:00+01:00", "2026-12-14T19:00:00+01:00", 4, null));
        service().rafraichir();
        attendreCartes(2);
        List<String> titres = cartes.stream().map(c -> c[0]).toList();
        assertEquals(List.of("Titre debut-pile", "Titre fin-future"), titres);
    }

    @Test
    void horlogeDuPcDecaleeDeDeuxJoursNeChangeRien() throws Exception {
        ZonedDateTime vraiMaintenant = ZonedDateTime.now(ZoneOffset.UTC);
        // Le serveur dit « dans 2 jours » : c'est le PC du joueur qui retarde de 2 jours.
        serveur.pour("annonces").date = vraiMaintenant.plusDays(2);
        publier(
                annonce("selon-serveur", "tous", "tous", iso(vraiMaintenant.plusDays(2).minusHours(1)),
                        iso(vraiMaintenant.plusDays(2).plusHours(1)), 0, null),
                annonce("selon-pc", "tous", "tous", iso(vraiMaintenant.minusHours(1)), iso(vraiMaintenant.plusHours(1)), 0, null));
        service().rafraichir();
        attendreCartes(1);
        assertEquals(1, cartes.size());
        assertEquals("Titre selon-serveur", cartes.get(0)[0]);
    }

    @Test
    void sansHeureDuServeurRienNEstMontre() throws Exception {
        publier(annonce("x", "tous", "tous", iso(T.minusDays(400)), null, 0, null));
        notifications.set(false);
        service().rafraichir(); // remplit la copie locale sans rien montrer
        notifications.set(true);
        serveur.close(); // hors ligne : copie présente, mais aucune heure du serveur
        ServiceAnnonces horsLigne = service();
        assertFalse(horsLigne.rafraichir());
        assertTrue(horsLigne.aMontrer().isEmpty());
        attendreCartes(0);
        assertTrue(cartes.isEmpty());
    }

    @Test
    void notificationsCoupeesNiCarteNiMarqueeVue() throws Exception {
        publier(annonce("n", "tous", "tous", iso(T.minusHours(1)), null, 0, null));
        notifications.set(false);
        ServiceAnnonces s = service();
        s.rafraichir();
        attendreCartes(0);
        assertTrue(cartes.isEmpty());
        assertFalse(Files.exists(dossier.resolve("annonces-vues.json")));
        notifications.set(true);
        s.rafraichir();
        attendreCartes(1);
        assertEquals(1, cartes.size(), "réactivées : l'annonce encore en cours arrive");
    }

    @Test
    void troisCartesAuPlusParCycleDansLOrdreEtEspacees() throws Exception {
        publier(
                annonce("p1-tot", "tous", "tous", iso(T.minusHours(3)), null, 1, null),
                annonce("p9", "tous", "tous", iso(T.minusHours(1)), null, 9, null),
                annonce("p5-tard", "tous", "tous", iso(T.minusHours(1)), null, 5, null),
                annonce("p5-tot", "tous", "tous", iso(T.minusHours(2)), null, 5, null),
                annonce("p1-tard", "tous", "tous", iso(T.minusHours(1)), null, 1, null));
        ServiceAnnonces s = service();
        s.rafraichir();
        attendreCartes(3);
        assertEquals(List.of("Titre p9", "Titre p5-tot", "Titre p5-tard"), cartes.stream().map(c -> c[0]).toList());
        for (int i = 1; i < instants.size(); i++) {
            assertTrue(instants.get(i) - instants.get(i - 1) >= ESPACEMENT.toNanos(), "une carte à la fois, espacées");
        }
        s.rafraichir();
        attendreCartes(5);
        assertEquals(List.of("Titre p9", "Titre p5-tot", "Titre p5-tard", "Titre p1-tot", "Titre p1-tard"),
                cartes.stream().map(c -> c[0]).toList(), "les suivantes au cycle suivant");
    }

    @Test
    void ciblesDesBoutonsEtLiensHttpsSeulement() throws Exception {
        joues = Set.of("nylerp", "pokenyle");
        publier(
                annonce("u", "pokenyle", "tous", iso(T.minusHours(1)), null, 9,
                        "{\"type\":\"univers\",\"valeur\":\"pokenyle\",\"libelle\":\"Ouvrir Pokényle\"}"),
                annonce("h", "tous", "tous", iso(T.minusHours(1)), null, 8,
                        "{\"type\":\"url\",\"valeur\":\"https://pokerayou.academy/saison-3/\",\"libelle\":\"Voir\"}"),
                annonce("p", "tous", "tous", iso(T.minusHours(1)), null, 7,
                        "{\"type\":\"url\",\"valeur\":\"http://pirate.example/\",\"libelle\":\"Voir\"}"));
        service().rafraichir();
        attendreCartes(3);
        assertEquals("pokenyle", cartes.get(0)[2]);
        assertEquals("https://pokerayou.academy/saison-3/", cartes.get(1)[2]);
        assertNull(cartes.get(2)[2], "lien non https : pas de bouton, clic = ouvrir le launcher");
    }

    @Test
    void texteBrutEtAnnoncesMalFormeesIgnorees() throws Exception {
        publier(
                annonce("ok", "tous", "tous", iso(T.minusHours(1)), null, 0, null)
                        .replace("Texte ok", "Ligne\\nsuivante\\t <b>gras</b>"),
                annonce("univers-inconnu", "minecraft", "tous", iso(T.minusHours(1)), null, 0, null),
                "{\"id\":\"sans-titre\",\"texte\":\"x\",\"univers\":\"tous\",\"compte\":\"tous\",\"debut\":\"" + iso(T) + "\"}",
                "\"pas un objet\"");
        service().rafraichir();
        attendreCartes(1);
        assertEquals(1, cartes.size());
        assertEquals("Ligne suivante <b>gras</b>", cartes.get(0)[1], "contrôles retirés, aucun HTML interprété");
    }
}
