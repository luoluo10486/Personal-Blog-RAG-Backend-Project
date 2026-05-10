package com.personalblog.ragbackend.mcp.core;

public interface McpToolExecutor {

    McpToolDefinition getToolDefinition();

    McpToolResponse execute(McpToolRequest request);

    default String getToolId() {
        return getToolDefinition().getToolId();
    }

    default boolean supports(McpToolRequest request) {
        return getToolId().equals(request.getToolId());
    }
}
