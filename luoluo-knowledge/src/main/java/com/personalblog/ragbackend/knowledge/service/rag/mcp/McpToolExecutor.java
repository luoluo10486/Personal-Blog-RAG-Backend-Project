package com.personalblog.ragbackend.knowledge.service.rag.mcp;

public interface McpToolExecutor {
    String getToolId();

    McpToolDescriptor getToolDefinition();

    McpToolCallResult execute(McpRequest request);
}
