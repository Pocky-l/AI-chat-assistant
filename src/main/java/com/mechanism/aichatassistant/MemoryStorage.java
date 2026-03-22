package com.mechanism.aichatassistant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MemoryStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_ENTRIES = 100; // keep last 100 Q&A pairs

    private static File getMemoryFile() {
        File dir = new File("logs/ai_chat");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "memory.json");
    }

    /**
     * Saves a question-answer pair to memory.json.
     */
    public static void save(String playerName, String question, String answer) {
        File file = getMemoryFile();
        JsonArray entries = load(file);

        JsonObject entry = new JsonObject();
        entry.addProperty("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
        entry.addProperty("player", playerName);
        entry.addProperty("question", question);
        entry.addProperty("answer", answer);
        entries.add(entry);

        // Keep only the last MAX_ENTRIES
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }

        try {
            Files.writeString(file.toPath(), GSON.toJson(entries));
        } catch (IOException e) {
            MechanismAIChatAssistant.LOGGER.error("Failed to write memory file", e);
        }
    }

    /**
     * Searches for an existing answer to a similar question.
     * Returns the answer string, or null if not found.
     */
    public static String findAnswer(String question) {
        JsonArray entries = load(getMemoryFile());
        String normalizedQuestion = normalize(question);

        for (int i = entries.size() - 1; i >= 0; i--) {
            JsonObject entry = entries.get(i).getAsJsonObject();
            String stored = normalize(entry.get("question").getAsString());
            if (stored.equals(normalizedQuestion)) {
                return entry.get("answer").getAsString();
            }
        }
        return null;
    }

    private static String normalize(String text) {
        return text.toLowerCase().trim().replaceAll("[.,!?;:]", "").replaceAll("\\s+", " ");
    }

    /**
     * Returns the last N entries as a formatted string for use as AI context.
     */
    public static String getRecentHistory(int count) {
        JsonArray entries = load(getMemoryFile());
        if (entries.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("Recent Q&A history:\n");
        int start = Math.max(0, entries.size() - count);
        for (int i = start; i < entries.size(); i++) {
            JsonObject entry = entries.get(i).getAsJsonObject();
            sb.append("Q: ").append(entry.get("question").getAsString()).append("\n");
            sb.append("A: ").append(entry.get("answer").getAsString()).append("\n\n");
        }
        return sb.toString().trim();
    }

    private static JsonArray load(File file) {
        if (!file.exists()) return new JsonArray();
        try {
            String content = Files.readString(file.toPath());
            return JsonParser.parseString(content).getAsJsonArray();
        } catch (Exception e) {
            MechanismAIChatAssistant.LOGGER.warn("Failed to read memory file, starting fresh");
            return new JsonArray();
        }
    }
}
