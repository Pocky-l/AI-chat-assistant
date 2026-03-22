package com.mechanism.aichatassistant;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class ModContextExtractor {

    // Cache: normalized category key → context string
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    /**
     * Extracts relevant mod context for the given question.
     * Uses keyword matching to find relevant mods, then reads their .jar files.
     */
    public static String extractRelevantContext(String question) {
        Set<ModKeywordRegistry.ModCategory> categories = ModKeywordRegistry.findCategories(question);

        // Cache key based on found categories
        String cacheKey = categories.stream()
                .map(ModKeywordRegistry.ModCategory::name)
                .sorted()
                .collect(Collectors.joining(","));

        if (CACHE.containsKey(cacheKey)) {
            return CACHE.get(cacheKey);
        }

        // Get all loaded mods
        List<ModListProvider.ModMeta> allMods = ModListProvider.getLoadedMods();

        // If no categories matched, return short summary of all mods
        if (categories.isEmpty()) {
            String summary = buildShortSummary(allMods);
            CACHE.put(cacheKey, summary);
            return summary;
        }

        // Filter relevant mods
        List<ModListProvider.ModMeta> relevantMods = allMods.stream()
                .filter(mod -> ModKeywordRegistry.isModRelevant(mod.modId(), mod.displayName(), categories))
                .limit(3)
                .collect(Collectors.toList());

        StringBuilder context = new StringBuilder();
        context.append("Installed mods on this server: ").append(buildShortSummary(allMods)).append("\n\n");

        if (relevantMods.isEmpty()) {
            CACHE.put(cacheKey, context.toString());
            return context.toString();
        }

        context.append("Mods relevant to this question:\n");

        File modsFolder = new File(Config.MODS_FOLDER.get());
        for (ModListProvider.ModMeta mod : relevantMods) {
            context.append("- ").append(mod.displayName()).append(" [").append(mod.modId()).append("] v").append(mod.version()).append("\n");
            if (mod.description() != null && !mod.description().isBlank()) {
                context.append("  Description: ").append(mod.description().replace("\n", " ")).append("\n");
            }

            // Try to read lang strings from jar
            List<String> langStrings = readLangFromJar(modsFolder, mod.modId());
            if (!langStrings.isEmpty()) {
                context.append("  Key features/commands:\n");
                langStrings.forEach(s -> context.append("    ").append(s).append("\n"));
            }
        }

        String result = context.toString();
        CACHE.put(cacheKey, result);
        return result;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static String buildShortSummary(List<ModListProvider.ModMeta> mods) {
        return mods.stream()
                .map(m -> m.displayName() + " [" + m.modId() + "]")
                .collect(Collectors.joining(", "));
    }

    private static List<String> readLangFromJar(File modsFolder, String modId) {
        if (!modsFolder.exists()) return List.of();

        File[] jars = modsFolder.listFiles((dir, name) -> name.endsWith(".jar") &&
                name.toLowerCase().contains(modId.toLowerCase().replace("_", "")));
        if (jars == null || jars.length == 0) return List.of();

        try (JarFile jarFile = new JarFile(jars[0])) {
            return ModListProvider.extractUsefulStringsFromJar(jarFile, modId);
        } catch (Exception e) {
            MechanismAIChatAssistant.LOGGER.warn("Could not read lang from jar for mod {}", modId);
            return List.of();
        }
    }
}
