package com.personalblog.ragbackend.mcp.core;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DefaultMcpToolRegistry implements McpToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultMcpToolRegistry.class);

    private final Map<String, McpToolExecutor> executorMap = new ConcurrentHashMap<>();
    private final List<McpToolExecutor> autoDiscoveredExecutors;

    public DefaultMcpToolRegistry(List<McpToolExecutor> autoDiscoveredExecutors) {
        this.autoDiscoveredExecutors = autoDiscoveredExecutors;
    }

    @PostConstruct
    public void init() {
        if (autoDiscoveredExecutors == null || autoDiscoveredExecutors.isEmpty()) {
            log.info("MCP tool registry skipped, no executors found");
            return;
        }
        for (McpToolExecutor executor : autoDiscoveredExecutors) {
            register(executor);
        }
        log.info("MCP tool auto registration complete, registered {} tools", autoDiscoveredExecutors.size());
    }

    @Override
    public void register(McpToolExecutor executor) {
        String toolId = executor.getToolId();
        executorMap.put(toolId, executor);
        log.info("MCP tool registered, toolId={}", toolId);
    }

    @Override
    public void unregister(String toolId) {
        executorMap.remove(toolId);
    }

    @Override
    public Optional<McpToolExecutor> getExecutor(String toolId) {
        return Optional.ofNullable(executorMap.get(toolId));
    }

    @Override
    public List<McpToolDefinition> listAllTools() {
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
