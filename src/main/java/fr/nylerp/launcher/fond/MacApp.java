package fr.nylerp.launcher.fond;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Les quelques messages {@code NSApplication} dont le mode fond a besoin sur macOS, envoyés par
 * {@code objc_msgSend} via JNA.
 *
 * <p>Chaque appel passe par un {@link Function} à arité FIXE : sur Apple Silicon, appeler
 * {@code objc_msgSend} comme une fonction variadique placerait les arguments sur la pile au lieu
 * des registres, et AppKit lirait n'importe quoi.
 *
 * <p>AppKit exige le fil principal ; sur macOS c'est le fil JavaFX, donc tout appel se fait depuis
 * {@code Platform.runLater}. En cas d'échec (bibliothèque introuvable, symbole absent), chaque
 * méthode renvoie une valeur neutre : le launcher garde simplement son icône dans le Dock.
 */
final class MacApp {

    private static final Logger LOG = LoggerFactory.getLogger(MacApp.class);

    /** {@code NSApplicationActivationPolicyRegular} : icône dans le Dock, ⌘⇥. */
    static final long NORMALE = 0;
    /** {@code NSApplicationActivationPolicyAccessory} : pas de Dock, fenêtres possibles. */
    static final long ACCESSOIRE = 1;

    private static Function msgSend;
    private static Function getClass;
    private static Function selRegister;
    private static boolean indisponible;

    private static synchronized boolean pret() {
        if (msgSend != null) return true;
        if (indisponible || !Systeme.mac()) return false;
        try {
            NativeLibrary objc = NativeLibrary.getInstance("objc");
            msgSend = objc.getFunction("objc_msgSend");
            getClass = objc.getFunction("objc_getClass");
            selRegister = objc.getFunction("sel_registerName");
            return true;
        } catch (Throwable t) {
            indisponible = true;
            LOG.warn("Fond : appels Objective-C indisponibles ({}) — l'icône du Dock restera visible", t.toString());
            return false;
        }
    }

    private static Pointer classe(String nom) {
        return (Pointer) getClass.invoke(Pointer.class, new Object[] { nom });
    }

    private static Pointer selecteur(String nom) {
        return (Pointer) selRegister.invoke(Pointer.class, new Object[] { nom });
    }

    private static Pointer nsApp() {
        return (Pointer) msgSend.invoke(Pointer.class,
                new Object[] { classe("NSApplication"), selecteur("sharedApplication") });
    }

    /** Bascule l'icône du Dock. Renvoie {@code true} si AppKit a accepté. */
    static boolean politique(long politique) {
        if (!pret()) return false;
        try {
            Pointer app = nsApp();
            if (app == null) return false;
            Object r = msgSend.invoke(byte.class,
                    new Object[] { app, selecteur("setActivationPolicy:"), politique });
            return r instanceof Byte b && b != 0;
        } catch (Throwable t) {
            LOG.warn("Fond : setActivationPolicy({}) a échoué : {}", politique, t.toString());
            return false;
        }
    }

    /** Amène le launcher au premier plan (après « Ouvrir » ou un clic sur une annonce). */
    static void activer() {
        if (!pret()) return;
        try {
            Pointer app = nsApp();
            if (app != null) {
                msgSend.invoke(void.class,
                        new Object[] { app, selecteur("activateIgnoringOtherApps:"), (byte) 1 });
            }
        } catch (Throwable t) {
            LOG.debug("Fond : activateIgnoringOtherApps a échoué : {}", t.toString());
        }
    }

    /** Rend la main à l'application précédente sans cacher nos fenêtres (les annonces restent visibles). */
    static void desactiver() {
        if (!pret()) return;
        try {
            Pointer app = nsApp();
            if (app != null) msgSend.invoke(void.class, new Object[] { app, selecteur("deactivate") });
        } catch (Throwable t) {
            LOG.debug("Fond : deactivate a échoué : {}", t.toString());
        }
    }

    /** {@code [NSApp isActive]} : vrai quand le launcher est l'application de premier plan. */
    static boolean estActive() {
        if (!pret()) return false;
        try {
            Pointer app = nsApp();
            if (app == null) return false;
            Object r = msgSend.invoke(byte.class, new Object[] { app, selecteur("isActive") });
            return r instanceof Byte b && b != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private MacApp() {}
}
