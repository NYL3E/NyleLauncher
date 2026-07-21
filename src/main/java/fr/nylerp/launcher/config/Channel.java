package fr.nylerp.launcher.config;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Canal de build (owner 2026-07-20) : <b>PROD</b> (défaut) ou <b>DEV</b>.
 *
 * <p>Le canal est lu au démarrage depuis la ressource bundlée {@code /channel.txt}, écrite par
 * {@code processResources} selon la propriété Gradle {@code -Pchannel} (voir build.gradle). Un build
 * <b>DEV</b> ({@code ./gradlew ... -Pchannel=dev}) :
 * <ul>
 *   <li>pointe sur le <b>serveur de build</b> (dev) au lieu du proxy de prod ;</li>
 *   <li>tire un <b>pack-dev</b> séparé (mods WIP) au lieu de {@code pack-latest} ;</li>
 *   <li>s'affiche sous un nom distinct (« NyleLauncher DEV ») et prend le <b>thème terminal</b> ;</li>
 * </ul>
 * — le tout sans jamais perturber le launcher de prod (canal absent/inconnu ⇒ PROD).
 */
public final class Channel {

    private static final boolean DEV = "dev".equalsIgnoreCase(read());

    private Channel() {}

    public static boolean isDev() { return DEV; }

    private static String read() {
        try (InputStream in = Channel.class.getResourceAsStream("/channel.txt")) {
            if (in == null) return "prod";
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "prod";   // fail-safe : au moindre doute, canal PROD
        }
    }
}
