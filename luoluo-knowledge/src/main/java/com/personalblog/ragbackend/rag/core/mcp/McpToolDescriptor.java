package com.personalblog.ragbackend.rag.core.mcp;

import java.util.Map;

public record McpToolDescriptor(
        String toolId,
        String description,
        Map<String, McpToolParameter> parameters
) {
}
