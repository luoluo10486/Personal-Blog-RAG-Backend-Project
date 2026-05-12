package com.personalblog.ragbackend.rag.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "rag.mcp")
public class RagMcpProperties {
    private boolean enabled = true;
    @Valid
    private List<ServerConfig> servers = new ArrayList<>();
    @Min(100)
    private int connectTimeoutMs = 3000;
    @Min(100)
    private int readTimeoutMs = 10000;
    private boolean parameterExtractionEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<ServerConfig> getServers() {
        return servers;
    }

    public void setServers(List<ServerConfig> servers) {
        this.servers = servers;
    }

    public String resolveServerUrl() {
        if (servers == null || servers.isEmpty() || servers.get(0) == null || servers.get(0).getUrl() == null) {
            return "http://127.0.0.1:9099";
        }
        return servers.get(0).getUrl();
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public boolean isParameterExtractionEnabled() {
        return parameterExtractionEnabled;
    }

    public void setParameterExtractionEnabled(boolean parameterExtractionEnabled) {
        this.parameterExtractionEnabled = parameterExtractionEnabled;
    }

    public static class ServerConfig {
        @NotBlank
        private String name = "default";

        @NotBlank
        private String url = "http://127.0.0.1:9099";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}
