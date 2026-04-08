package com.mechanism.aichatassistant;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = MechanismAIChatAssistant.MODID)
public class ChatEventListener {

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        String message = event.getMessage().getString();
        String prefix = Config.TRIGGER_PREFIX.get();

        if (!message.startsWith(prefix)) return;

        String question = message.substring(prefix.length()).trim();
        if (question.isBlank()) return;

        String playerName = event.getPlayer().getName().getString();
        String aiName = Config.AI_NAME.get();

        AIChatLogger.logQuestion(playerName, question);

        // Pre-check: if question explicitly names a known mod that isn't installed → respond immediately
        String notInstalledMod = detectMentionedNotInstalledMod(question);
        if (notInstalledMod != null) {
            AIChatLogger.logSearch("Pre-check: mod '" + notInstalledMod + "' mentioned but not installed — skipping Claude");
            String msg = "SHORT: Мод **" + notInstalledMod + "** не установлен на данном сервере.";
            sendResponse(event.getPlayer(), aiName, msg);
            return;
        }

        // Check memory for existing answer
        String cached = MemoryStorage.findAnswer(question);
        if (cached != null) {
            AIChatLogger.logSearch("Answer found in memory cache — skipping mod search and API call");
            AIChatLogger.logAnswer("[from memory] " + cached, 0, 0);
            sendResponse(event.getPlayer(), aiName, cached);
            return;
        }

        String memoryHistory = MemoryStorage.getRecentHistory(5);
        if (!memoryHistory.isBlank()) {
            AIChatLogger.logSearch("Memory: loaded " + memoryHistory.split("Q:").length + " recent entries as context");
        } else {
            AIChatLogger.logSearch("Memory: no history yet");
        }
        AIChatLogger.logSearch("No exact memory match — starting context extraction pipeline");

