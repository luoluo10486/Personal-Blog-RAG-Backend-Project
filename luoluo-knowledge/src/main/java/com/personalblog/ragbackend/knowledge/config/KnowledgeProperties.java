package com.personalblog.ragbackend.knowledge.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.knowledge")
public class KnowledgeProperties {
    private boolean enabled = true;
    @NotBlank
    private String defaultBaseCode = "default";
    @Valid
    private VectorProperties vector = new VectorProperties();
    @Valid
    private DefaultStoreProperties defaults = new DefaultStoreProperties();
    @Valid
    private ChunkingProperties chunking = new ChunkingProperties();
    @Valid
    private ScheduleProperties schedule = new ScheduleProperties();
    @Valid
    private SearchProperties search = new SearchProperties();
    @Valid
    private TraceProperties trace = new TraceProperties();
    @Valid
    private List<ProviderProperties> providers = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultBaseCode() {
        return defaultBaseCode;
    }

    public void setDefaultBaseCode(String defaultBaseCode) {
        this.defaultBaseCode = defaultBaseCode;
    }

    public VectorProperties getVector() {
        return vector;
    }

    public void setVector(VectorProperties vector) {
        this.vector = vector;
    }

    public DefaultStoreProperties getDefaults() {
        return defaults;
    }

    public void setDefaults(DefaultStoreProperties defaults) {
        this.defaults = defaults;
    }

    public ChunkingProperties getChunking() {
        return chunking;
    }

    public void setChunking(ChunkingProperties chunking) {
        this.chunking = chunking;
    }

    public ScheduleProperties getSchedule() {
        return schedule;
    }

    public void setSchedule(ScheduleProperties schedule) {
        this.schedule = schedule;
    }

    public SearchProperties getSearch() {
        return search;
    }

    public void setSearch(SearchProperties search) {
        this.search = search;
    }

    public TraceProperties getTrace() {
        return trace;
    }

    public void setTrace(TraceProperties trace) {
        this.trace = trace;
    }

    public List<ProviderProperties> getProviders() {
        return providers;
    }

    public void setProviders(List<ProviderProperties> providers) {
        this.providers = providers;
    }

    public static class VectorProperties {
        @NotBlank
        private String type = "milvus";
        @Valid
        private MilvusProperties milvus = new MilvusProperties();
        @Valid
        private PgVectorProperties pg = new PgVectorProperties();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public MilvusProperties getMilvus() {
            return milvus;
        }

        public void setMilvus(MilvusProperties milvus) {
            this.milvus = milvus;
        }

        public PgVectorProperties getPg() {
            return pg;
        }

        public void setPg(PgVectorProperties pg) {
            this.pg = pg;
        }
    }

    public static class MilvusProperties {
        private boolean enabled = true;
        @NotBlank
        private String uri = "http://127.0.0.1:19530";
        private String token = "";
        private String databaseName = "";
        @NotBlank
        private String collectionPrefix = "kb_";
        @NotBlank
        private String consistencyLevel = "BOUNDED";
        @Min(1)
        private int nprobe = 16;

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

        public String getCollectionPrefix() {
            return collectionPrefix;
        }

        public void setCollectionPrefix(String collectionPrefix) {
            this.collectionPrefix = collectionPrefix;
        }

        public String getConsistencyLevel() {
            return consistencyLevel;
        }

        public void setConsistencyLevel(String consistencyLevel) {
            this.consistencyLevel = consistencyLevel;
        }

        public int getNprobe() {
            return nprobe;
        }

        public void setNprobe(int nprobe) {
            this.nprobe = nprobe;
        }
    }

