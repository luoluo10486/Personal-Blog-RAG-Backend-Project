package com.personalblog.ragbackend.rag.core.mcp;

import cn.hutool.core.collection.CollUtil;
import com.personalblog.ragbackend.rag.core.mcp.client.HttpMCPClient;
import com.personalblog.ragbackend.rag.core.mcp.client.MCPClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(McpClientProperties.class)
public class McpClientAutoConfiguration {

    private final McpClientProperties properties;
    @Qualifier("aiHttpClient")
    private final HttpClient httpClient;
    private final McpToolRegistry toolRegistry;

    private final List<MCPClient> clients = new ArrayList<>();

    @PostConstruct
    public void init() {
        List<McpClientProperties.ServerConfig> servers = properties.getServers();
        if (servers == null || servers.isEmpty()) {
            log.info("No MCP server configured, skip remote tool registration");
            return;
        }
        for (McpClientProperties.ServerConfig server : servers) {
            registerRemoteTools(server);
        }
    }

    private void registerRemoteTools(McpClientProperties.ServerConfig server) {
        String serverName = server.getName();
        String serverUrl = server.getUrl();
        log.info("Connecting MCP Server: name={}, url={}", serverName, serverUrl);
        try {
            MCPClient mcpClient = new HttpMCPClient(httpClient, serverUrl);
            if (!mcpClient.initialize()) {
                log.error("MCP Server [{}] initialization failed, skip tool registration", serverName);
                return;
            }
            clients.add(mcpClient);
            List<MCPTool> tools = mcpClient.listTools();
            if (CollUtil.isEmpty(tools)) {
                log.info("MCP Server [{}] returned no tools", serverName);
                return;
            }
            for (MCPTool tool : tools) {
                McpClientToolExecutor executor = new McpClientToolExecutor(mcpClient, tool);
                toolRegistry.register(executor);
                log.info("Registered remote MCP tool: toolId={}, server={}", tool.getToolId(), serverName);
            }
        } catch (Exception exception) {
            log.error("Connect MCP Server [{}] failed, reason={}", serverName, exception.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        for (MCPClient client : clients) {
            if (client instanceof AutoCloseable closable) {
                try {
                    closable.close();
                } catch (Exception exception) {
                    log.warn("Close MCP client failed", exception);
                }
            }
        }
    }
}
