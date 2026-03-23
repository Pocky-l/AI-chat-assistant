package com.mechanism.aichatassistant;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * Registers the /aidetail <id> command.
 * When a player clicks "показать подробнее" in chat, this command is executed
 * and the full AI response is displayed to them.
 */
@EventBusSubscriber(modid = MechanismAIChatAssistant.MODID)
public class DetailCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("aidetail")
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                    .executes(ctx -> {
                        int id = IntegerArgumentType.getInteger(ctx, "id");
                        String fullText = DetailStorage.get(id);

                        if (fullText == null) {
                            ctx.getSource().sendFailure(
                                Component.literal("Ответ не найден или устарел.")
                            );
                            return 0;
                        }

                        String aiName = Config.AI_NAME.get();
                        List<Component> lines = ResponseFormatter.format(aiName, fullText);
                        for (Component line : lines) {
                            ctx.getSource().sendSuccess(() -> line, false);
                        }
                        return 1;
                    })
                )
        );
    }
}
