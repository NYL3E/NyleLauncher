package fr.nylerp.launcher.ui.boutique;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Boutique dans le launcher (refonte 2026-10, lot L10). On n'en fait PAS une copie : le launcher affiche la
 * vraie page de la boutique du site dans un WebView, en mode launcher. Toute modification de la boutique du
 * site apparaît donc d'elle-même dans le launcher.
 *
 * Point d'entrée unique : {@link #ouvrir(String, String)}.
 */
public final class Boutique {

    /** Site de Pokényle : la page boutique gère {@code ?launcher=1&pseudo=…} (en-tête et pied masqués, pseudo prérempli). */
    public static final String ORIGINE_POKENYLE = "https://pokerayou.academy";
    /** Site NyleRP : sa boutique n'a pas de mode launcher, on l'ouvre telle quelle. */
    public static final String BOUTIQUE_NYLERP = "https://www.nylerp.fr/boutique/";

    private Boutique() { }

    /** Crée la vue de la boutique de l'univers (« pokenyle » ou « nylerp »). Le WebView naît ici et meurt à {@link BoutiqueView#fermer()}. */
    public static BoutiqueView ouvrir(String univers, String pseudo) {
        boolean nylerp = "nylerp".equalsIgnoreCase(univers);
        return new BoutiqueView(nylerp ? "Boutique NyleRP" : "Boutique Pokényle", url(univers, pseudo));
    }

    /** Adresse chargée par le launcher pour un univers et un pseudo (pseudo encodé, absent s'il est vide). */
    static String url(String univers, String pseudo) {
        if ("nylerp".equalsIgnoreCase(univers)) return BOUTIQUE_NYLERP;
        StringBuilder u = new StringBuilder(ORIGINE_POKENYLE).append("/boutique/?launcher=1");
        if (pseudo != null && !pseudo.isBlank()) {
            u.append("&pseudo=").append(URLEncoder.encode(pseudo.trim(), StandardCharsets.UTF_8));
        }
        return u.toString();
    }

    /** Ce qu'il faut faire d'une adresse vers laquelle la page veut aller. */
    enum Destination { ICI, PAIEMENT, NAVIGATEUR }

    /**
     * Les pages des deux sites restent dans la vue ; Tebex et les prestataires de paiement (PayPal, Stripe,
     * 3-D Secure…) vont dans la fenêtre de paiement intégrée ; tout le reste part dans le navigateur système.
     */
    static Destination destination(String adresse) {
        String hote = hote(adresse);
        if (hote == null) return Destination.ICI;            // about:blank, data:, javascript: … : rien à faire
        if (estSite(hote)) return Destination.ICI;
        if (estPaiement(hote)) return Destination.PAIEMENT;
        return Destination.NAVIGATEUR;
    }

    static boolean estSite(String hote) {
        return finitPar(hote, "pokerayou.academy") || finitPar(hote, "nylerp.fr")
                || hote.equals("pokenyle-v2.pages.dev") || hote.endsWith(".pokenyle-v2.pages.dev");
    }

    static boolean estPaiement(String hote) {
        return finitPar(hote, "tebex.io") || finitPar(hote, "paypal.com") || finitPar(hote, "paypalobjects.com")
                || finitPar(hote, "stripe.com") || finitPar(hote, "stripe.network") || finitPar(hote, "paysafecard.com")
                || finitPar(hote, "adyen.com") || finitPar(hote, "checkout.com") || finitPar(hote, "gocardless.com")
                || finitPar(hote, "google.com") || finitPar(hote, "apple.com") || finitPar(hote, "hcaptcha.com")
                || finitPar(hote, "recaptcha.net") || finitPar(hote, "cloudflare.com");
    }

    /** Hôte en minuscules d'une adresse http(s), ou null si ce n'en est pas une. */
    static String hote(String adresse) {
        if (adresse == null) return null;
        try {
            URI u = URI.create(adresse.trim());
            String s = u.getScheme();
            if (s == null || !(s.equalsIgnoreCase("http") || s.equalsIgnoreCase("https"))) return null;
            return u.getHost() == null ? null : u.getHost().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean finitPar(String hote, String domaine) {
        return hote.equals(domaine) || hote.endsWith("." + domaine);
    }

    /** Ouvre une adresse dans le navigateur système (silencieux en cas d'échec). */
    static void navigateur(String adresse) {
        Thread t = new Thread(() -> {
            try {
                if (java.awt.Desktop.isDesktopSupported()
                        && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                    java.awt.Desktop.getDesktop().browse(URI.create(adresse));
                }
            } catch (Exception ignored) { }
        }, "boutique-navigateur");
        t.setDaemon(true);
        t.start();
    }
}
