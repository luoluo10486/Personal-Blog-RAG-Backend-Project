package com.personalblog.ragbackend.knowledge.service.rag.mcp;

import java.util.List;

public record McpToolParameter(
        String type,
        String description,
        boolean required,
        Object defaultValue,
        List<String> enumValues
) {
}
