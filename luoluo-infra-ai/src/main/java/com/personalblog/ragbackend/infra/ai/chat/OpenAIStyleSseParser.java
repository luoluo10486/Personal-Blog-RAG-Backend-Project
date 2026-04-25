package com.personalblog.ragbackend.infra.ai.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class OpenAIStyleSseParser {

    private static final String DATA_PREFIX = "data:";
    private static final String DONE_MARKER = "[DONE]";

    private OpenAIStyleSseParser() {
    }

    static ParsedEvent parseLine(String line, ObjectMapper objectMapper, boolean reasoningEnabled) {
        if (line == null || line.isBlank()) {
            return ParsedEvent.empty();
        }

        String payload = line.trim();
        if (payload.startsWith(DATA_PREFIX)) {
            payload = payload.substring(DATA_PREFIX.length()).trim();
        }
        if (DONE_MARKER.equalsIgnoreCase(payload)) {
            return ParsedEvent.done();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception ex) {
            return ParsedEvent.empty();
        }

        JsonNode choice = root.path("choices").path(0);
        String content = extractText(choice, "content");
        String reasoning = reasoningEnabled ? extractText(choice, "reasoning_content") : null;
        boolean completed = hasFinishReason(choice);
        return new ParsedEvent(content, reasoning, completed);
    }

    private static boolean hasFinishReason(JsonNode choice) {
        JsonNode finishReason = choice.path("finish_reason");
        return !finishReason.isMissingNode() && !finishReason.isNull();
    }

    private static String extractText(JsonNode choice, String fieldName) {
        JsonNode delta = choice.path("delta");
        String deltaText = nullableText(delta.path(fieldName));
        if (deltaText != null) {
            return deltaText;
        }
        JsonNode message = choice.path("message");
        return nullableText(message.path(fieldName));
    }

    private static String nullableText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isEmpty() ? null : value;
    }

    record ParsedEvent(String content, String reasoning, boolean completed) {
        static ParsedEvent empty() {
            return new ParsedEvent(null, null, false);
        }

        static ParsedEvent done() {
            return new ParsedEvent(null, null, true);
        }

        boolean hasContent() {
            return content != null && !content.isEmpty();
        }

        boolean hasReasoning() {
            return reasoning != null && !reasoning.isEmpty();
        }
    }
}
