package com.personalblog.ragbackend.rag.core.mcp.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "rag.mcp")
public class MCPClientProperties {
    private List<ServerConfig> servers = new ArrayList<>();

    @Data
    public static class ServerConfig {
        private String name;
        private String url;
    }
}
