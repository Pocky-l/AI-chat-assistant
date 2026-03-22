package com.mechanism.aichatassistant;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ClaudeCLIClient {

    /**
     * Sends a question to Claude via Claude CLI subprocess.
     * Requires `claude` to be available in PATH.
     */
    public static CompletableFuture<ClaudeClient.AskResult> ask(String question, String systemPrompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String fullPrompt = systemPrompt + "\n\nQuestion: " + question;

                ProcessBuilder pb = new ProcessBuilder("claude", "-p", fullPrompt);
                pb.redirectErrorStream(false);
                Process process = pb.start();

                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }

                boolean finished = process.waitFor(30, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return new ClaudeClient.AskResult("[AI] Request timed out.", 0, 0);
                }

                String response = output.toString().trim();
                if (response.isBlank()) {
                    return new ClaudeClient.AskResult("[AI] Empty response from Claude CLI.", 0, 0);
                }

                // Claude CLI doesn't provide token counts, so we estimate
                int estimatedTokens = response.split("\\s+").length;
                return new ClaudeClient.AskResult(response, 0, estimatedTokens);

            } catch (Exception e) {
                AIChatLogger.logError("Claude CLI error: " + e.getMessage());
                return new ClaudeClient.AskResult("[AI] Failed to run Claude CLI: " + e.getMessage(), 0, 0);
            }
        });
    }
}
