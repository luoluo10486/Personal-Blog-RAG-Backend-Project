package com.personalblog.ragbackend.mcp.config;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class McpServerConfig {

    @Bean
    public HttpServletStreamableServerTransportProvider transportProvider() {
        return HttpServletStreamableServerTransportProvider.builder()
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
            HttpServletStreamableServerTransportProvider transportProvider) {
        return new ServletRegistrationBean<>(transportProvider, "/mcp");
    }

    @Bean
    public McpSyncServer mcpServer(HttpServletStreamableServerTransportProvider transportProvider,
                                   List<McpServerFeatures.SyncToolSpecification> toolSpecs) {
        return McpServer.sync(transportProvider)
                .serverInfo("ragent-mcp-server", "0.0.1")
                .tools(toolSpecs)
                .build();
    }
}
