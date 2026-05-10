package com.personalblog.ragbackend.mcp.core;

import java.util.List;
import java.util.Optional;

public interface McpToolRegistry {

    void register(McpToolExecutor executor);

    void unregister(String toolId);

    Optional<McpToolExecutor> getExecutor(String toolId);

    List<McpToolDefinition> listAllTools();

    List<McpToolExecutor> listAllExecutors();

    boolean contains(String toolId);

    int size();
}
