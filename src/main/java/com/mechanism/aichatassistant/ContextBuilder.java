package com.mechanism.aichatassistant;

public class ContextBuilder {

    private static final String BASE_PROMPT =
            "You are a helpful assistant embedded in a Minecraft server.\n" +
            "Answer the player's question based on the mods installed on this specific server.\n" +
            "Respond in the same language the player used.\n" +
            "\n" +
            "FORMATTING RULES (mandatory):\n" +
            "- Keep answers as short as possible without losing meaning.\n" +
            "- Use a header only if necessary: # Title (renders as green bold).\n" +
            "- For step-by-step instructions, number each step: 1. Step or use bullet: - Step. Each step on its own line.\n" +
            "- Highlight key items, names, and important words in **bold**.\n" +
            "- Use *italics* for emphasis on a single word.\n" +
            "- Use `backticks` for commands, item IDs, or code snippets (renders as aqua).\n" +
            "- Every paragraph or list item must be on its own line — never merge multiple points into one line.\n" +
            "\n" +
            "CONTENT RULES:\n" +
            "- If the question is about a specific mod feature or command, give exact commands or steps.\n" +
            "- If you are not certain about an item name or feature, look it up from the mod context provided — do not invent names.\n" +
            "- If the relevant mod is not installed, say so.";

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
