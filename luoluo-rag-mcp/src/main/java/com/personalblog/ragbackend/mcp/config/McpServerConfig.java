package com.personalblog.ragbackend.mcp.config;

import com.personalblog.ragbackend.mcp.tools.RagMcpTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider ragToolProvider(RagMcpTools ragMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(ragMcpTools)
                .build();
    }
}
