package com.personalblog.ragbackend.ingestion.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class JsonResponseParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonResponseParser() {
    }

    public static List<String> parseStringList(String response) {
        if (response == null || response.isBlank()) {
            return Collections.emptyList();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(response);
            if (node.isArray()) {
                List<String> result = new ArrayList<>();
                for (JsonNode item : node) {
                    if (item != null && !item.isNull()) {
                        result.add(item.asText());
                    }
                }
                return result;
            }
        } catch (Exception ignored) {
        }
        return List.of(response.trim());
    }

    public static Map<String, Object> parseObject(String response) {
        if (response == null || response.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(response);
            if (node.isObject()) {
                return OBJECT_MAPPER.convertValue(node, Map.class);
            }
        } catch (Exception ignored) {
        }
        return Map.of("value", response.trim());
    }

    public static String prettyPrint(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }
}
