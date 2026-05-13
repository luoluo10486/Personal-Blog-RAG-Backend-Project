package com.personalblog.ragbackend.rag.core.mcp.client;

import com.personalblog.ragbackend.rag.core.mcp.MCPTool;
import com.personalblog.ragbackend.rag.core.mcp.MCPToolRegistry;
import com.personalblog.ragbackend.rag.core.mcp.RemoteMCPToolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.net.http.HttpClient;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(MCPClientProperties.class)
public class MCPClientAutoConfiguration {

    private final MCPClientProperties properties;
    @Qualifier("aiHttpClient")
    private final HttpClient httpClient;
    private final MCPToolRegistry toolRegistry;

    @PostConstruct
    public void init() {
        List<MCPClientProperties.ServerConfig> servers = properties.getServers();
        if (servers == null || servers.isEmpty()) {
            log.info("No MCP server configured, skip remote tool registration");
            return;
        }
        for (MCPClientProperties.ServerConfig server : servers) {
            registerRemoteTools(server);
        }
    }

    private void registerRemoteTools(MCPClientProperties.ServerConfig server) {
        String serverName = server.getName();
        String serverUrl = server.getUrl();
        log.info("Connecting MCP Server: name={}, url={}", serverName, serverUrl);
        try {
            HttpMCPClient mcpClient = new HttpMCPClient(httpClient, serverUrl);
            if (!mcpClient.initialize()) {
                log.error("MCP Server [{}] initialization failed, skip tool registration", serverName);
                return;
            }
            List<MCPTool> tools = mcpClient.listTools();
            if (tools.isEmpty()) {
                log.info("MCP Server [{}] returned no tools", serverName);
                return;
            }
            for (MCPTool tool : tools) {
                RemoteMCPToolExecutor executor = new RemoteMCPToolExecutor(mcpClient, tool);
                toolRegistry.register(executor);
                log.info("Registered remote MCP tool: toolId={}, server={}", tool.getToolId(), serverName);
            }
        } catch (Exception exception) {
            log.error("Connect MCP Server [{}] failed, reason={}", serverName, exception.getMessage());
        }
    }
}
