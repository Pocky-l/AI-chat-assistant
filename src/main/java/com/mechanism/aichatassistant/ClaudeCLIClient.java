package com.mechanism.aichatassistant;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ClaudeCLIClient {

    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    public static CompletableFuture<ClaudeClient.AskResult> ask(String question, String systemPrompt) {
        return CompletableFuture.supplyAsync(() -> {
            Path tempInput = null;
            Process process = null;
            try {
                String configPath = Config.CLAUDE_CLI_PATH.get();
                String claudePath = (configPath == null || configPath.isBlank())
                        ? ClaudePathDetector.detect()
                        : configPath;
                if (claudePath == null) {
                    return new ClaudeClient.AskResult("[AI] Claude CLI not found. Install it from https://claude.ai/download", 0, 0);
                }

                // Write question to a temp file to handle UTF-8 correctly on Windows
                tempInput = Files.createTempFile("claude_input_", ".txt");
                Files.writeString(tempInput, question, StandardCharsets.UTF_8);

                List<String> cmd = new ArrayList<>();
                if (IS_WINDOWS) {
                    cmd.add("cmd.exe");
                    cmd.add("/c");
                }
                cmd.add(claudePath);
                cmd.add("--system-prompt");
                cmd.add(systemPrompt);
                cmd.add("-p"); // print mode, reads prompt from stdin

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(false);
                pb.redirectInput(tempInput.toFile()); // question via stdin only

                process = pb.start();

                // Capture stderr in background to prevent blocking
                final Process proc = process;
                java.io.ByteArrayOutputStream stderrCapture = new java.io.ByteArrayOutputStream();
                CompletableFuture<Void> stderrFuture = CompletableFuture.runAsync(() -> {
                    try { proc.getErrorStream().transferTo(stderrCapture); }
                    catch (Exception ignored) {}
                });

                byte[] outputBytes = process.getInputStream().readAllBytes();
                String output = new String(outputBytes, StandardCharsets.UTF_8)
                        .replaceAll("\u001B\\[[;\\d]*[A-Za-z]", "") // strip ANSI codes
                        .trim();

                boolean finished = process.waitFor(30, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return new ClaudeClient.AskResult("[AI] Request timed out.", 0, 0);
                }

                stderrFuture.join();
                String stderrOutput = stderrCapture.toString(StandardCharsets.UTF_8).trim();

                if (output.isBlank()) {
                    if (!stderrOutput.isBlank()) {
                        AIChatLogger.logError("Claude CLI stderr: " + stderrOutput);
                    }
                    return new ClaudeClient.AskResult("[AI] Empty response from Claude CLI.", 0, 0);
                }

                int estimatedTokens = output.split("\\s+").length;
                return new ClaudeClient.AskResult(output, 0, estimatedTokens);

            } catch (Exception e) {
                AIChatLogger.logError("Claude CLI error: " + e.getMessage());
                return new ClaudeClient.AskResult("[AI] Failed to run Claude CLI: " + e.getMessage(), 0, 0);
            } finally {
                if (process != null) process.destroyForcibly();
                if (tempInput != null) {
                    try { Files.deleteIfExists(tempInput); } catch (Exception ignored) {}
                }
            }
        });
    }
}
