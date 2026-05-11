package com.personalblog.ragbackend.knowledge.service.rag.mcp;

import java.util.Map;

public record McpRequest(
        String toolId,
        String userQuestion,
        Map<String, Object> parameters,
        String userId,
        String conversationId
) {
}
