package com.personalblog.ragbackend.rag.core.mcp;

import com.personalblog.ragbackend.rag.core.mcp.client.MCPClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RemoteMCPToolExecutor implements MCPToolExecutor {

    private final MCPClient mcpClient;
    private final MCPTool toolDefinition;

    @Override
    public MCPTool getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String getToolId() {
        return toolDefinition.getToolId();
    }

    @Override
    public MCPResponse execute(MCPRequest request) {
        long start = System.currentTimeMillis();
        try {
            String result = mcpClient.callTool(toolDefinition.getToolId(), request.getParameters());
            long costMs = System.currentTimeMillis() - start;

            if (result == null) {
                MCPResponse response = MCPResponse.error(request.getToolId(), "REMOTE_CALL_FAILED", "remote tool call failed");
                response.setCostMs(costMs);
                return response;
            }

            MCPResponse response = MCPResponse.success(request.getToolId(), result);
            response.setCostMs(costMs);
            return response;
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - start;
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("remote MCP tool call failed, toolId={}, reason={}", request.getToolId(), reason);
            MCPResponse response = MCPResponse.error(request.getToolId(), "REMOTE_CALL_ERROR", reason);
            response.setCostMs(costMs);
            return response;
        }
    }
}
