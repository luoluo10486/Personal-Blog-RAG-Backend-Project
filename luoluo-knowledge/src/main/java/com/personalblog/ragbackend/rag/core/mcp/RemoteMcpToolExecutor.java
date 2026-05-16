package com.personalblog.ragbackend.rag.core.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Compatibility layer keeping the old `RemoteMCPToolExecutor` type.
 * The real implementation now lives in `McpClientToolExecutor`.
 */
@Deprecated
public class RemoteMCPToolExecutor extends McpClientToolExecutor {

    public RemoteMCPToolExecutor(McpSyncClient mcpClient, Tool toolDefinition, String serverUrl) {
        super(mcpClient, toolDefinition, serverUrl);
    }
}
