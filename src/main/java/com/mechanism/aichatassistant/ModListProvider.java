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
                if (bytes.length > 500_000) break; // skip extremely huge lang files

                JsonObject json = JsonParser.parseString(new String(bytes)).getAsJsonObject();

                // Pass 1: high-value entries — tooltips and commands first
                for (var kv : json.entrySet()) {
                    String key = kv.getKey().toLowerCase();
                    if (key.contains("tooltip") || key.contains("command") || key.contains("cmd") || key.contains("gui.title")) {
                        result.add(kv.getKey() + ": " + kv.getValue().getAsString());
                        if (result.size() >= 30) break;
                    }
                }

                // Pass 2: fill remaining slots with item/block names
                for (var kv : json.entrySet()) {
                    if (result.size() >= 40) break;
                    String key = kv.getKey().toLowerCase();
                    if ((key.contains("item.") || key.contains("block.")) && key.startsWith(modId.toLowerCase())) {
                        result.add(kv.getKey() + ": " + kv.getValue().getAsString());
                    }
                }
            } catch (Exception e) {
                MechanismAIChatAssistant.LOGGER.warn("Failed to read lang for mod {}", modId);
            }
            break;
        }
        return result;
    }

    /**
     * Finds and formats crafting recipes from a mod's jar that match any of the given keywords.
     * Keywords should be English item name fragments (e.g. "geiger", "hazmat", "dosimeter").
     * Returns a list of human-readable recipe strings.
     */
    public static List<String> extractRecipesFromJar(JarFile jarFile, String modId, List<String> keywords) {
        List<String> result = new ArrayList<>();
        String recipePrefix = "data/" + modId + "/recipe/";

        jarFile.stream()
            .filter(e -> e.getName().startsWith(recipePrefix) && e.getName().endsWith(".json"))
            .forEach(entry -> {
                String fileName = entry.getName()
                    .substring(recipePrefix.length())
                    .replace(".json", "")
                    .replace("/", "_");

                boolean matches = keywords.stream()
                    .anyMatch(kw -> fileName.toLowerCase().contains(kw.toLowerCase()));
                if (!matches) return;

                try (InputStream is = jarFile.getInputStream(entry)) {
                    String raw = new String(is.readAllBytes());
                    JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
                    String formatted = formatRecipe(fileName, json);
                    if (formatted != null) result.add(formatted);
                } catch (Exception ignored) {}
            });

        return result;
    }

    /**
     * Formats a Minecraft crafting recipe JSON into a human-readable string.
     * Supports shaped and shapeless crafting. Skips unknown/complex types.
     */
    private static String formatRecipe(String name, JsonObject json) {
        String type = json.has("type") ? json.get("type").getAsString() : "";

        if (type.contains("crafting_shaped")) {
            StringBuilder sb = new StringBuilder();
            sb.append("Recipe [").append(name).append("] (shaped crafting):\n");

            // Key: letter → item/tag name
            if (json.has("key")) {
                json.getAsJsonObject("key").entrySet().forEach(kv -> {
                    JsonElement val = kv.getValue();
                    String ingredient = resolveIngredient(val);
                    sb.append("  ").append(kv.getKey()).append(" = ").append(ingredient).append("\n");
                });
            }

            // Pattern
            if (json.has("pattern")) {
                sb.append("  Pattern:\n");
                json.getAsJsonArray("pattern").forEach(row ->
                    sb.append("    ").append(row.getAsString()).append("\n")
                );
            }

            // Result
            if (json.has("result")) {
                String resultItem = resolveIngredient(json.get("result"));
                int count = json.getAsJsonObject("result").has("count")
                    ? json.getAsJsonObject("result").get("count").getAsInt() : 1;
                sb.append("  Result: ").append(resultItem).append(" x").append(count).append("\n");
            }

            return sb.toString();

        } else if (type.contains("crafting_shapeless")) {
            StringBuilder sb = new StringBuilder();
            sb.append("Recipe [").append(name).append("] (shapeless crafting):\n");

            if (json.has("ingredients")) {
                sb.append("  Ingredients: ");
                List<String> ingredients = new ArrayList<>();
                json.getAsJsonArray("ingredients").forEach(el ->
                    ingredients.add(resolveIngredient(el))
                );
                sb.append(String.join(", ", ingredients)).append("\n");
            }

            if (json.has("result")) {
                String resultItem = resolveIngredient(json.get("result"));
                sb.append("  Result: ").append(resultItem).append("\n");
            }

            return sb.toString();
        }

        // Unknown recipe type — skip
        return null;
    }

    // Common tag → readable name mappings
    private static final java.util.Map<String, String> TAG_NAMES = java.util.Map.ofEntries(
        java.util.Map.entry("c:ingots/lead",          "Lead Ingot"),
        java.util.Map.entry("c:ingots/iron",          "Iron Ingot"),
        java.util.Map.entry("c:ingots/gold",          "Gold Ingot"),
        java.util.Map.entry("c:ingots/copper",        "Copper Ingot"),
        java.util.Map.entry("c:ingots/tin",           "Tin Ingot"),
        java.util.Map.entry("c:ingots/osmium",        "Osmium Ingot"),
        java.util.Map.entry("c:ingots/steel",         "Steel Ingot"),
        java.util.Map.entry("c:gems/diamond",         "Diamond"),
        java.util.Map.entry("c:gems/emerald",         "Emerald"),
        java.util.Map.entry("c:gems/quartz",          "Quartz"),
        java.util.Map.entry("c:dusts/redstone",       "Redstone Dust"),
        java.util.Map.entry("c:dusts/glowstone",      "Glowstone Dust"),
        java.util.Map.entry("c:circuits/basic",       "Basic Control Circuit"),
        java.util.Map.entry("c:circuits/advanced",    "Advanced Control Circuit"),
        java.util.Map.entry("c:circuits/elite",       "Elite Control Circuit"),
        java.util.Map.entry("c:circuits/ultimate",    "Ultimate Control Circuit"),
        java.util.Map.entry("c:glass_panes",          "Glass Pane"),
        java.util.Map.entry("c:glass_blocks",         "Glass Block"),
        java.util.Map.entry("c:storage_blocks/iron",  "Iron Block"),
        java.util.Map.entry("c:storage_blocks/gold",  "Gold Block"),
        java.util.Map.entry("minecraft:redstone",     "Redstone"),
        java.util.Map.entry("minecraft:iron_ingot",   "Iron Ingot"),
        java.util.Map.entry("minecraft:gold_ingot",   "Gold Ingot"),
        java.util.Map.entry("minecraft:glass_pane",   "Glass Pane")
    );

    private static String resolveIngredient(JsonElement el) {
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("tag")) {
                String tag = obj.get("tag").getAsString();
                return TAG_NAMES.getOrDefault(tag, tag);
            }
            if (obj.has("item")) return simplifyId(obj.get("item").getAsString());
            if (obj.has("id"))   return simplifyId(obj.get("id").getAsString());
        }
        return el.toString();
    }

    private static String simplifyId(String id) {
        // "mekanism:geiger_counter" → "Geiger Counter"
        int colon = id.indexOf(':');
        String name = colon >= 0 ? id.substring(colon + 1) : id;
        return java.util.Arrays.stream(name.split("_"))
            .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
            .collect(java.util.stream.Collectors.joining(" "));
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
