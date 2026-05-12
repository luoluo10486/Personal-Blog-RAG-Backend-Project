package com.personalblog.ragbackend.rag.controller.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record SystemSettingsVO(UploadSettings upload, RagSettings rag, AISettings ai) {
    public record UploadSettings(Long maxFileSize, Long maxRequestSize) {
    }

    public record RagSettings(@JsonProperty("default") DefaultSettings defaultConfig,
                              QueryRewriteSettings queryRewrite,
                              RateLimitSettings rateLimit,
                              MemorySettings memory,
                              TraceSettings trace,
                              McpSettings mcp) {
    }

    public record DefaultSettings(String collectionName, Integer dimension, String metricType, Long sseTimeoutMs) {
    }

    public record QueryRewriteSettings(Boolean enabled) {
    }

    public record RateLimitSettings(GlobalRateLimit global) {
    }

    public record GlobalRateLimit(Boolean enabled,
                                  Integer maxConcurrent,
                                  Integer maxWaitSeconds,
                                  Integer leaseSeconds,
                                  Integer pollIntervalMs) {
    }

    public record MemorySettings(Integer historyKeepTurns,
                                 Boolean summaryEnabled,
                                 Integer summaryStartTurns,
                                 Integer summaryMaxChars,
                                 Integer titleMaxLength) {
    }

    public record TraceSettings(Boolean enabled, Integer maxErrorLength) {
    }

    public record McpSettings(List<ServerConfig> servers) {
    }

    public record ServerConfig(String name, String url) {
    }

    public record AISettings(Map<String, ProviderConfig> providers,
                             ModelGroup chat,
                             ModelGroup embedding,
                             ModelGroup rerank,
                             Selection selection,
                             Stream stream) {
        public record ProviderConfig(String url, String apiKey, Map<String, String> endpoints) {
        }

        public record ModelGroup(String defaultModel, String deepThinkingModel, List<ModelCandidate> candidates) {
        }

        public record ModelCandidate(String id,
                                     String provider,
                                     String model,
                                     String url,
                                     Integer dimension,
                                     Integer priority,
                                     Boolean enabled,
                                     Boolean supportsThinking) {
        }

        public record Selection(Integer failureThreshold, Long openDurationMs) {
        }

        public record Stream(Integer messageChunkSize) {
        }
    }
}
