package com.personalblog.ragbackend.rag.controller;

import com.personalblog.ragbackend.common.web.domain.Result;
import com.personalblog.ragbackend.common.web.domain.Results;
import com.personalblog.ragbackend.infra.config.AIModelProperties;
import com.personalblog.ragbackend.rag.config.RAGConfigProperties;
import com.personalblog.ragbackend.rag.config.RAGDefaultProperties;
import com.personalblog.ragbackend.rag.config.MemoryProperties;
import com.personalblog.ragbackend.rag.config.RagRateLimitProperties;
import com.personalblog.ragbackend.rag.controller.vo.SystemSettingsVO;
import com.personalblog.ragbackend.rag.controller.vo.SystemSettingsVO.AISettings;
import com.personalblog.ragbackend.rag.controller.vo.SystemSettingsVO.DefaultSettings;
import com.personalblog.ragbackend.rag.controller.vo.SystemSettingsVO.GlobalRateLimit;
import com.personalblog.ragbackend.rag.controller.vo.SystemSettingsVO.MemorySettings;
import com.personalblog.ragbackend.rag.controller.vo.SystemSettingsVO.QueryRewriteSettings;
import com.personalblog.ragbackend.rag.controller.vo.SystemSettingsVO.RagSettings;
import com.personalblog.ragbackend.rag.controller.vo.SystemSettingsVO.RateLimitSettings;
import com.personalblog.ragbackend.rag.controller.vo.SystemSettingsVO.UploadSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class RAGSettingsController {
    private final RAGDefaultProperties ragDefaultProperties;
    private final RAGConfigProperties ragConfigProperties;
    private final RagRateLimitProperties ragRateLimitProperties;
    private final MemoryProperties memoryProperties;
    private final AIModelProperties aiModelProperties;

    @Value("${spring.servlet.multipart.max-file-size:50MB}")
    private DataSize maxFileSize;

    @Value("${spring.servlet.multipart.max-request-size:100MB}")
    private DataSize maxRequestSize;

    @GetMapping("/rag/settings")
    public Result<SystemSettingsVO> settings() {
        SystemSettingsVO response = SystemSettingsVO.builder()
                .upload(UploadSettings.builder()
                        .maxFileSize(maxFileSize.toBytes())
                        .maxRequestSize(maxRequestSize.toBytes())
                        .build())
                .rag(RagSettings.builder()
                        .defaultConfig(toDefaultSettings())
                        .queryRewrite(QueryRewriteSettings.builder()
                                .enabled(ragConfigProperties.getQueryRewriteEnabled())
                                .build())
                        .rateLimit(RateLimitSettings.builder()
                                .global(GlobalRateLimit.builder()
                                        .enabled(ragRateLimitProperties.getGlobalEnabled())
                                        .maxConcurrent(ragRateLimitProperties.getGlobalMaxConcurrent())
                                        .maxWaitSeconds(ragRateLimitProperties.getGlobalMaxWaitSeconds())
                                        .leaseSeconds(ragRateLimitProperties.getGlobalLeaseSeconds())
                                        .pollIntervalMs(ragRateLimitProperties.getGlobalPollIntervalMs())
                                        .build())
                                .build())
                        .memory(toMemorySettings())
                        .build())
                .ai(toAISettings())
                .build();
        return Results.success(response);
    }

    private DefaultSettings toDefaultSettings() {
        return DefaultSettings.builder()
                .collectionName(ragDefaultProperties.getCollectionName())
                .dimension(ragDefaultProperties.getDimension())
                .metricType(ragDefaultProperties.getMetricType())
                .build();
    }

    private MemorySettings toMemorySettings() {
        return MemorySettings.builder()
                .historyKeepTurns(memoryProperties.getHistoryKeepTurns())
                .summaryEnabled(memoryProperties.getSummaryEnabled())
                .summaryStartTurns(memoryProperties.getSummaryStartTurns())
                .summaryMaxChars(memoryProperties.getSummaryMaxChars())
                .titleMaxLength(memoryProperties.getTitleMaxLength())
                .build();
    }

    private AISettings toAISettings() {
        Map<String, AISettings.ProviderConfig> providers = new HashMap<>();
        if (aiModelProperties.getProviders() != null) {
            aiModelProperties.getProviders().forEach((name, config) ->
                    providers.put(name, AISettings.ProviderConfig.builder()
                            .url(config.getUrl())
                            .apiKey(maskApiKey(config.getApiKey()))
                            .endpoints(config.getEndpoints())
                            .build()));
        }

        return AISettings.builder()
                .providers(providers)
                .chat(toModelGroup(aiModelProperties.getChat()))
                .embedding(toModelGroup(aiModelProperties.getEmbedding()))
                .rerank(toModelGroup(aiModelProperties.getRerank()))
                .selection(aiModelProperties.getSelection() == null
                        ? null
                        : AISettings.Selection.builder()
                        .failureThreshold(aiModelProperties.getSelection().getFailureThreshold())
                        .openDurationMs(aiModelProperties.getSelection().getOpenDurationMs())
                        .build())
                .stream(aiModelProperties.getStream() == null
                        ? null
                        : AISettings.Stream.builder()
                        .messageChunkSize(aiModelProperties.getStream().getMessageChunkSize())
                        .build())
                .build();
    }

    private AISettings.ModelGroup toModelGroup(AIModelProperties.ModelGroup group) {
        if (group == null) {
            return null;
        }
        return AISettings.ModelGroup.builder()
                .defaultModel(group.getDefaultModel())
                .deepThinkingModel(group.getDeepThinkingModel())
                .candidates(group.getCandidates() == null ? null : group.getCandidates().stream()
                        .map(candidate -> AISettings.ModelCandidate.builder()
                                .id(candidate.getId())
                                .provider(candidate.getProvider())
                                .model(candidate.getModel())
                                .url(candidate.getUrl())
                                .dimension(candidate.getDimension())
                                .priority(candidate.getPriority())
                                .enabled(candidate.getEnabled())
                                .supportsThinking(candidate.getSupportsThinking())
                                .build())
                        .toList())
                .build();
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 10) {
            return "******";
        }
        return trimmed.substring(0, 6) + "***" + trimmed.substring(trimmed.length() - 4);
    }
}