        // Step 1: extract mod context asynchronously
        CompletableFuture.supplyAsync(() -> ModContextExtractor.extractRelevantContext(question))
            .orTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .exceptionally(e -> {
                AIChatLogger.logSearch("Mod context extraction timed out or failed: " + e.getMessage());
                return "";
            })
            // Step 2: build prompt and ask Claude (via CLI or API)
            .thenCompose(context -> {
                String systemPrompt = ContextBuilder.buildSystemPrompt(context, memoryHistory);
                int contextLen = context == null ? 0 : context.length();
                AIChatLogger.logSearch("Context built (" + contextLen + " chars) — sending to Claude via " + (Config.USE_CLI.get() ? "CLI" : "API"));
                if (contextLen > 0) {
                    String preview = context.length() > 300 ? context.substring(0, 300) + "..." : context;
                    AIChatLogger.logSearch("Context preview:\n" + preview);
                }
                String recipeSnippet = extractRecipeSnippet(context);
                String wrappedQuestion = recipeSnippet.isEmpty()
                    ? "SHORT: <1 sentence answer, max 15 words>\nFULL:\n<detailed answer>\n\nQuestion: " + question
                    : "The following crafting recipe is confirmed on this server:\n" +
                      recipeSnippet + "\n" +
                      "SHORT: <1 sentence answer, max 15 words>\nFULL:\n<detailed answer>\n\nQuestion: " + question;
                if (Config.USE_CLI.get()) {
                    return ClaudeCLIClient.ask(wrappedQuestion, systemPrompt);
                }
                return ClaudeClient.ask(wrappedQuestion, systemPrompt);
            })
            // Step 3: send response to all players
            .thenAccept(result -> {
                AIChatLogger.logSearch("Response received from Claude — delivering to players");
                AIChatLogger.logAnswer(result.text(), result.inputTokens(), result.outputTokens());
                // If we have a recipe, override SHORT with Java-generated answer
                String recipeSnippet2 = extractRecipeSnippet(ModContextExtractor.getLastContext());
                String javaShort = buildShortFromRecipe(recipeSnippet2);
                boolean isError = result.text().startsWith("[AI] ");
                if (!javaShort.isEmpty()) {
                    AIChatLogger.logSearch("SHORT generated from recipe data (Java): " + javaShort);
                    if (!isError) MemoryStorage.save(playerName, question, "SHORT: " + javaShort + "\nFULL:\n" + result.text());
                    sendResponseWithShort(event.getPlayer(), aiName, javaShort, result.text());
                } else {
                    AIChatLogger.logSearch("SHORT extracted from Claude response (first sentence)");
                    if (!isError) MemoryStorage.save(playerName, question, result.text());
                    sendResponse(event.getPlayer(), aiName, result.text());
                }
            })
            .exceptionally(e -> {
                AIChatLogger.logError("Pipeline failed: " + e.getMessage());
                return null;
            });
    }

    /**
     * Parses Claude's response into short and full parts.
     * Expected format:
     *   SHORT: <one sentence>
     *   FULL:
     *   <detailed answer>
     */
    private static String[] parseResponse(String raw) {
        if (raw.startsWith("SHORT:")) {
            int fullIndex = raw.indexOf("\nFULL:");
            if (fullIndex != -1) {
                String shortText = raw.substring(6, fullIndex).trim();
                String fullText = raw.substring(fullIndex + 6).trim();
                return new String[]{shortText, fullText.isBlank() ? null : fullText};
            }
            return new String[]{raw.substring(6).trim(), null};
        }
        // Fallback: no format — show first line as short
        String[] lines = raw.trim().split("\n", 2);
        return new String[]{lines[0].trim(), lines.length > 1 ? lines[1].trim() : null};
    }

    /** Extracts the 'Crafting recipes:' section from context, if present. */
    private static String extractRecipeSnippet(String context) {
        if (context == null) return "";
        int start = context.indexOf("Crafting recipes:");
        if (start == -1) return "";
        String sub = context.substring(start);
        int end = sub.indexOf("\n\n");
        return end > 0 ? sub.substring(0, end).trim() : sub.trim();
    }

    /**
     * Builds a short Russian sentence from a recipe snippet.
     * Example: "Нужно: 4x Lead Ingot, 1x Redstone Dust — shaped крафт в верстаке."
     */
    private static final java.util.Map<String, String> RU_NAMES = java.util.Map.ofEntries(
        java.util.Map.entry("Lead Ingot",               "свинцовый слиток"),
        java.util.Map.entry("Iron Ingot",               "железный слиток"),
        java.util.Map.entry("Gold Ingot",               "золотой слиток"),
        java.util.Map.entry("Copper Ingot",             "медный слиток"),
        java.util.Map.entry("Tin Ingot",                "оловянный слиток"),
        java.util.Map.entry("Osmium Ingot",             "осмиевый слиток"),
        java.util.Map.entry("Steel Ingot",              "стальной слиток"),
        java.util.Map.entry("Diamond",                  "алмаз"),
        java.util.Map.entry("Emerald",                  "изумруд"),
        java.util.Map.entry("Quartz",                   "кварц"),
        java.util.Map.entry("Redstone Dust",            "красная пыль"),
        java.util.Map.entry("Redstone",                 "красная пыль"),
        java.util.Map.entry("Glowstone Dust",           "светящаяся пыль"),
        java.util.Map.entry("Basic Control Circuit",    "базовая схема управления"),
        java.util.Map.entry("Advanced Control Circuit", "улучшенная схема управления"),
        java.util.Map.entry("Elite Control Circuit",    "элитная схема управления"),
        java.util.Map.entry("Ultimate Control Circuit", "высшая схема управления"),
        java.util.Map.entry("Glass Pane",               "стеклянная панель"),
        java.util.Map.entry("Glass Block",              "стекло"),
        java.util.Map.entry("Iron Block",               "блок железа"),
        java.util.Map.entry("Gold Block",               "блок золота")
    );

    static String buildShortFromRecipe(String recipeSnippet) {
        if (recipeSnippet.isBlank()) return "";

        // Collect ingredient key→name mappings
        java.util.Map<String, String> keyToName = new java.util.LinkedHashMap<>();
        for (String line : recipeSnippet.split("\n")) {
            // Matches lines like "  I = Lead Ingot" or "  C = Basic Control Circuit"
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^\\s+([A-Za-z])\\s*=\\s*(.+)$").matcher(line);
            if (m.matches()) keyToName.put(m.group(1), m.group(2).trim());
        }

        // Count letter occurrences in pattern lines
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        boolean inPattern = false;
        for (String line : recipeSnippet.split("\n")) {
            if (line.trim().equals("Pattern:")) { inPattern = true; continue; }
            if (inPattern) {
                if (line.trim().startsWith("Result:")) break;
                for (char c : line.toCharArray()) {
                    String key = String.valueOf(c);
                    if (keyToName.containsKey(key))
                        counts.merge(key, 1, Integer::sum);
                }
            }
        }

        if (counts.isEmpty() || keyToName.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("Нужно: ");
        boolean first = true;
        for (java.util.Map.Entry<String, Integer> e : counts.entrySet()) {
            String name = keyToName.getOrDefault(e.getKey(), e.getKey());
            String ruName = RU_NAMES.getOrDefault(name, name);
            if (!first) sb.append(", ");
            sb.append(e.getValue()).append("x ").append(ruName);
            first = false;
        }
        sb.append(" — крафт в верстаке.");
        return sb.toString();
    }

    private static String extractFirstSentence(String text) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (t.isEmpty()) continue;
            // Skip pure headers
            if (t.startsWith("#") || t.startsWith("---")) continue;

            // If line ends with ':' — it's an intro like "you will need:", collect what follows
            if (t.endsWith(":")) {
                StringBuilder sb = new StringBuilder(t.replaceAll(":$", "") + ": ");
                int collected = 0;
                for (int j = i + 1; j < lines.length && collected < 4; j++) {
                    String item = lines[j].trim()
                        .replaceAll("^[-*•]\\s*", "")   // strip bullet
                        .replaceAll("\\*\\*(.+?)\\*\\*", "$1"); // strip bold
                    if (item.isEmpty()) break;
                    if (collected > 0) sb.append(", ");
                    sb.append(item);
                    collected++;
                }
                return sb.toString();
            }

            // Normal sentence — find end at . ! ?
            int end = -1;
            for (int k = 0; k < t.length(); k++) {
                char c = t.charAt(k);
                if (c == '.' || c == '!' || c == '?') { end = k + 1; break; }
            }
            String sentence = end > 0 ? t.substring(0, end) : t;
            return sentence.length() > 100 ? sentence.substring(0, 100) + "…" : sentence;
        }
        return lines[0].trim();
    }

    /**
     * Sends the response to all players on the server.
     * Shows short answer + "показать подробнее" button if FULL block exists.
     */
    private static void sendResponseWithShort(ServerPlayer source, String aiName, String shortText, String fullText) {
        int detailId = DetailStorage.save(fullText);
        List<Component> lines = ResponseFormatter.formatShort(aiName, shortText, detailId);
        for (Component line : lines)
            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers())
                player.sendSystemMessage(line);
    }

    /**
     * Known specific mod names (keyword → display name).
     * Only unambiguous, specific mod names — no generic terms like "magic" or "storage".
     */
    private static final Map<String, String> KNOWN_MOD_KEYWORDS = Map.ofEntries(
        Map.entry("mekanism",           "Mekanism"),
        Map.entry("botania",            "Botania"),
        Map.entry("thaumcraft",         "Thaumcraft"),
        Map.entry("ae2",                "Applied Energistics 2"),
        Map.entry("appliedenergistics", "Applied Energistics 2"),
        Map.entry("create",             "Create"),
        Map.entry("thermal",            "Thermal Expansion"),
        Map.entry("buildcraft",         "BuildCraft"),
        Map.entry("enderio",            "Ender IO"),
        Map.entry("industrialcraft",    "IndustrialCraft"),
        Map.entry("ftbquests",          "FTB Quests"),
        Map.entry("griefprevention",    "GriefPrevention"),
        Map.entry("worldguard",         "WorldGuard"),
        Map.entry("towny",              "Towny"),
        Map.entry("lycanites",          "Lycanites Mobs"),
        Map.entry("iceandfire",         "Ice and Fire"),
        Map.entry("bloodmagic",         "Blood Magic"),
        Map.entry("ars nouveau",        "Ars Nouveau"),
        Map.entry("ars_nouveau",        "Ars Nouveau"),
        Map.entry("tinkers",            "Tinkers' Construct"),
        Map.entry("tconstruct",         "Tinkers' Construct"),
        Map.entry("immersiveengineering","Immersive Engineering"),
        Map.entry("kubejs",             "KubeJS"),
        Map.entry("pneumaticcraft",     "PneumaticCraft"),
        Map.entry("cofh",               "CoFH Core"),
        Map.entry("rftools",            "RFTools"),
        Map.entry("draconicevolution",  "Draconic Evolution"),
        Map.entry("extrabotany",        "Extra Botany"),
        Map.entry("pam",                "Pam's HarvestCraft"),
        Map.entry("harvestcraft",       "Pam's HarvestCraft")
    );

    /**
     * Checks if the question explicitly mentions a known mod that is NOT installed on the server.
     * Returns the display name of the uninstalled mod, or null if no match.
     */
    private static String detectMentionedNotInstalledMod(String question) {
        String lower = question.toLowerCase();

        Set<String> loadedIds = ModListProvider.getLoadedMods().stream()
                .map(m -> m.modId().toLowerCase())
                .collect(Collectors.toSet());

        for (Map.Entry<String, String> entry : KNOWN_MOD_KEYWORDS.entrySet()) {
            String keyword = entry.getKey();
            if (!lower.contains(keyword)) continue;

            // Check if any loaded mod ID contains this keyword
            boolean installed = loadedIds.stream().anyMatch(id -> id.contains(keyword));
            if (!installed) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static void sendResponse(ServerPlayer source, String aiName, String rawText) {
        String[] parts = parseResponse(rawText);
        String shortText = parts[0];
        String fullText  = parts[1];

        List<Component> lines;

        if (shortText != null && fullText != null) {
            // Normal case: SHORT + FULL — show short with button
            int detailId = DetailStorage.save(fullText);
            lines = ResponseFormatter.formatShort(aiName, shortText, detailId);
        } else if (shortText != null) {
            // Only SHORT, no FULL — just show it
            lines = ResponseFormatter.format(aiName, shortText);
        } else {
            // Fallback (old cache or Claude ignored format):
            // Show first line as short + button with full text
            int detailId = DetailStorage.save(rawText);
            String firstLine = rawText.split("\n")[0].trim();
            lines = ResponseFormatter.formatShort(aiName, firstLine, detailId);
        }

        for (Component line : lines) {
            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                player.sendSystemMessage(line);
            }
        }
    }
}
