package com.mechanism.aichatassistant;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.ModList;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class ModListProvider {

    public record ModMeta(String modId, String displayName, String version, String description) {}

    /**
     * Returns all loaded mods via NeoForge API (fast, in-memory).
     */
    public static List<ModMeta> getLoadedMods() {
        return ModList.get().getMods().stream()
                .filter(mod -> !mod.getModId().equals("minecraft") && !mod.getModId().equals("neoforge"))
                .map(mod -> new ModMeta(
                        mod.getModId(),
                        mod.getDisplayName(),
                        mod.getVersion().toString(),
                        mod.getDescription()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Returns a short summary string of all loaded mods.
     */
    public static String getLoadedModsSummary() {
        return getLoadedMods().stream()
                .map(m -> m.displayName() + " [" + m.modId() + "] v" + m.version())
                .collect(Collectors.joining(", "));
    }

    /**
     * Reads mod metadata directly from .jar files in the given mods folder.
     */
    public static List<ModMeta> readModsFromFolder(File modsFolder) {
        List<ModMeta> result = new ArrayList<>();
        if (!modsFolder.exists() || !modsFolder.isDirectory()) return result;

        File[] jars = modsFolder.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jars == null) return result;

        for (File jar : jars) {
            try (JarFile jarFile = new JarFile(jar)) {
                var entry = jarFile.getEntry("META-INF/neoforge.mods.toml");
                if (entry == null) entry = jarFile.getEntry("META-INF/mods.toml");
                if (entry == null) continue;

                try (InputStream is = jarFile.getInputStream(entry)) {
                    String toml = new String(is.readAllBytes());
                    String modId = extractTomlValue(toml, "modId");
                    String version = extractTomlValue(toml, "version");
                    String displayName = extractTomlValue(toml, "displayName");
                    String description = extractTomlDescription(toml);
                    if (modId != null)
                        result.add(new ModMeta(modId, displayName, version, description));
                }
            } catch (Exception e) {
                MechanismAIChatAssistant.LOGGER.warn("Failed to read jar: {}", jar.getName());
            }
        }
        return result;
    }

    /**
     * Extracts useful command/item names from a mod's lang file inside its jar.
     * Returns up to 40 relevant entries.
     */
    public static List<String> extractUsefulStringsFromJar(JarFile jarFile, String modId) {
        List<String> result = new ArrayList<>();
        String[] langPaths = {
                "assets/" + modId + "/lang/en_us.json",
                "assets/" + modId + "/lang/en_US.json"
        };

        for (String path : langPaths) {
            var entry = jarFile.getEntry(path);
            if (entry == null) continue;

            try (InputStream is = jarFile.getInputStream(entry)) {
                byte[] bytes = is.readAllBytes();
                if (bytes.length > 100_000) break; // skip huge lang files

                JsonObject json = JsonParser.parseString(new String(bytes)).getAsJsonObject();
                int count = 0;
                for (var kv : json.entrySet()) {
                    String key = kv.getKey().toLowerCase();
                    if (key.contains("command") || key.contains("cmd") ||
                        key.contains("gui.title") || key.contains("item.") ||
                        key.contains("block.") || key.contains("tooltip")) {
                        JsonElement val = kv.getValue();
                        result.add(kv.getKey() + ": " + val.getAsString());
                        if (++count >= 40) break;
                    }
                }
            } catch (Exception e) {
                MechanismAIChatAssistant.LOGGER.warn("Failed to read lang for mod {}", modId);
            }
            break;
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

    private static String extractTomlDescription(String toml) {
        int start = toml.indexOf("'''");
        if (start == -1) return null;
        int end = toml.indexOf("'''", start + 3);
        if (end == -1) return null;
        String desc = toml.substring(start + 3, end).trim();
        return desc.length() > 300 ? desc.substring(0, 300) + "..." : desc;
    }
}
