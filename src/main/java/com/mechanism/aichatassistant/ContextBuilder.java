package com.mechanism.aichatassistant;

public class ContextBuilder {

    private static final String BASE_PROMPT =
            "You are a helpful assistant embedded in a Minecraft server. " +
            "Answer the player's question based on the mods installed on this specific server. " +
            "Be concise — answers will be shown in game chat. " +
            "If the question is about a specific mod feature or command, give exact commands or steps. " +
            "If the relevant mod is not installed, say so. " +
            "Respond in the same language the player used.";

    /**
     * Builds a system prompt with mod context embedded.
     * Trims context if it exceeds the token budget.
     */
    public static String buildSystemPrompt(String modContext) {
        if (!Config.ENABLE_MOD_CONTEXT.get() || modContext == null || modContext.isBlank()) {
            return BASE_PROMPT;
        }

        int maxChars = Config.MAX_CONTEXT_TOKENS.get() * 4; // ~4 chars per token
        String trimmed = modContext.length() > maxChars
                ? modContext.substring(0, maxChars) + "\n[context trimmed]"
                : modContext;

        return BASE_PROMPT + "\n\nServer mod context:\n" + trimmed;
    }
}
