package com.mechanism.aichatassistant;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class ClaudeClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-haiku-4-5-20251001";
    private static final String SYSTEM_PROMPT =
            "You are a friendly and helpful assistant inside a Minecraft game. " +
            "Answer questions on any topic — history, science, cooking, games, life advice, and more. " +
            "Keep your answers short and clear, since they will be displayed in a game chat. " +
            "Do not mention programming, code, or software development unless the player explicitly asks about it. " +
            "Respond in the same language the player used.";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public record AskResult(String text, int inputTokens, int outputTokens) {
        public int totalTokens() { return inputTokens + outputTokens; }
    }

    /**
     * Sends a question to Claude and returns the response with token usage asynchronously.
     */
    public static CompletableFuture<AskResult> ask(String question) {
        String apiKey = Config.ANTHROPIC_API_KEY.get();

        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.completedFuture(
                    new AskResult("[AI] API key is not set. Add it to the config file.", 0, 0));
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("max_tokens", 512);
        body.addProperty("system", SYSTEM_PROMPT);

        JsonArray messages = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", question);
        messages.add(userMessage);
        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .timeout(Duration.ofSeconds(30))
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        String err = "API error " + response.statusCode() + ": " + response.body();
                        AIChatLogger.logError(err);
                        return new AskResult("[AI] Error: " + response.statusCode(), 0, 0);
                    }
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    String text = json.getAsJsonArray("content")
                            .get(0).getAsJsonObject()
                            .get("text").getAsString();

                    JsonObject usage = json.getAsJsonObject("usage");
                    int inputTokens = usage.get("input_tokens").getAsInt();
                    int outputTokens = usage.get("output_tokens").getAsInt();

                    return new AskResult(text, inputTokens, outputTokens);
                })
                .exceptionally(e -> {
                    AIChatLogger.logError("Request failed: " + e.getMessage());
                    return new AskResult("[AI] Failed to connect to AI service.", 0, 0);
                });
    }
}
