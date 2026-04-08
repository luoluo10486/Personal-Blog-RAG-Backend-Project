package com.personalblog.ragbackend.rag.config;

import io.milvus.v2.common.ConsistencyLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * RAG 模块配置。
 *
 * 说明：
 * - `embeddingProvider` 控制 embedding 生成方式：`demo`（本地哈希向量）或 `siliconflow`（真实 API）。
 * - `retrieval` 控制粗检索（Dense / Sparse / Hybrid）与召回参数。
 * - `rerank` 控制二阶段重排序（可选走 SiliconFlow rerank；失败时回退本地启发式重排）。
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
    private String systemPrompt = "你是一名专业的电商客服助手，请保持回答简洁准确。";
    @Valid
    private final MilvusProperties milvus = new MilvusProperties();
    @Valid
    private final RetrievalProperties retrieval = new RetrievalProperties();
    @Valid
    private final RerankProperties rerank = new RerankProperties();

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

    public RetrievalProperties getRetrieval() {
        return retrieval;
    }

    public RerankProperties getRerank() {
        return rerank;
    }

    public enum SearchMode {
        DENSE_ONLY,
        SPARSE_ONLY,
        HYBRID
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

    public static class RetrievalProperties {
        private SearchMode mode = SearchMode.HYBRID;
        @Min(1)
        private int denseRecallTopK = 12;
        @Min(1)
        private int sparseRecallTopK = 12;
        @Min(1)
        private int nprobe = 16;
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double dropRatioSearch = 0.2;
        @Min(1)
        private int rrfK = 60;
        @NotBlank
        private String consistencyLevel = "BOUNDED";

        public SearchMode getMode() {
            return mode;
        }

        public void setMode(SearchMode mode) {
            this.mode = mode;
        }

        public int getDenseRecallTopK() {
            return denseRecallTopK;
        }

        public void setDenseRecallTopK(int denseRecallTopK) {
            this.denseRecallTopK = denseRecallTopK;
        }

        public int getSparseRecallTopK() {
            return sparseRecallTopK;
        }

        public void setSparseRecallTopK(int sparseRecallTopK) {
            this.sparseRecallTopK = sparseRecallTopK;
        }

        public int getNprobe() {
            return nprobe;
        }

        public void setNprobe(int nprobe) {
            this.nprobe = nprobe;
        }

        public double getDropRatioSearch() {
            return dropRatioSearch;
        }

        public void setDropRatioSearch(double dropRatioSearch) {
            this.dropRatioSearch = dropRatioSearch;
        }

        public int getRrfK() {
            return rrfK;
        }

        public void setRrfK(int rrfK) {
            this.rrfK = rrfK;
        }

        public String getConsistencyLevel() {
            return consistencyLevel;
        }

        public void setConsistencyLevel(String consistencyLevel) {
            this.consistencyLevel = consistencyLevel;
        }

        public ConsistencyLevel resolveConsistencyLevel() {
            return ConsistencyLevel.valueOf(consistencyLevel.trim().toUpperCase());
        }
    }

    public static class RerankProperties {
        private boolean enabled = true;
        @NotBlank
        private String provider = "demo";
        @NotBlank
        private String apiUrl = "https://api.siliconflow.cn/v1/rerank";
        @NotBlank
        private String model = "BAAI/bge-reranker-v2-m3";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}
