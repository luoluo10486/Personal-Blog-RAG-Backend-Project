package com.personalblog.ragbackend.rag.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * RAG 模块配置属性。
 */
@Validated
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {
    private boolean enabled = false;
    @NotBlank
    private String embeddingProvider = "demo";
    @NotBlank
    private String apiUrl = "https://api.siliconflow.cn/v1/chat/completions";
    @NotBlank
    private String embeddingApiUrl = "https://api.siliconflow.cn/v1/embeddings";
    private String apiKey = "";
    @NotBlank
    private String model = "Qwen/Qwen3-32B";
    @NotBlank
    private String embeddingModel = "Qwen/Qwen3-Embedding-8B";
    @DecimalMin("0.0")
    @DecimalMax("2.0")
    private double temperature = 0;
    @Min(1)
    private int maxTokens = 1024;
    @Min(1)
    private int connectTimeoutSeconds = 30;
    @Min(1)
    private int readTimeoutSeconds = 60;
    @Min(8)
    private int demoEmbeddingDimension = 64;
    @NotBlank
    private String systemPrompt = "You are a professional ecommerce support assistant. Keep answers concise.";
    @Valid
    private final MilvusProperties milvus = new MilvusProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEmbeddingProvider() {
        return embeddingProvider;
    }

    public void setEmbeddingProvider(String embeddingProvider) {
        this.embeddingProvider = embeddingProvider;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getEmbeddingApiUrl() {
        return embeddingApiUrl;
    }

    public void setEmbeddingApiUrl(String embeddingApiUrl) {
        this.embeddingApiUrl = embeddingApiUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    public int getDemoEmbeddingDimension() {
        return demoEmbeddingDimension;
    }

    public void setDemoEmbeddingDimension(int demoEmbeddingDimension) {
        this.demoEmbeddingDimension = demoEmbeddingDimension;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public MilvusProperties getMilvus() {
        return milvus;
    }

    public static class MilvusProperties {
        private boolean enabled = false;
        @NotBlank
        private String uri = "http://127.0.0.1:19530";
        private String token = "";
        private String databaseName = "";
        @NotBlank
        private String collectionName = "rag_demo_chunks";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getDatabaseName() {
            return databaseName;
        }

        public void setDatabaseName(String databaseName) {
            this.databaseName = databaseName;
        }

        public String getCollectionName() {
            return collectionName;
        }

        public void setCollectionName(String collectionName) {
            this.collectionName = collectionName;
        }
    }
}
