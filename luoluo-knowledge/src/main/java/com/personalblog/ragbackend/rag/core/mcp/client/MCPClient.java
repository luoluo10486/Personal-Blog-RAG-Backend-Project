package com.personalblog.ragbackend.rag.core.mcp.client;

import com.personalblog.ragbackend.rag.core.mcp.MCPTool;

import java.util.List;
import java.util.Map;

public interface MCPClient {
    boolean initialize();

    List<MCPTool> listTools();

    String callTool(String toolName, Map<String, Object> arguments);
}
