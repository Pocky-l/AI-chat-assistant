package com.mechanism.aichatassistant;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ClaudePathDetector {

    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    private static String cachedPath = null;

    /**
     * Returns the path to the claude executable, or null if not found.
     * Result is cached after first successful detection.
     */
    public static String detect() {
        if (cachedPath != null) return cachedPath;

        // 1. Try via PATH (where/which)
        String fromPath = findViaPath();
        if (fromPath != null) {
            cachedPath = fromPath;
            MechanismAIChatAssistant.LOGGER.info("[AI] Found Claude CLI via PATH: {}", cachedPath);
            return cachedPath;
        }

        // 2. Try common install locations
        for (String candidate : commonLocations()) {
            if (new File(candidate).isFile()) {
                cachedPath = candidate;
                MechanismAIChatAssistant.LOGGER.info("[AI] Found Claude CLI at: {}", cachedPath);
                return cachedPath;
            }
        }

        MechanismAIChatAssistant.LOGGER.error("[AI] Claude CLI not found. Install it from https://claude.ai/download");
        return null;
    }

    private static String findViaPath() {
        try {
            List<String> cmd = IS_WINDOWS
                    ? List.of("cmd.exe", "/c", "where", "claude")
                    : List.of("sh", "-c", "which claude");

            Process p = new ProcessBuilder(cmd).start();
            boolean done = p.waitFor(5, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return null; }

            String result = new String(p.getInputStream().readAllBytes()).trim().lines().findFirst().orElse("").trim();
            if (!result.isBlank() && new File(result).isFile()) return result;
        } catch (Exception ignored) {}
        return null;
    }

    private static List<String> commonLocations() {
        String home = System.getProperty("user.home", "");
        if (IS_WINDOWS) {
            String appData = System.getenv().getOrDefault("APPDATA", home + "\\AppData\\Roaming");
            String localAppData = System.getenv().getOrDefault("LOCALAPPDATA", home + "\\AppData\\Local");
            return List.of(
                    home + "\\.local\\bin\\claude.exe",
                    appData + "\\npm\\claude.cmd",
                    appData + "\\npm\\claude.exe",
                    localAppData + "\\Programs\\claude\\claude.exe",
                    "C:\\Program Files\\claude\\claude.exe"
            );
        } else {
            return List.of(
                    home + "/.local/bin/claude",
                    "/usr/local/bin/claude",
                    "/usr/bin/claude",
                    "/opt/homebrew/bin/claude"
            );
        }
    }
}