    public static class PgVectorProperties {
        private boolean enabled = false;
        @NotBlank
        private String schema = "public";
        @NotBlank
        private String tableName = "knowledge_chunk_vector";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }
    }

    public static class DefaultStoreProperties {
        @NotBlank
        private String collectionName = "knowledge_default_store";
        @Min(1)
        private int dimension = 1536;
        @NotBlank
        private String metricType = "COSINE";
        @NotBlank
        private String embeddingModel = "Qwen/Qwen3-Embedding-8B";
        @NotBlank
        private String chatModel = "Qwen/Qwen3-32B";
        @NotBlank
        private String rerankModel = "BAAI/bge-reranker-v2-m3";

        public String getCollectionName() {
            return collectionName;
        }

        public void setCollectionName(String collectionName) {
            this.collectionName = collectionName;
        }

        public int getDimension() {
            return dimension;
        }

        public void setDimension(int dimension) {
            this.dimension = dimension;
        }

        public String getMetricType() {
            return metricType;
        }

        public void setMetricType(String metricType) {
            this.metricType = metricType;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        public String getChatModel() {
            return chatModel;
        }

        public void setChatModel(String chatModel) {
            this.chatModel = chatModel;
        }

        public String getRerankModel() {
            return rerankModel;
        }

        public void setRerankModel(String rerankModel) {
            this.rerankModel = rerankModel;
        }
    }

    public static class ChunkingProperties {
        @NotBlank
        private String strategy = "structure-aware";
        @Min(1)
        private int chunkSize = 800;
        @Min(0)
        private int chunkOverlap = 120;
        @Min(1)
        private int maxChunkCount = 1000;

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public int getChunkOverlap() {
            return chunkOverlap;
        }

        public void setChunkOverlap(int chunkOverlap) {
            this.chunkOverlap = chunkOverlap;
        }

        public int getMaxChunkCount() {
            return maxChunkCount;
        }

        public void setMaxChunkCount(int maxChunkCount) {
            this.maxChunkCount = maxChunkCount;
        }
    }

    public static class ScheduleProperties {
        @Min(0)
        private long scanDelayMs = 10000;
        @Min(1)
        private long lockSeconds = 900;
        @Min(1)
        private int batchSize = 20;
        @Min(1)
        private long minIntervalSeconds = 60;

        public long getScanDelayMs() {
            return scanDelayMs;
        }

        public void setScanDelayMs(long scanDelayMs) {
            this.scanDelayMs = scanDelayMs;
        }

        public long getLockSeconds() {
            return lockSeconds;
        }

        public void setLockSeconds(long lockSeconds) {
            this.lockSeconds = lockSeconds;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public long getMinIntervalSeconds() {
            return minIntervalSeconds;
        }

        public void setMinIntervalSeconds(long minIntervalSeconds) {
            this.minIntervalSeconds = minIntervalSeconds;
        }
    }

    public static class SearchProperties {
        @Min(1)
        private int topK = 8;
        @Min(1)
        private int topKMultiplier = 3;
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double confidenceThreshold = 0.6;
        @Min(1)
        private int rrfK = 60;
        @Valid
        private RerankProperties rerank = new RerankProperties();

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public int getTopKMultiplier() {
            return topKMultiplier;
        }

        public void setTopKMultiplier(int topKMultiplier) {
            this.topKMultiplier = topKMultiplier;
        }

        public double getConfidenceThreshold() {
            return confidenceThreshold;
        }

        public void setConfidenceThreshold(double confidenceThreshold) {
            this.confidenceThreshold = confidenceThreshold;
        }

        public int getRrfK() {
            return rrfK;
        }

        public void setRrfK(int rrfK) {
            this.rrfK = rrfK;
        }

        public RerankProperties getRerank() {
            return rerank;
        }

        public void setRerank(RerankProperties rerank) {
            this.rerank = rerank;
        }
    }

    public static class RerankProperties {
        private boolean enabled = true;
        @NotBlank
        private String provider = "demo";

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
    }

    public static class TraceProperties {
        private boolean enabled = true;
        @Min(1)
        private int maxErrorLength = 1000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxErrorLength() {
            return maxErrorLength;
        }

        public void setMaxErrorLength(int maxErrorLength) {
            this.maxErrorLength = maxErrorLength;
        }
    }

    public static class ProviderProperties {
        @NotBlank
        private String id;
        @NotBlank
        private String type;
        @NotBlank
        private String baseUrl;
        private String apiKey = "";
        @Valid
        private EndpointProperties endpoints = new EndpointProperties();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

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

        public EndpointProperties getEndpoints() {
            return endpoints;
        }

        public void setEndpoints(EndpointProperties endpoints) {
            this.endpoints = endpoints;
        }
    }

    public static class EndpointProperties {
        private String chat = "/v1/chat/completions";
        private String embedding = "/v1/embeddings";
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
}
