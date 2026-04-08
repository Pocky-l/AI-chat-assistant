package com.mechanism.aichatassistant;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class ModContextExtractor {

    // Cache: normalized category key → context string
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    private static volatile String lastContext = "";

    // Russian name aliases for mod items (modId → list of "русское название = English Name" pairs)
    private static final Map<String, List<String>> RUSSIAN_ALIASES = Map.of(
        "mekanism", List.of(
            "дозиметр = Dosimeter",
            "счётчик гейгера = Geiger Counter",
            "счётчик гейгера = Geiger Counter",
            "костюм химзащиты / хазмат = Hazmat Suit",
            "ядерный реактор = Fission Reactor",
            "ядерный синтез = Fusion Reactor",
            "очиститель радиации = Radiation Scrubber",
            "энергокуб = Energy Cube",
            "универсальный кабель = Universal Cable",
            "обогатитель = Enrichment Chamber",
            "дробилка = Crusher",
            "металлургическая инфузионная камера = Metallurgic Infuser",
            "химический инжектор = Chemical Injection Chamber",
            "бочка ядерных отходов = Radioactive Waste Barrel",
            "турбина = Industrial Turbine",
            "электролитический сепаратор = Electrolytic Separator"
        )
    );

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
            AIChatLogger.logSearch("Context cache HIT for categories: [" + cacheKey + "]");
            return CACHE.get(cacheKey);
        }

        AIChatLogger.logSearch("Context cache MISS — starting mod search");

        // Get all loaded mods
        List<ModListProvider.ModMeta> allMods = ModListProvider.getLoadedMods();
        AIChatLogger.logSearch("Loaded mods total: " + allMods.size());

        // If no categories matched, return short summary of all mods
        if (categories.isEmpty()) {
            AIChatLogger.logSearch("No categories matched — returning full mod list summary");
            String summary = buildShortSummary(allMods);
            CACHE.put(cacheKey, summary);
            return summary;
        }

        AIChatLogger.logSearch("Matched categories: " + categories.stream()
                .map(ModKeywordRegistry.ModCategory::name)
                .collect(Collectors.joining(", ")));

        // Filter relevant mods
        List<ModListProvider.ModMeta> relevantMods = allMods.stream()
                .filter(mod -> ModKeywordRegistry.isModRelevant(mod.modId(), mod.displayName(), categories))
                .limit(3)
                .collect(Collectors.toList());

        if (relevantMods.isEmpty()) {
            AIChatLogger.logSearch("No relevant mods found for these categories");
        } else {
            AIChatLogger.logSearch("Relevant mods found: " + relevantMods.stream()
                    .map(m -> m.displayName() + " [" + m.modId() + "]")
                    .collect(Collectors.joining(", ")));
        }

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

            // Append Russian name aliases if available for this mod
            List<String> aliases = RUSSIAN_ALIASES.get(mod.modId());
            if (aliases != null) {
                context.append("  Russian item name aliases:\n");
                aliases.forEach(a -> context.append("    ").append(a).append("\n"));
            }

            // Try to read lang strings from jar
            List<String> langStrings = readLangFromJar(modsFolder, mod.modId());
            if (!langStrings.isEmpty()) {
                AIChatLogger.logSearch("Extracted " + langStrings.size() + " lang strings from jar: " + mod.modId());
                langStrings.forEach(s -> AIChatLogger.logSearch("  >> " + s));
                context.append("  Key features/commands:\n");
                langStrings.forEach(s -> context.append("    ").append(s).append("\n"));
            } else {
                AIChatLogger.logSearch("No lang strings found in jar: " + mod.modId());
            }

            // Try to find crafting recipes matching the question
            List<String> recipeKeywords = buildRecipeKeywords(question, mod.modId());
            List<String> recipes = readRecipesFromJar(modsFolder, mod.modId(), recipeKeywords);
            if (!recipes.isEmpty()) {
                AIChatLogger.logSearch("Found " + recipes.size() + " recipes for keywords: " + recipeKeywords);
                context.append("  Crafting recipes:\n");
                recipes.forEach(r -> context.append(r).append("\n"));
            } else {
                AIChatLogger.logSearch("No recipes found for keywords: " + recipeKeywords);
            }
        }

        // Read server-side recipe overrides: KubeJS scripts and datapacks
        List<String> generalKeywords = buildGeneralKeywords(question);
        if (!generalKeywords.isEmpty()) {
            List<String> kubeRecipes = ServerModFilesReader.readKubeJSRecipes(generalKeywords);
            if (!kubeRecipes.isEmpty()) {
                AIChatLogger.logSearch("Found " + kubeRecipes.size() + " KubeJS recipe snippets");
                context.append("\nKubeJS recipe overrides (server scripts):\n");
                kubeRecipes.forEach(r -> context.append(r).append("\n"));
            }

            List<String> datapackRecipes = ServerModFilesReader.readDatapackRecipes(generalKeywords);
            if (!datapackRecipes.isEmpty()) {
                AIChatLogger.logSearch("Found " + datapackRecipes.size() + " datapack recipe overrides");
                context.append("\nDatapack recipe overrides:\n");
                datapackRecipes.forEach(r -> context.append(r).append("\n"));
            }
        }

        String result = context.toString();
        CACHE.put(cacheKey, result);
        lastContext = result;
        return result;
    }

    public static String getLastContext() { return lastContext; }

    public static void clearCache() {
        CACHE.clear();
    }

    private static String buildShortSummary(List<ModListProvider.ModMeta> mods) {
        return mods.stream()
                .map(m -> m.displayName() + " [" + m.modId() + "]")
                .collect(Collectors.joining(", "));
    }

    /**
     * Builds a list of English keywords to search recipe files with.
     * Combines words from the question with English equivalents from Russian aliases.
     */
    private static List<String> buildRecipeKeywords(String question, String modId) {
        List<String> keywords = new ArrayList<>();

        // Add words from the question itself (English words, 4+ chars)
        for (String word : question.toLowerCase().split("\\s+")) {
            if (word.length() >= 4 && word.matches("[a-z]+")) {
                keywords.add(word);
            }
        }

        // Add English equivalents from Russian aliases
        List<String> aliases = RUSSIAN_ALIASES.get(modId);
        if (aliases != null) {
            String questionLower = question.toLowerCase();
            for (String alias : aliases) {
                // alias format: "русское название = English Name"
                String[] parts = alias.split("=", 2);
                if (parts.length == 2) {
                    String russian = parts[0].trim().toLowerCase();
                    String english = parts[1].trim().toLowerCase().replace(" ", "_");
                    // Check if any Russian word from alias appears in question
                    for (String ruWord : russian.split("[/\\s]+")) {
                        if (question.toLowerCase().contains(ruWord) && ruWord.length() >= 4) {
                            keywords.add(english);
                            break;
                        }
                    }
                }
            }
        }

        return keywords;
    }

    /**
     * Builds general keywords from the question for KubeJS/datapack searches.
     * Extracts all English words 4+ chars, plus English equivalents from all Russian aliases.
     */
    private static List<String> buildGeneralKeywords(String question) {
        List<String> keywords = new ArrayList<>();
        String lower = question.toLowerCase();

        for (String word : lower.split("\\s+")) {
            if (word.length() >= 4 && word.matches("[a-z]+")) {
                keywords.add(word);
            }
        }

        // Add English equivalents from all known aliases
        for (List<String> aliases : RUSSIAN_ALIASES.values()) {
            for (String alias : aliases) {
                String[] parts = alias.split("=", 2);
                if (parts.length == 2) {
                    String russian = parts[0].trim().toLowerCase();
                    String english = parts[1].trim().toLowerCase().replace(" ", "_");
                    for (String ruWord : russian.split("[/\\s]+")) {
                        if (ruWord.length() >= 4 && lower.contains(ruWord)) {
                            keywords.add(english);
                            break;
                        }
                    }
                }
            }
        }

        return keywords;
    }

    private static List<String> readRecipesFromJar(File modsFolder, String modId, List<String> keywords) {
        if (keywords.isEmpty()) return List.of();

        File[] jars = modsFolder.listFiles((dir, name) -> name.endsWith(".jar") &&
                name.toLowerCase().contains(modId.toLowerCase().replace("_", "")));
        if (jars == null || jars.length == 0) return List.of();

        try (JarFile jarFile = new JarFile(jars[0])) {
            return ModListProvider.extractRecipesFromJar(jarFile, modId, keywords);
        } catch (Exception e) {
            MechanismAIChatAssistant.LOGGER.warn("Could not read recipes from jar for mod {}", modId);
            return List.of();
        }
    }

    private static List<String> readLangFromJar(File modsFolder, String modId) {
        AIChatLogger.logSearch("Searching jar for [" + modId + "] in: " + modsFolder.getAbsolutePath());
        if (!modsFolder.exists()) {
            AIChatLogger.logSearch("Mods folder NOT FOUND: " + modsFolder.getAbsolutePath());
            return List.of();
        }

        File[] jars = modsFolder.listFiles((dir, name) -> name.endsWith(".jar") &&
                name.toLowerCase().contains(modId.toLowerCase().replace("_", "")));
        if (jars == null || jars.length == 0) {
            AIChatLogger.logSearch("No jar file found matching modId: " + modId);
            return List.of();
        }
        AIChatLogger.logSearch("Found jar: " + jars[0].getName());

        try (JarFile jarFile = new JarFile(jars[0])) {
            return ModListProvider.extractUsefulStringsFromJar(jarFile, modId);
        } catch (Exception e) {
            MechanismAIChatAssistant.LOGGER.warn("Could not read lang from jar for mod {}", modId);
            return List.of();
        }
    }
}
