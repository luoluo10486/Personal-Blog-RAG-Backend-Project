package com.personalblog.ragbackend.rag.core.mcp;

import com.personalblog.ragbackend.rag.core.mcp.client.MCPClient;

/**
 * Compatibility layer keeping the old `RemoteMCPToolExecutor` type.
 * The real implementation now lives in `McpClientToolExecutor`.
 */
@Deprecated
public class RemoteMCPToolExecutor extends McpClientToolExecutor {

    public RemoteMCPToolExecutor(MCPClient mcpClient, MCPTool toolDefinition) {
        super(mcpClient, toolDefinition);
    }
}
