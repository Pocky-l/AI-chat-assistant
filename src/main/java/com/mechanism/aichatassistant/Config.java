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

    public static final ModConfigSpec.BooleanValue ENABLE_MOD_CONTEXT = BUILDER
            .comment("Whether to analyze installed mods and include context in AI requests.")
            .define("enableModContext", true);

    public static final ModConfigSpec.IntValue MAX_CONTEXT_TOKENS = BUILDER
            .comment("Maximum number of tokens to use for mod context in AI requests.")
            .defineInRange("maxContextTokens", 1200, 100, 3000);

    public static final ModConfigSpec.ConfigValue<String> MODS_FOLDER = BUILDER
            .comment("Path to the mods folder. Used to read .jar files for mod context.")
            .define("modsFolder", "mods");

    static final ModConfigSpec SPEC = BUILDER.build();
}
