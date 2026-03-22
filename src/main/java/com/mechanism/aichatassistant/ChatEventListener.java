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

        // TODO: заменить на реальный ответ от AI
        String response = "[AI] Thinking...";

        Component responseComponent = Component.literal(response);
        for (ServerPlayer player : event.getPlayer().getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(responseComponent);
        }
    }
}
