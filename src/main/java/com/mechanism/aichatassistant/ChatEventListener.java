package com.mechanism.aichatassistant;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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

        // Check memory for existing answer
        String cached = MemoryStorage.findAnswer(question);
        if (cached != null) {
            AIChatLogger.logAnswer("[from memory] " + cached, 0, 0);
            sendResponse(event.getPlayer(), aiName, cached);
            return;
        }

        // Step 1: extract mod context asynchronously
        CompletableFuture.supplyAsync(() -> ModContextExtractor.extractRelevantContext(question))
            .orTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .exceptionally(e -> {
                MechanismAIChatAssistant.LOGGER.warn("Mod context extraction failed: {}", e.getMessage());
                return "";
            })
            // Step 2: build prompt and ask Claude (via CLI or API)
            .thenCompose(context -> {
                String systemPrompt = ContextBuilder.buildSystemPrompt(context);
                if (Config.USE_CLI.get()) {
                    return ClaudeCLIClient.ask(question, systemPrompt);
                }
                return ClaudeClient.ask(question, systemPrompt);
            })
            // Step 3: send response to all players
            .thenAccept(result -> {
                AIChatLogger.logAnswer(result.text(), result.inputTokens(), result.outputTokens());
                if (!result.text().startsWith("[AI] ")) {
                    MemoryStorage.save(playerName, question, result.text());
                }
                sendResponse(event.getPlayer(), aiName, result.text());
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
                return new String[]{shortText, fullText};
            } else {
                // Only SHORT, no FULL block
                return new String[]{raw.substring(6).trim(), null};
            }
        }
        // Fallback: Claude didn't follow the format — show as full only
        return new String[]{null, raw};
    }

    /**
     * Sends the response to all players on the server.
     * Shows short answer + "показать подробнее" button if FULL block exists.
     */
    private static void sendResponse(ServerPlayer source, String aiName, String rawText) {
        String[] parts = parseResponse(rawText);
        String shortText = parts[0];
        String fullText  = parts[1];

        List<Component> lines;

        if (shortText != null && fullText != null) {
            // Save full answer and show short + button
            int detailId = DetailStorage.save(fullText);
            lines = ResponseFormatter.formatShort(aiName, shortText, detailId);
        } else if (shortText != null) {
            // No full block — just show the short answer
            lines = ResponseFormatter.format(aiName, shortText);
        } else {
            // Fallback — show everything
            lines = ResponseFormatter.format(aiName, rawText);
        }

        for (Component line : lines) {
            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                player.sendSystemMessage(line);
            }
        }
    }
}
