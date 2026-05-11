package com.personalblog.ragbackend.knowledge.service.rag.mcp;

import java.util.List;
import java.util.Optional;

public interface McpToolRegistry {
    void register(McpToolExecutor executor);

    void unregister(String toolId);

    Optional<McpToolExecutor> getExecutor(String toolId);

    List<McpToolExecutor> listExecutors();
}
