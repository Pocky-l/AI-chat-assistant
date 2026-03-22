package com.mechanism.aichatassistant;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = MechanismAIChatAssistant.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = MechanismAIChatAssistant.MODID, value = Dist.CLIENT)
public class MechanismAIChatAssistantClient {
    public MechanismAIChatAssistantClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        MechanismAIChatAssistant.LOGGER.info("Mechanism AI Chat Assistant client setup!");
    }
}
