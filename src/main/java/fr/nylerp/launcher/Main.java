package fr.nylerp.launcher;

import fr.nylerp.launcher.util.CrashReporter;
import javafx.application.Application;

public final class Main {
    public static void main(String[] args) {
        // Install crash handler FIRST so any exception from JavaFX setup, font loading, etc. is captured.
        CrashReporter.install();
        Application.launch(LauncherApp.class, args);
    }
}
