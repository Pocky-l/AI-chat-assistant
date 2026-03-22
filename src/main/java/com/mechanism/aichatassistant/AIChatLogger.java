package com.mechanism.aichatassistant;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AIChatLogger {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static File getLogFile() {
        File logDir = new File("logs/ai_chat");
        if (!logDir.exists()) logDir.mkdirs();
        String fileName = "ai_chat_" + LocalDateTime.now().format(FILE_DATE_FORMAT) + ".log";
        return new File(logDir, fileName);
    }

    private static void write(String level, String message) {
        String line = "[" + LocalDateTime.now().format(DATE_FORMAT) + "] [" + level + "] " + message;
        MechanismAIChatAssistant.LOGGER.info(line);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(getLogFile(), true))) {
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            MechanismAIChatAssistant.LOGGER.error("Failed to write to AI chat log", e);
        }
    }

    public static void logQuestion(String playerName, String question) {
        write("QUESTION", playerName + " asked: " + question);
    }

    public static void logAnswer(String answer, int inputTokens, int outputTokens) {
        write("ANSWER", answer);
        write("TOKENS", "input=" + inputTokens + " output=" + outputTokens + " total=" + (inputTokens + outputTokens));
    }

    public static void logError(String error) {
        write("ERROR", error);
    }
}
