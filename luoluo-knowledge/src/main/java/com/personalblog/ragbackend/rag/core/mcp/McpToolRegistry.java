package com.personalblog.ragbackend.rag.core.mcp;

import java.util.List;
import java.util.Optional;

public interface MCPToolRegistry {
    void register(MCPToolExecutor executor);

    void unregister(String toolId);

    Optional<MCPToolExecutor> getExecutor(String toolId);

    List<MCPTool> listAllTools();

    List<MCPToolExecutor> listAllExecutors();

    boolean contains(String toolId);

    int size();
}
