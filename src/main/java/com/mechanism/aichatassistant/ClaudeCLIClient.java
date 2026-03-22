package com.mechanism.aichatassistant;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ClaudeCLIClient {

    /**
     * Sends a question to Claude via Claude CLI subprocess.
     * Writes the prompt to a temp UTF-8 file and pipes it as stdin
     * to avoid Windows encoding issues with non-ASCII characters.
     */
    public static CompletableFuture<ClaudeClient.AskResult> ask(String question, String systemPrompt) {
        return CompletableFuture.supplyAsync(() -> {
            File tempFile = null;
            try {
                String fullPrompt = systemPrompt + "\n\nQuestion: " + question;

                // Write prompt to a temp file with UTF-8 encoding
                tempFile = File.createTempFile("ai_prompt_", ".txt");
                Files.writeString(tempFile.toPath(), fullPrompt, StandardCharsets.UTF_8);

                String claudePath = Config.CLAUDE_CLI_PATH.get();
                ProcessBuilder pb = new ProcessBuilder(claudePath, "-p", fullPrompt);
                pb.redirectInput(tempFile); // pipe file as stdin
                pb.redirectErrorStream(false);
                Process process = pb.start();

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
            } finally {
                if (tempFile != null) tempFile.delete();
            }
        });
    }
}
