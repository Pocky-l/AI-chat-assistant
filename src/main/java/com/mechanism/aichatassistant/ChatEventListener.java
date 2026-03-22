package com.mechanism.aichatassistant;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;

@EventBusSubscriber(modid = MechanismAIChatAssistant.MODID)
public class ChatEventListener {

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        String message = event.getMessage().getString();
        String prefix = Config.TRIGGER_PREFIX.get();

        if (!message.startsWith(prefix)) return;

        String question = message.substring(prefix.length()).trim();
        if (question.isBlank()) return;

        String aiName = Config.AI_NAME.get();

        String playerName = event.getPlayer().getName().getString();
        AIChatLogger.logQuestion(playerName, question);

        // Notify the player that the AI is processing
        event.getPlayer().sendSystemMessage(Component.literal("[" + aiName + "] Thinking..."));

        ClaudeClient.ask(question).thenAccept(result -> {
            AIChatLogger.logAnswer(result.text(), result.inputTokens(), result.outputTokens());
            String formatted = "[" + aiName + "] " + result.text();
            Component responseComponent = Component.literal(formatted);
            for (ServerPlayer player : event.getPlayer().getServer().getPlayerList().getPlayers()) {
                player.sendSystemMessage(responseComponent);
            }
        });
    }
}
