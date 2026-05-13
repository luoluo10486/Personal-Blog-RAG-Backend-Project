package com.personalblog.ragbackend.rag.core.mcp;

import java.util.Map;

public record MCPToolDescriptor(String toolId, String description, Map<String, MCPToolParameter> parameters) {
}
