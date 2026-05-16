package com.personalblog.ragbackend.rag.core.mcp;

public interface McpToolExecutor {
    String getToolId();

    MCPTool getToolDefinition();

    MCPResponse execute(MCPRequest request);
}
