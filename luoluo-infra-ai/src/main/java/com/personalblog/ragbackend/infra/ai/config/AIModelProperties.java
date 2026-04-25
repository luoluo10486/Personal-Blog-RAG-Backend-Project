package com.personalblog.ragbackend.infra.ai.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "app.ai")
public class AIModelProperties {

    private boolean enabled = true;

    @Min(1)
    private int connectTimeoutSeconds = 30;

    @Min(1)
    private int readTimeoutSeconds = 60;

    @Valid
    private final Map<String, ProviderConfig> providers = new LinkedHashMap<>();

    @Valid
    private final ModelGroup chat = new ModelGroup();

    @Valid
    private final ModelGroup embedding = new ModelGroup();

    @Valid
    private final ModelGroup rerank = new ModelGroup();

    @Valid
    private final Selection selection = new Selection();

    @Valid
    private final Stream stream = new Stream();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    public Map<String, ProviderConfig> getProviders() {
        return providers;
    }

    public ModelGroup getChat() {
        return chat;
    }

    public ModelGroup getEmbedding() {
        return embedding;
    }

    public ModelGroup getRerank() {
        return rerank;
    }

    public Selection getSelection() {
        return selection;
    }

    public Stream getStream() {
        return stream;
    }

    public static class ModelGroup {
        private String defaultModel;
        private String deepThinkingModel;

        @Valid
        private List<ModelCandidate> candidates = new ArrayList<>();

        public String getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
        }

        public String getDeepThinkingModel() {
            return deepThinkingModel;
        }

        public void setDeepThinkingModel(String deepThinkingModel) {
            this.deepThinkingModel = deepThinkingModel;
        }

        public List<ModelCandidate> getCandidates() {
            return candidates;
        }

        public void setCandidates(List<ModelCandidate> candidates) {
            this.candidates = candidates;
        }
    }

    public static class ModelCandidate {
        private String id;

        @NotBlank
        private String provider;

        @NotBlank
        private String model;

        private String url;

        @Min(1)
        private Integer dimension;

        @Min(1)
        private Integer priority = 100;

        private Boolean enabled = true;

        private Boolean supportsThinking = false;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Integer getDimension() {
            return dimension;
        }

        public void setDimension(Integer dimension) {
            this.dimension = dimension;
        }

        public Integer getPriority() {
            return priority;
        }

        public void setPriority(Integer priority) {
            this.priority = priority;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Boolean getSupportsThinking() {
            return supportsThinking;
        }

        public void setSupportsThinking(Boolean supportsThinking) {
            this.supportsThinking = supportsThinking;
        }
    }

    public static class ProviderConfig {
        @NotBlank
        private String type;

        @NotBlank
        private String baseUrl;

        private String apiKey = "";

        @Valid
        private final EndpointConfig endpoints = new EndpointConfig();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public EndpointConfig getEndpoints() {
            return endpoints;
        }
    }

    public static class EndpointConfig {
        @NotBlank
        private String chat = "/v1/chat/completions";

        @NotBlank
        private String embedding = "/v1/embeddings";

        @NotBlank
        private String rerank = "/v1/rerank";

        public String getChat() {
            return chat;
        }

        public void setChat(String chat) {
            this.chat = chat;
        }

        public String getEmbedding() {
            return embedding;
        }

        public void setEmbedding(String embedding) {
            this.embedding = embedding;
        }

        public String getRerank() {
            return rerank;
        }

        public void setRerank(String rerank) {
            this.rerank = rerank;
        }
    }

    public static class Selection {
        @Min(1)
        private Integer failureThreshold = 2;

        @Min(1000)
        private Long openDurationMs = 30000L;

        public Integer getFailureThreshold() {
            return failureThreshold;
        }

        public void setFailureThreshold(Integer failureThreshold) {
            this.failureThreshold = failureThreshold;
        }

        public Long getOpenDurationMs() {
            return openDurationMs;
        }

        public void setOpenDurationMs(Long openDurationMs) {
            this.openDurationMs = openDurationMs;
        }
    }

    public static class Stream {
        @Min(1)
        private Integer messageChunkSize = 5;

        @Min(1)
        private Integer executorThreads = 4;

        public Integer getMessageChunkSize() {
            return messageChunkSize;
        }

        public void setMessageChunkSize(Integer messageChunkSize) {
            this.messageChunkSize = messageChunkSize;
        }

        public Integer getExecutorThreads() {
            return executorThreads;
        }

        public void setExecutorThreads(Integer executorThreads) {
            this.executorThreads = executorThreads;
        }
    }
}
