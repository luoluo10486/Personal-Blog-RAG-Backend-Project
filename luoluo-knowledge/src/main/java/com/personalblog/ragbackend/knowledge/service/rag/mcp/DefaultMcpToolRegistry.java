package com.personalblog.ragbackend.knowledge.service.rag.mcp;

import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.knowledge.config.RagMcpProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class DefaultMcpToolRegistry implements McpToolRegistry {
    private final ConcurrentMap<String, McpToolExecutor> executorMap = new ConcurrentHashMap<>();
    private final RagMcpProperties properties;
    private final McpToolClient client;

    public DefaultMcpToolRegistry(List<McpToolExecutor> autoDiscoveredExecutors,
                                  RagMcpProperties properties,
                                  McpToolClient client) {
        this.properties = properties;
        this.client = client;
        if (autoDiscoveredExecutors != null) {
            autoDiscoveredExecutors.forEach(this::register);
        }
    }

    @Override
    public void register(McpToolExecutor executor) {
        if (executor == null || executor.getToolDefinition() == null || StrUtil.isBlank(executor.getToolId())) {
            return;
        }
        executorMap.put(executor.getToolId(), executor);
    }

    @Override
    public void unregister(String toolId) {
        if (StrUtil.isBlank(toolId)) {
            return;
        }
        executorMap.remove(toolId.trim());
    }

    @Override
    public Optional<McpToolExecutor> getExecutor(String toolId) {
        if (StrUtil.isBlank(toolId)) {
            return Optional.empty();
        }
        String normalizedToolId = toolId.trim();
        McpToolExecutor executor = executorMap.get(normalizedToolId);
        if (executor != null) {
            return Optional.of(executor);
        }
        refreshRemoteTools();
        return Optional.ofNullable(executorMap.get(normalizedToolId));
    }

    @Override
    public List<McpToolExecutor> listExecutors() {
        refreshRemoteTools();
        return new ArrayList<>(executorMap.values());
    }

    private void refreshRemoteTools() {
        if (!properties.isEnabled()) {
            return;
        }
        List<McpToolDescriptor> tools = client.listTools();
        for (McpToolDescriptor tool : tools) {
            if (tool == null || StrUtil.isBlank(tool.toolId())) {
                continue;
            }
            executorMap.computeIfAbsent(tool.toolId(), ignored -> new RemoteMcpToolExecutor(client, tool));
        }
    }
}
