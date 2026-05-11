package com.personalblog.ragbackend.knowledge.service.rag.mcp;

import java.util.Map;

public record McpToolDescriptor(
        String toolId,
        String description,
        Map<String, McpToolParameter> parameters
) {
}
