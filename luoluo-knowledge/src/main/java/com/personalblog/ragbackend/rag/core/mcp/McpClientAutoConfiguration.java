package com.personalblog.ragbackend.rag.core.mcp;

import cn.hutool.core.collection.CollUtil;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(McpClientProperties.class)
public class McpClientAutoConfiguration {

    private final McpClientProperties properties;
    private final McpToolRegistry toolRegistry;

    private final List<McpSyncClient> clients = new ArrayList<>();

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
            String mcpUrl = serverUrl.endsWith("/mcp") ? serverUrl : serverUrl + "/mcp";
            HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(mcpUrl).build();

            McpSyncClient client = McpClient.sync(transport)
                    .clientInfo(new Implementation("ragent-bootstrap", "1.0.0"))
                    .build();
            client.initialize();
            clients.add(client);

            ListToolsResult result = client.listTools();
            List<Tool> tools = result.tools();
            if (CollUtil.isEmpty(tools)) {
                log.info("MCP Server [{}] returned no tools", serverName);
                return;
            }

            for (Tool tool : tools) {
                McpClientToolExecutor executor = new McpClientToolExecutor(client, tool, serverUrl);
                toolRegistry.register(executor);
                log.info("Registered remote MCP tool: toolId={}, server={}", tool.name(), serverName);
            }
        } catch (Exception exception) {
            log.error("Connect MCP Server [{}] failed, reason={}", serverName, exception.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        for (McpSyncClient client : clients) {
            try {
                client.close();
            } catch (Exception exception) {
                log.warn("Close MCP client failed", exception);
            }
        }
    }
}
