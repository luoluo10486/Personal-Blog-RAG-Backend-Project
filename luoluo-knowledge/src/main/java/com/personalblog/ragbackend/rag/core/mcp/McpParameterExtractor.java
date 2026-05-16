package com.personalblog.ragbackend.rag.core.mcp;

import java.util.Map;

public interface McpParameterExtractor {
    Map<String, Object> extractParameters(String userQuestion, MCPTool tool);

    default Map<String, Object> extractParameters(String userQuestion, MCPTool tool, String customPromptTemplate) {
        return extractParameters(userQuestion, tool);
    }
}
