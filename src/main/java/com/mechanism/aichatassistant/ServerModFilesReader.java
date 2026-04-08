package com.mechanism.aichatassistant;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads server-side mod configuration files that can override or add recipes:
 * - KubeJS scripts (kubejs/server_scripts/, kubejs/startup_scripts/)
 * - Datapack recipe JSONs (world/datapacks/[pack]/data/[ns]/recipe/*.json)
 */
public class ServerModFilesReader {

    /**
     * Reads KubeJS server scripts and returns recipe-relevant snippets matching keywords.
     */
    public static List<String> readKubeJSRecipes(List<String> keywords) {
        List<String> result = new ArrayList<>();
        File kubejsRoot = new File(Config.KUBEJS_FOLDER.get());
        if (!kubejsRoot.exists()) {
            AIChatLogger.logSearch("KubeJS folder not found: " + kubejsRoot.getAbsolutePath());
            return result;
        }

        String[] scriptDirs = {"server_scripts", "startup_scripts"};
        for (String dir : scriptDirs) {
            File scriptsDir = new File(kubejsRoot, dir);
            if (!scriptsDir.exists() || !scriptsDir.isDirectory()) continue;

            scanKubeJSDir(scriptsDir, keywords, result);
            if (result.size() >= 3) break;
        }

        return result;
    }

    private static void scanKubeJSDir(File dir, List<String> keywords, List<String> result) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (result.size() >= 3) return;

            if (file.isDirectory()) {
                scanKubeJSDir(file, keywords, result);
                continue;
            }

            String name = file.getName();
            if (!name.endsWith(".js") && !name.endsWith(".ts")) continue;

            try {
                String content = Files.readString(file.toPath());

                // Must contain recipe-related calls
                boolean hasRecipes = content.contains("ServerEvents.recipes")
                        || content.contains("events.listen('recipes'")
                        || content.contains("onEvent('recipes")
                        || content.contains("event.shaped(")
                        || content.contains("event.shapeless(")
                        || content.contains("event.remove(")
                        || content.contains("event.replaceInput(")
                        || content.contains("event.addShapeless(")
                        || content.contains("event.addShaped(");
                if (!hasRecipes) continue;

                // Must match at least one keyword (or keywords list is empty → include all)
                boolean matches = keywords.isEmpty() || keywords.stream()
                        .anyMatch(kw -> content.toLowerCase().contains(kw.toLowerCase()));
                if (!matches) continue;

                AIChatLogger.logSearch("KubeJS recipe script matched: " + file.getName());

                // Extract lines that contain keywords, with 2-line context window
                String[] lines = content.split("\n");
                List<String> extracted = extractRelevantLines(lines, keywords, 20);

                if (extracted.isEmpty()) continue;

                StringBuilder snippet = new StringBuilder();
                snippet.append("KubeJS [").append(file.getName()).append("]:\n");
                extracted.forEach(l -> snippet.append("  ").append(l).append("\n"));
                result.add(snippet.toString());

            } catch (Exception e) {
                MechanismAIChatAssistant.LOGGER.warn("Failed to read KubeJS script: {}", file.getName());
            }
        }
    }

    /**
     * Extracts lines matching keywords with surrounding context, up to maxLines total.
     */
    private static List<String> extractRelevantLines(String[] lines, List<String> keywords, int maxLines) {
        List<String> result = new ArrayList<>();
        boolean[] included = new boolean[lines.length];

        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase();
            boolean keywordHit = keywords.stream().anyMatch(kw -> lower.contains(kw.toLowerCase()));
            boolean recipeCall = lower.contains("event.shaped") || lower.contains("event.shapeless")
                    || lower.contains("event.remove") || lower.contains("event.replaceInput");
            if (keywordHit || recipeCall) {
                // Include 2 lines before and 3 lines after
                for (int j = Math.max(0, i - 2); j <= Math.min(lines.length - 1, i + 3); j++) {
                    included[j] = true;
                }
            }
        }

        int count = 0;
        for (int i = 0; i < lines.length && count < maxLines; i++) {
            if (included[i]) {
                result.add(lines[i].trim());
                count++;
            }
        }
        return result;
    }

    /**
     * Reads datapack recipe JSONs from world/datapacks matching keywords.
     */
    public static List<String> readDatapackRecipes(List<String> keywords) {
        List<String> result = new ArrayList<>();
        File datapacksRoot = new File(Config.DATAPACKS_FOLDER.get());

        if (!datapacksRoot.exists() || !datapacksRoot.isDirectory()) {
            AIChatLogger.logSearch("Datapacks folder not found: " + datapacksRoot.getAbsolutePath());
            return result;
        }

        File[] packs = datapacksRoot.listFiles(File::isDirectory);
        if (packs == null) return result;

        for (File pack : packs) {
            File dataDir = new File(pack, "data");
            if (!dataDir.exists()) continue;

            File[] namespaceDirs = dataDir.listFiles(File::isDirectory);
            if (namespaceDirs == null) continue;

            for (File ns : namespaceDirs) {
                for (String recipeDirName : new String[]{"recipe", "recipes"}) {
                    File recipeDir = new File(ns, recipeDirName);
                    if (!recipeDir.exists()) continue;

                    scanDatapackRecipeDir(recipeDir, keywords, result);
                    if (result.size() >= 10) return result;
                }
            }
        }

        return result;
    }

    private static void scanDatapackRecipeDir(File dir, List<String> keywords, List<String> result) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (result.size() >= 10) return;

            if (file.isDirectory()) {
                scanDatapackRecipeDir(file, keywords, result);
                continue;
            }

            if (!file.getName().endsWith(".json")) continue;

            String fileName = file.getName().replace(".json", "");
            boolean matches = keywords.isEmpty() || keywords.stream()
                    .anyMatch(kw -> fileName.toLowerCase().contains(kw.toLowerCase()));
            if (!matches) continue;

            try {
                String raw = Files.readString(file.toPath());
                JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
                String formatted = ModListProvider.formatRecipe(fileName, json);
                if (formatted != null) {
                    AIChatLogger.logSearch("Datapack recipe matched: " + file.getName());
                    result.add("(datapack/" + dir.getParentFile().getName() + ") " + formatted);
                }
            } catch (Exception ignored) {}
        }
    }
}
