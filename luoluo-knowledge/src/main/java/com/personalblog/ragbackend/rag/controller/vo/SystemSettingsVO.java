package com.personalblog.ragbackend.rag.controller.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemSettingsVO {
    private UploadSettings upload;
    private RagSettings rag;
    private AISettings ai;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UploadSettings {
        private Long maxFileSize;
        private Long maxRequestSize;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RagSettings {
        @JsonProperty("default")
        private DefaultSettings defaultConfig;
        private QueryRewriteSettings queryRewrite;
        private RateLimitSettings rateLimit;
        private MemorySettings memory;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DefaultSettings {
        private String collectionName;
        private Integer dimension;
        private String metricType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QueryRewriteSettings {
        private Boolean enabled;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RateLimitSettings {
        private GlobalRateLimit global;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GlobalRateLimit {
        private Boolean enabled;
        private Integer maxConcurrent;
        private Integer maxWaitSeconds;
        private Integer leaseSeconds;
        private Integer pollIntervalMs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MemorySettings {
        private Integer historyKeepTurns;
        private Boolean summaryEnabled;
        private Integer summaryStartTurns;
        private Integer summaryMaxChars;
        private Integer titleMaxLength;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AISettings {
        private Map<String, ProviderConfig> providers;
        private ModelGroup chat;
        private ModelGroup embedding;
        private ModelGroup rerank;
        private Selection selection;
        private Stream stream;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class ProviderConfig {
            private String url;
            private String apiKey;
            private Map<String, String> endpoints;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class ModelGroup {
            private String defaultModel;
            private String deepThinkingModel;
            private List<ModelCandidate> candidates;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class ModelCandidate {
            private String id;
            private String provider;
            private String model;
            private String url;
            private Integer dimension;
            private Integer priority;
            private Boolean enabled;
            private Boolean supportsThinking;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class Selection {
            private Integer failureThreshold;
            private Long openDurationMs;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class Stream {
            private Integer messageChunkSize;
        }
    }
}
