package com.personalblog.ragbackend.rag.core.mcp;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultMcpToolRegistry implements McpToolRegistry {

    private final Map<String, McpToolExecutor> executorMap = new ConcurrentHashMap<>();
    private final List<McpToolExecutor> autoDiscoveredExecutors;

    @PostConstruct
    public void init() {
        if (CollectionUtils.isEmpty(autoDiscoveredExecutors)) {
            log.info("MCP tool registry skipped, no executors discovered");
            return;
        }

        for (McpToolExecutor executor : autoDiscoveredExecutors) {
            register(executor);
        }
        log.info("MCP tool registry initialized, registered {} tools", autoDiscoveredExecutors.size());
    }

    @Override
    public void register(McpToolExecutor executor) {
        if (executor == null || executor.getToolDefinition() == null) {
            log.warn("Ignore empty MCP executor");
            return;
        }

        String toolId = executor.getToolId();
        if (toolId == null || toolId.isBlank()) {
            log.warn("Ignore MCP executor with blank toolId");
            return;
        }

        McpToolExecutor existing = executorMap.put(toolId, executor);
        if (existing != null) {
            log.warn("MCP tool already exists, replaced toolId={}", toolId);
        }
    }

    @Override
    public void unregister(String toolId) {
        McpToolExecutor removed = executorMap.remove(toolId);
        if (removed != null) {
            log.info("MCP tool unregistered, toolId={}", toolId);
        }
    }

    @Override
    public Optional<McpToolExecutor> getExecutor(String toolId) {
        return Optional.ofNullable(executorMap.get(toolId));
    }

    @Override
    public List<MCPTool> listAllTools() {
        return executorMap.values().stream()
                .map(McpToolExecutor::getToolDefinition)
                .toList();
    }

    @Override
    public List<McpToolExecutor> listAllExecutors() {
        return new ArrayList<>(executorMap.values());
    }

    @Override
    public boolean contains(String toolId) {
        return executorMap.containsKey(toolId);
    }

    @Override
    public int size() {
        return executorMap.size();
    }
}
