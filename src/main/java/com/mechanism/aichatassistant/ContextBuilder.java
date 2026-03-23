package com.mechanism.aichatassistant;

public class ContextBuilder {

    private static final String BASE_PROMPT =
            "You are a helpful assistant embedded in a Minecraft server. You answer questions for regular players — not admins or server operators.\n" +
            "Respond in the same language the player used.\n" +
            "\n" +
            "CONTENT RULES (strict):\n" +
            "- Answer only what the player asked. No extra tips, no admin commands, no operator-only features.\n" +
            "- Keep answers as short as possible — every word must earn its place.\n" +
            "- Only mention items, features, or commands that exist in the mod context provided. Do not invent names.\n" +
            "- If the relevant mod is not installed or you are not sure, say so in one sentence.\n" +
            "\n" +
            "FORMATTING RULES (strict):\n" +
            "- NEVER use triple backticks (``` or ```java etc). They are not supported and will appear as raw symbols.\n" +
            "- Use a header only if truly necessary: # Title\n" +
            "- For steps, use: 1. Step or - Step. Each step on its own line.\n" +
            "- Wrap key item names and important words in **bold**.\n" +
            "- Use *italics* sparingly, only for single-word emphasis.\n" +
            "- Use `backticks` only for short commands or IDs (single backtick pairs only).\n" +
            "- Every paragraph or list item must be on its own line.";

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
