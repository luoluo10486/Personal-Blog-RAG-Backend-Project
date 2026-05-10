package com.personalblog.ragbackend.mcp.core;

import java.util.function.Function;

public class LambdaMcpToolExecutor implements McpToolExecutor {

    private final McpToolDefinition toolDefinition;
    private final Function<McpToolRequest, McpToolResponse> executor;

    public LambdaMcpToolExecutor(McpToolDefinition toolDefinition, Function<McpToolRequest, McpToolResponse> executor) {
        this.toolDefinition = toolDefinition;
        this.executor = executor;
    }

    @Override
    public McpToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public McpToolResponse execute(McpToolRequest request) {
        return executor.apply(request);
    }
}
