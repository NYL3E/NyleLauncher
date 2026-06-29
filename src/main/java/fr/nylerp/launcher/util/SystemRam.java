package fr.nylerp.launcher.util;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Detects the machine's total physical RAM, WITHOUT depending on the {@code jdk.management}
 * module — the bootstrap's jlink runtime only bundles {@code java.management}, so
 * {@code com.sun.management.OperatingSystemMXBean#getTotalMemorySize()} is not statically
 * available in the payload. We try it reflectively first (works under a full JDK / when the
 * module happens to be present), then fall back to OS commands. Returns {@code -1} if unknown.
 *
 * <p>Used to clamp the game heap (-Xmx) so a default/oversized RAM setting can never make the
 * spawned JVM fail to reserve/commit its heap at startup.
 */
public final class SystemRam {
    private SystemRam() {}

    private static volatile long cachedMb = Long.MIN_VALUE;

    /** Total physical RAM in MB, or {@code -1} if it could not be determined. Cached. */
    public static long totalMb() {
        if (cachedMb != Long.MIN_VALUE) return cachedMb;
        long v = detect();
        cachedMb = v;
        return v;
    }

    private static long detect() {
        // 1) Reflective com.sun.management — present on a full JDK; throws NoSuchMethod under the
        //    payload's java.management-only runtime → caught, falls through to OS commands.
        for (String name : new String[]{"getTotalMemorySize", "getTotalPhysicalMemorySize"}) {
            try {
                Object os = ManagementFactory.getOperatingSystemMXBean();
                Method m = os.getClass().getMethod(name);
                m.setAccessible(true);
                long bytes = ((Number) m.invoke(os)).longValue();
                if (bytes > 0) return bytes / (1024 * 1024);
            } catch (Throwable ignored) { /* try next */ }
        }
        String osn = System.getProperty("os.name", "").toLowerCase();
        try {
            if (osn.contains("win")) {
                long b = firstLong(run("powershell", "-NoProfile", "-Command",
                        "(Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory"));
                if (b > 0) return b / (1024 * 1024);
                b = firstLong(run("wmic", "ComputerSystem", "get", "TotalPhysicalMemory"));
                if (b > 0) return b / (1024 * 1024);
            } else if (osn.contains("mac")) {
                long b = firstLong(run("sysctl", "-n", "hw.memsize"));
                if (b > 0) return b / (1024 * 1024);
            } else {
                for (String line : Files.readAllLines(Path.of("/proc/meminfo"))) {
                    if (line.startsWith("MemTotal")) {
                        long kb = firstLong(line);
                        if (kb > 0) return kb / 1024;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private static String run(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor();
        return out;
    }

    /** First run of digits in {@code s} as a long, or {@code -1}. */
    private static long firstLong(String s) {
        if (s == null) return -1;
        StringBuilder n = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') n.append(c);
            else if (n.length() > 0) break;
        }
        try { return n.length() > 0 ? Long.parseLong(n.toString()) : -1; }
        catch (NumberFormatException e) { return -1; }
    }
}
