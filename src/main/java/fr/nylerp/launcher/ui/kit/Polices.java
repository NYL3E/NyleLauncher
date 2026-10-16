package fr.nylerp.launcher.ui.kit;

import javafx.scene.text.Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * Les polices du launcher, en TTF statiques (JavaFX ne lit ni le woff2 ni les axes variables ; il enregistre chaque
 * fichier sous SA famille, d'où un nom par graisse) :
 * <ul>
 *   <li>TOUS les titres (décision owner 16/10, style Aniimo) → {@link #TITRES}, Northura Black SemExp (accents complets) ;
 *       voir {@link TitreEclats} ;</li>
 *   <li>corps de texte, comme le site ({@code var(--arrondi)}) en 700 / 800 / 900 → {@link #NUNITO_BOLD} /
 *       {@link #NUNITO_EXTRA} / {@link #NUNITO_NOIR} (instances de Nunito, scripts/polices-site.py) ;</li>
 *   <li>{@code var(--bulle)} du site (Lilita One, renommée) → {@link #BULLE} ;</li>
 *   <li>texte courant {@code var(--lecture)} → police système ({@link #lecture(double)}).</li>
 * </ul>
 */
public final class Polices {

    private static final Logger LOG = LoggerFactory.getLogger(Polices.class);

    public static final String TITRES       = "Northura Black SemExp";
    public static final String NUNITO_BOLD  = "Nunito Bold";
    public static final String NUNITO_EXTRA = "Nunito ExtraBold";
    public static final String NUNITO_NOIR  = "Nunito Black";
    public static final String BULLE        = "Bulle Pokenyle";

    private static final String[] FICHIERS = {
            "NorthuraBlackSemiExpanded.ttf", "Nunito-Bold.ttf", "Nunito-ExtraBold.ttf", "Nunito-Black.ttf", "BullePokenyle-Regular.ttf"
    };
    private static volatile boolean chargees;

    /** Enregistre les polices auprès de JavaFX (idempotent ; à appeler avant de construire un composant du kit). */
    public static synchronized void charger() {
        if (chargees) return;
        for (String f : FICHIERS) {
            try (InputStream in = Polices.class.getResourceAsStream("/fonts/site/" + f)) {
                Font font = in == null ? null : Font.loadFont(in, 14);
                if (font == null) LOG.warn("police du kit introuvable ou illisible : {}", f);
                else LOG.debug("police du kit : {} → famille « {} »", f, font.getFamily());
            } catch (Exception e) {
                LOG.warn("police du kit {} : {}", f, e.toString());
            }
        }
        chargees = true;
    }

    public static Font titre(double taille) { charger(); return Font.font(TITRES, taille); }
    public static Font gras(double taille)  { charger(); return Font.font(NUNITO_BOLD, taille); }
    public static Font extra(double taille) { charger(); return Font.font(NUNITO_EXTRA, taille); }
    public static Font noir(double taille)  { charger(); return Font.font(NUNITO_NOIR, taille); }
    public static Font bulle(double taille) { charger(); return Font.font(BULLE, taille); }

    /** Texte courant : la police du système (San Francisco sur Mac, Segoe UI sous Windows), comme le site. */
    public static Font lecture(double taille) { return Font.font("System", taille); }

    private Polices() {}
}
