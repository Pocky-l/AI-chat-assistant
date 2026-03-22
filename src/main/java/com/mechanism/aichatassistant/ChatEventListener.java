package com.mechanism.aichatassistant;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;

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
        event.getPlayer().sendSystemMessage(Component.literal("[" + aiName + "] Thinking..."));

        // Step 1: extract mod context asynchronously
        CompletableFuture.supplyAsync(() -> ModContextExtractor.extractRelevantContext(question))
            .orTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .exceptionally(e -> {
                MechanismAIChatAssistant.LOGGER.warn("Mod context extraction failed: {}", e.getMessage());
                return "";
            })
            // Step 2: build prompt and ask Claude
            .thenCompose(context -> {
                String systemPrompt = ContextBuilder.buildSystemPrompt(context);
                return ClaudeClient.ask(question, systemPrompt);
            })
            // Step 3: send response to all players
            .thenAccept(result -> {
                AIChatLogger.logAnswer(result.text(), result.inputTokens(), result.outputTokens());
                String formatted = "[" + aiName + "] " + result.text();
                Component response = Component.literal(formatted);
                for (ServerPlayer player : event.getPlayer().getServer().getPlayerList().getPlayers()) {
                    player.sendSystemMessage(response);
                }
            })
            .exceptionally(e -> {
                AIChatLogger.logError("Pipeline failed: " + e.getMessage());
                return null;
            });
    }
}
