package com.personalblog.ragbackend.knowledge.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.knowledge.mcp")
public class RagMcpProperties {
    private boolean enabled = true;
    @NotBlank
    private String serverUrl = "http://127.0.0.1:9099";
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

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
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
}
