package fr.nylerp.launcher;

import fr.nylerp.launcher.util.CrashReporter;
import fr.nylerp.launcher.util.HeapRelaunch;
import javafx.application.Application;

public final class Main {
    public static void main(String[] args) {
        // Install crash handler FIRST so any exception from JavaFX setup, font loading, etc. is captured.
        CrashReporter.install();
        // Installs older than bootstrap 0.3.20 froze -Xmx256m in their jpackage .cfg — not
        // enough for the Microsoft-login WebView (OOM crash loop). If we woke up in such a
        // cramped JVM, hand over to a relaunched 1 GB JVM BEFORE touching JavaFX. Anti-loop
        // guarded; on any failure it returns false and we continue in-process as before.
        if (HeapRelaunch.relaunchIfCramped(args)) {
            System.exit(0); // the relaunched process owns the UI from here
            return;
        }
        Application.launch(LauncherApp.class, args);
    }
}
