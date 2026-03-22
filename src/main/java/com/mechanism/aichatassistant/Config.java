package com.mechanism.aichatassistant;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> TRIGGER_PREFIX = BUILDER
            .comment("The symbol/prefix that triggers the AI chat assistant. Default is '?'")
            .define("triggerPrefix", "?");

    public static final ModConfigSpec.ConfigValue<String> ANTHROPIC_API_KEY = BUILDER
            .comment("Your Anthropic API key. Get one at https://console.anthropic.com/")
            .define("apiKey", "");

    public static final ModConfigSpec.ConfigValue<String> AI_NAME = BUILDER
            .comment("The name displayed in chat when the AI responds.")
            .define("aiName", "Assistant");

    static final ModConfigSpec SPEC = BUILDER.build();
}
