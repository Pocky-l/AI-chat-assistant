package com.mechanism.aichatassistant;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> TRIGGER_PREFIX = BUILDER
            .comment("The symbol/prefix that triggers the AI chat assistant. Default is '?'")
            .define("triggerPrefix", "?");

    static final ModConfigSpec SPEC = BUILDER.build();
}
