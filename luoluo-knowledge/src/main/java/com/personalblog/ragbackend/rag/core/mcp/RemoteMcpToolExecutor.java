package com.personalblog.ragbackend.rag.core.mcp;

import cn.hutool.core.util.StrUtil;

import java.util.Map;

public class RemoteMcpToolExecutor implements McpToolExecutor {
    private final McpToolClient client;
    private final McpToolDescriptor tool;

    public RemoteMcpToolExecutor(McpToolClient client, McpToolDescriptor tool) {
        this.client = client;
        this.tool = tool;
    }

    @Override
    public String getToolId() {
        return tool == null ? "" : StrUtil.blankToDefault(tool.toolId(), "");
    }

    @Override
    public McpToolDescriptor getToolDefinition() {
        return tool;
    }

    @Override
    public McpToolCallResult execute(McpRequest request) {
        Map<String, Object> arguments = request == null || request.parameters() == null
                ? Map.of()
                : request.parameters();
        return client.callTool(
                getToolId(),
                arguments,
                request == null ? "" : request.userId(),
                request == null ? "" : request.conversationId(),
                request == null ? "" : request.userQuestion()
        );
    }
}
