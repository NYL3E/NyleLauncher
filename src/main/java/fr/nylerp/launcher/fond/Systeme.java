package fr.nylerp.launcher.fond;

import java.util.Locale;

/** Système d'exploitation courant, lu une fois. */
final class Systeme {

    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

    static boolean mac()     { return OS.contains("mac"); }
    static boolean windows() { return OS.contains("win"); }
    static boolean linux()   { return !mac() && !windows(); }

    private Systeme() {}
}
