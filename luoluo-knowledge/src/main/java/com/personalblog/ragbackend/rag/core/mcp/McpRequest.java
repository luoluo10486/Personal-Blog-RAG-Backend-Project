package com.personalblog.ragbackend.rag.core.mcp;

import java.util.Map;

public record McpRequest(
        String toolId,
        String userQuestion,
        Map<String, Object> parameters,
        String userId,
        String conversationId
) {
}
