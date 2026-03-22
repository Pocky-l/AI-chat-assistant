package com.mechanism.aichatassistant;

import net.neoforged.fml.ModList;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class ModListProvider {

    /**
     * Returns a formatted string with all loaded mods and their versions (via NeoForge API).
     */
    public static String getLoadedMods() {
        return ModList.get().getMods().stream()
                .filter(mod -> !mod.getModId().equals("minecraft") && !mod.getModId().equals("neoforge"))
                .map(mod -> mod.getModId() + " (" + mod.getVersion() + ")")
                .collect(Collectors.joining(", "));
    }

    /**
     * Returns the total count of loaded mods (excluding minecraft and neoforge).
     */
    public static int getModCount() {
        return (int) ModList.get().getMods().stream()
                .filter(mod -> !mod.getModId().equals("minecraft") && !mod.getModId().equals("neoforge"))
                .count();
    }

    /**
     * Reads mod metadata directly from .jar files in the given mods folder.
     * Useful for listing mods from the filesystem without the game running.
     */
    public static List<String> readModsFromFolder(File modsFolder) {
        List<String> result = new ArrayList<>();

        if (!modsFolder.exists() || !modsFolder.isDirectory()) return result;

        File[] jars = modsFolder.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jars == null) return result;

        for (File jar : jars) {
            try (JarFile jarFile = new JarFile(jar)) {
                var entry = jarFile.getEntry("META-INF/neoforge.mods.toml");
                if (entry == null) entry = jarFile.getEntry("META-INF/mods.toml");
                if (entry == null) {
                    result.add(jar.getName() + " (no mod metadata)");
                    continue;
                }

                try (InputStream is = jarFile.getInputStream(entry)) {
                    String toml = new String(is.readAllBytes());
                    String modId = extractTomlValue(toml, "modId");
                    String version = extractTomlValue(toml, "version");
                    String displayName = extractTomlValue(toml, "displayName");
                    result.add((displayName != null ? displayName : modId) + " [" + modId + "] v" + version);
                }
            } catch (Exception e) {
                result.add(jar.getName() + " (read error: " + e.getMessage() + ")");
            }
        }

        return result;
    }

    private static String extractTomlValue(String toml, String key) {
        for (String line : toml.split("\n")) {
            line = line.trim();
            if (line.startsWith(key + "=") || line.startsWith(key + " =")) {
                return line.substring(line.indexOf('=') + 1).trim()
                        .replaceAll("^\"|\"$", "");
            }
        }
        return null;
    }
}
