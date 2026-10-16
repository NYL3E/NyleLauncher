package fr.nylerp.launcher.ui.boutique;

import org.junit.jupiter.api.Test;

import static fr.nylerp.launcher.ui.boutique.Boutique.Destination.*;
import static org.junit.jupiter.api.Assertions.*;

class BoutiqueTest {

    @Test
    void urlPokenyleAvecPseudoEncode() {
        assertEquals("https://pokerayou.academy/boutique/?launcher=1&pseudo=NYL3E", Boutique.url("pokenyle", "NYL3E"));
        assertEquals("https://pokerayou.academy/boutique/?launcher=1&pseudo=Jean+%26+Co%C3%A9",
                Boutique.url("pokenyle", " Jean & Coé "));
    }

    @Test
    void urlSansPseudo() {
        assertEquals("https://pokerayou.academy/boutique/?launcher=1", Boutique.url("pokenyle", null));
        assertEquals("https://pokerayou.academy/boutique/?launcher=1", Boutique.url("pokenyle", "  "));
    }

    @Test
    void urlNylerp() {
        assertEquals(Boutique.BOUTIQUE_NYLERP, Boutique.url("nylerp", "NYL3E"));
        assertEquals(Boutique.BOUTIQUE_NYLERP, Boutique.url("NyleRP", null));
    }

    @Test
    void filtrageDesLiens() {
        assertEquals(ICI, Boutique.destination("https://pokerayou.academy/boutique/?launcher=1"));
        assertEquals(ICI, Boutique.destination("https://www.nylerp.fr/boutique/"));
        assertEquals(ICI, Boutique.destination("https://d09bcb0b.pokenyle-v2.pages.dev/boutique/"));
        assertEquals(ICI, Boutique.destination("about:blank"));
        assertEquals(ICI, Boutique.destination(null));
        assertEquals(PAIEMENT, Boutique.destination("https://checkout.tebex.io/checkout/abc"));
        assertEquals(PAIEMENT, Boutique.destination("https://pay.tebex.io/x"));
        assertEquals(PAIEMENT, Boutique.destination("https://www.paypal.com/checkoutnow?token=1"));
        assertEquals(PAIEMENT, Boutique.destination("https://hooks.stripe.com/3d_secure"));
        assertEquals(NAVIGATEUR, Boutique.destination("https://discord.gg/nyle"));
        assertEquals(NAVIGATEUR, Boutique.destination("https://x.com/AREtoiles"));
        // un domaine qui contient le nôtre sans en être un sous-domaine ne passe pas
        assertEquals(NAVIGATEUR, Boutique.destination("https://faux-pokerayou.academy.example.com/"));
        assertEquals(NAVIGATEUR, Boutique.destination("https://evil-tebex.io/"));
    }
}
