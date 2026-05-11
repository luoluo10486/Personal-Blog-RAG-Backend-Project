package com.personalblog.ragbackend.knowledge.service.rag.mcp;

import java.util.Map;

public record McpToolCallResult(
        String toolId,
        boolean success,
        String text,
        String errorMessage,
        Map<String, Object> arguments
) {
}
