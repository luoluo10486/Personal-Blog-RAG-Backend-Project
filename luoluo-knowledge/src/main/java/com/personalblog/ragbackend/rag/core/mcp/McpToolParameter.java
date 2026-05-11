package com.personalblog.ragbackend.rag.core.mcp;

import java.util.List;

public record McpToolParameter(
        String type,
        String description,
        boolean required,
        Object defaultValue,
        List<String> enumValues
) {
}
