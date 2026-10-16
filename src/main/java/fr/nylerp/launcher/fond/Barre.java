package fr.nylerp.launcher.fond;

import fr.nylerp.launcher.config.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

/**
 * L'icône du launcher dans la zone de notification (Windows), la barre des menus (macOS) ou la
 * zone d'état (Linux), avec un menu « Ouvrir / Quitter ».
 *
 * <p>Sur Windows et Linux, un clic gauche ouvre directement le launcher (comme Discord ou Steam) ;
 * sur macOS, un clic déroule le menu, comme toutes les icônes de la barre des menus.
 *
 * <p>Image : {@code /images/barre/barre-macTemplate.png} (monochrome, adapté au thème clair ou
 * sombre de la barre des menus) et {@code /images/barre/barre-couleur.png} quand le lot des médias
 * les fournira ; d'ici là, le logo du launcher.
 *
 * <p>GNOME sans extension d'icônes d'état n'a pas de zone de notification : {@link #installer}
 * renvoie alors {@code null} et le launcher se passe d'icône (voir {@link Fond}).
 */
final class Barre {

    private static final Logger LOG = LoggerFactory.getLogger(Barre.class);

    private final AtomicReference<TrayIcon> icone = new AtomicReference<>();
    private volatile long derniereInteraction;
    private volatile boolean retiree;

    private Barre() {}

    /**
     * Pose l'icône. {@code null} si le système n'a pas de zone de notification ; si la pose échoue
     * plus tard sur le fil AWT, {@code surEchec} est appelé (sur ce fil).
     *
     * <p>La création se fait sur le fil AWT SANS l'attendre : sous macOS, le fil JavaFX est le fil
     * principal d'AppKit, dont AWT a lui-même besoin pour s'initialiser ; un {@code invokeAndWait}
     * depuis JavaFX les bloque l'un l'autre pour toujours (constaté au premier essai).
     */
    static Barre installer(Runnable ouvrir, Runnable quitter, Runnable surEchec) {
        try {
            if (Systeme.mac()) System.setProperty("apple.awt.enableTemplateImages", "true");
            if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
                LOG.info("Fond : pas de zone de notification sur ce bureau — launcher sans icône de barre");
                return null;
            }
            Barre barre = new Barre();
            EventQueue.invokeLater(() -> {
                try {
                    if (!barre.retiree) barre.icone.set(creer(barre, ouvrir, quitter));
                    LOG.info("Fond : icône posée dans la barre système");
                } catch (Throwable t) {
                    // Zone annoncée mais refusée (certains bureaux Linux) : sans icône, fermer la
                    // fenêtre doit quitter, jamais laisser un launcher invisible.
                    LOG.warn("Fond : icône de barre impossible : {}", t.toString());
                    surEchec.run();
                }
            });
            return barre;
        } catch (Throwable t) {
            LOG.warn("Fond : icône de barre impossible : {}", t.toString());
            return null;
        }
    }

    private static TrayIcon creer(Barre barre, Runnable ouvrir, Runnable quitter) throws Exception {
        PopupMenu menu = new PopupMenu();
        MenuItem itemOuvrir = new MenuItem("Ouvrir " + Constants.APP_NAME);
        itemOuvrir.addActionListener(e -> ouvrir.run());
        MenuItem itemQuitter = new MenuItem("Quitter");
        itemQuitter.addActionListener(e -> quitter.run());
        menu.add(itemOuvrir);
        menu.addSeparator();
        menu.add(itemQuitter);

        TrayIcon icone = new TrayIcon(image(), Constants.APP_NAME, menu);
        icone.setImageAutoSize(false); // les tailles exactes sont fournies (image multirésolution)
        // Windows : double-clic (ActionEvent) ; Windows/Linux : clic gauche simple aussi.
        icone.addActionListener(e -> ouvrir.run());
        icone.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                barre.derniereInteraction = System.currentTimeMillis();
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (!Systeme.mac() && e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 1) {
                    ouvrir.run();
                }
            }
        });
        SystemTray.getSystemTray().add(icone);
        return icone;
    }

    private static Image image() {
        String chemin = Systeme.mac() ? "/images/barre/barre-macTemplate.png" : "/images/barre/barre-couleur.png";
        BufferedImage img = lire(chemin);
        if (img == null) {
            img = lire("/images/logo-mark.png");
            if (img == null) return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            // La barre des menus macOS teint l'image (noir sur barre claire, blanc sur sombre) :
            // en silhouette pleine, le coffre devenait une tache. On découpe la bande rouge et le
            // fond du coffre pour garder le couvercle et le rebord, lisibles à 18 pt.
            if (Systeme.mac()) img = gabarit(img);
        }
        // Taille logique de la zone (16 px sous Windows à 100 %, 18 pt sous macOS), déclinée pour les
        // écrans à haute densité : AWT choisit la variante adaptée, sans flou de mise à l'échelle.
        java.awt.Dimension d = SystemTray.getSystemTray().getTrayIconSize();
        int base = Math.max(16, Math.min(d.width, d.height));
        int[] facteurs = { 100, 125, 150, 200 };
        Image[] variantes = new Image[facteurs.length];
        for (int i = 0; i < facteurs.length; i++) variantes[i] = reduire(img, base * facteurs[i] / 100);
        return new java.awt.image.BaseMultiResolutionImage(variantes);
    }

    /** Version monochrome du logo pour la barre des menus (alpha seul ; macOS choisit la teinte). */
    static BufferedImage gabarit(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = src.getRGB(x, y);
                int a = p >>> 24, r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
                boolean rouge = r > 150 && g < 110;
                boolean sombre = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0 < 0.42;
                out.setRGB(x, y, (rouge || sombre) ? 0 : (a << 24));
            }
        }
        return out;
    }

    /** Réduction de qualité (passes successives par moitié, puis bicubique). */
    static BufferedImage reduire(BufferedImage src, int cote) {
        BufferedImage cur = src;
        int w = src.getWidth();
        while (w / 2 >= cote) {
            w /= 2;
            cur = dessiner(cur, w);
        }
        return dessiner(cur, cote);
    }

    private static BufferedImage dessiner(BufferedImage src, int cote) {
        BufferedImage out = new BufferedImage(cote, cote, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, cote, cote, null);
        g.dispose();
        return out;
    }

    private static BufferedImage lire(String chemin) {
        try (InputStream in = Barre.class.getResourceAsStream(chemin)) {
            return in == null ? null : ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    /** Horodatage du dernier clic sur l'icône (macOS : un menu ouvert peut activer l'app). */
    long derniereInteraction() { return derniereInteraction; }

    void retirer() {
        retiree = true;
        try {
            EventQueue.invokeLater(() -> {
                TrayIcon i = icone.getAndSet(null);
                if (i != null) SystemTray.getSystemTray().remove(i);
            });
        } catch (Throwable ignored) { }
    }
}
