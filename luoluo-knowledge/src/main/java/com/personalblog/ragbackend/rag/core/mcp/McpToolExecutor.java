package com.personalblog.ragbackend.rag.core.mcp;

public interface McpToolExecutor {
    String getToolId();

    McpToolDescriptor getToolDefinition();

    McpToolCallResult execute(McpRequest request);
}
