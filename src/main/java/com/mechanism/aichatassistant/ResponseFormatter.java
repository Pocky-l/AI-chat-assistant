package com.mechanism.aichatassistant;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts Claude's markdown-like responses into formatted Minecraft Components.
 * Supports: **bold**, *italic*, `code`, bullet lists (- / *), numbered lists, headers (#).
 * Long responses are split into multiple lines for readability.
 */
public class ResponseFormatter {

    private static final Pattern BOLD_PATTERN   = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)");
    private static final Pattern CODE_PATTERN   = Pattern.compile("`([^`]+)`");

    /**
     * Splits the response into individual Component lines for chat output.
     * Each line is sent as a separate sendSystemMessage call.
     */
    public static List<Component> format(String aiName, String rawText) {
        List<Component> lines = new ArrayList<>();

        // Header prefix with gold color
        MutableComponent header = Component.literal("[" + aiName + "] ")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

        String[] paragraphs = rawText.split("\n");
        boolean firstLine = true;

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) continue;

            MutableComponent lineComponent = firstLine ? header.copy() : Component.literal("  ");
            firstLine = false;

            // Detect headers: # Title or ## Title
            if (trimmed.startsWith("###")) {
                lineComponent.append(
                    Component.literal(trimmed.substring(3).trim())
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                );
            } else if (trimmed.startsWith("##")) {
                lineComponent.append(
                    Component.literal(trimmed.substring(2).trim())
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                );
            } else if (trimmed.startsWith("#")) {
                lineComponent.append(
                    Component.literal(trimmed.substring(1).trim())
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                );
            // Detect bullet lists: - item or * item
            } else if (trimmed.matches("^[-*] .+")) {
                lineComponent.append(
                    Component.literal("• ").withStyle(ChatFormatting.YELLOW)
                );
                lineComponent.append(parseInline(trimmed.substring(2)));
            // Detect numbered lists: 1. item
            } else if (trimmed.matches("^\\d+\\. .+")) {
                int dotIndex = trimmed.indexOf(". ");
                String number = trimmed.substring(0, dotIndex + 1);
                String content = trimmed.substring(dotIndex + 2);
                lineComponent.append(
                    Component.literal(number + " ").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                );
                lineComponent.append(parseInline(content));
            // Regular paragraph
            } else {
                lineComponent.append(parseInline(trimmed));
            }

            lines.add(lineComponent);
        }

        return lines.isEmpty()
            ? List.of(header.copy().append(Component.literal(rawText).withStyle(ChatFormatting.WHITE)))
            : lines;
    }

    /**
     * Parses inline markdown (**bold**, *italic*, `code`) from a text segment
     * and returns a styled Component.
     */
    private static MutableComponent parseInline(String text) {
        MutableComponent result = Component.empty();
        int cursor = 0;

        // Collect all inline spans sorted by start position
        List<Span> spans = new ArrayList<>();

        findSpans(spans, text, BOLD_PATTERN,   Style.BOLD);
        findSpans(spans, text, CODE_PATTERN,   Style.CODE);
        findSpans(spans, text, ITALIC_PATTERN, Style.ITALIC);

        // Sort by start position, remove overlaps
        spans.sort((a, b) -> a.start != b.start ? a.start - b.start : b.end - a.end);
        List<Span> filtered = new ArrayList<>();
        int lastEnd = 0;
        for (Span span : spans) {
            if (span.start >= lastEnd) {
                filtered.add(span);
                lastEnd = span.end;
            }
        }

        for (Span span : filtered) {
            if (cursor < span.start) {
                result.append(Component.literal(text.substring(cursor, span.start))
                    .withStyle(ChatFormatting.WHITE));
            }
            result.append(applyStyle(span.content, span.style));
            cursor = span.end;
        }

        if (cursor < text.length()) {
            result.append(Component.literal(text.substring(cursor))
                .withStyle(ChatFormatting.WHITE));
        }

        return result;
    }

    private enum Style { BOLD, ITALIC, CODE }

    private static void findSpans(List<ResponseFormatter.Span> spans, String text, Pattern pattern, Style style) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            spans.add(new Span(m.start(), m.end(), m.group(1), style));
        }
    }

    // Inner record needed because Java doesn't allow local records in static methods referencing outer enum
    private record Span(int start, int end, String content, Style style) {}

    private static MutableComponent applyStyle(String text, Style style) {
        return switch (style) {
            case BOLD   -> Component.literal(text).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
            case ITALIC -> Component.literal(text).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            case CODE   -> Component.literal(text).withStyle(ChatFormatting.AQUA);
        };
    }
}
