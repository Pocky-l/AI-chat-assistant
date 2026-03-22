package com.mechanism.aichatassistant;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ClaudeCLIClient {

    /**
     * Sends a question to Claude via Claude CLI subprocess.
     * System prompt is passed via -p flag (ASCII only),
     * the question is written to stdin in UTF-8 to avoid Windows encoding issues.
     */
    public static CompletableFuture<ClaudeClient.AskResult> ask(String question, String systemPrompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String claudePath = Config.CLAUDE_CLI_PATH.get();
                ProcessBuilder pb = new ProcessBuilder(claudePath, "-p", systemPrompt);
                pb.redirectErrorStream(false);
                Process process = pb.start();

                // Write the question to stdin in UTF-8
                try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                    writer.write(question);
                    writer.flush();
                }

                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
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

                int estimatedTokens = response.split("\\s+").length;
                return new ClaudeClient.AskResult(response, 0, estimatedTokens);

            } catch (Exception e) {
                AIChatLogger.logError("Claude CLI error: " + e.getMessage());
                return new ClaudeClient.AskResult("[AI] Failed to run Claude CLI: " + e.getMessage(), 0, 0);
            }
        });
    }
}
