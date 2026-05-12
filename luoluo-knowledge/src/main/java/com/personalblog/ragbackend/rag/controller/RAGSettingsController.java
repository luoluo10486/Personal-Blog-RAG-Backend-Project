package com.personalblog.ragbackend.rag.controller;

import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.infra.config.AIModelProperties;
import com.personalblog.ragbackend.rag.config.RagMcpProperties;
import com.personalblog.ragbackend.rag.config.RAGConfigProperties;
import com.personalblog.ragbackend.rag.config.RAGDefaultProperties;
import com.personalblog.ragbackend.rag.config.RagMemoryProperties;
import com.personalblog.ragbackend.rag.config.RagRateLimitProperties;
import com.personalblog.ragbackend.rag.controller.vo.SystemSettingsVO;
import com.personalblog.ragbackend.rag.controller.vo.SystemSettingsVO.AISettings;
import com.personalblog.ragbackend.rag.controller.vo.SystemSettingsVO.DefaultSettings;
import com.personalblog.ragbackend.rag.controller.vo.SystemSettingsVO.GlobalRateLimit;
import com.personalblog.ragbackend.rag.controller.vo.SystemSettingsVO.MemorySettings;
import com.personalblog.ragbackend.rag.controller.vo.SystemSettingsVO.McpSettings;
import com.personalblog.ragbackend.rag.controller.vo.SystemSettingsVO.QueryRewriteSettings;
import com.personalblog.ragbackend.rag.controller.vo.SystemSettingsVO.RagSettings;
import com.personalblog.ragbackend.rag.controller.vo.SystemSettingsVO.RateLimitSettings;
import com.personalblog.ragbackend.rag.controller.vo.SystemSettingsVO.TraceSettings;
import com.personalblog.ragbackend.rag.controller.vo.SystemSettingsVO.UploadSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@MemberLoginRequired
public class RAGSettingsController {
    private final RAGDefaultProperties ragDefaultProperties;
    private final RAGConfigProperties ragConfigProperties;
    private final RagRateLimitProperties ragRateLimitProperties;
    private final RagMemoryProperties memoryProperties;
    private final RagMcpProperties ragMcpProperties;
    private final AIModelProperties aiModelProperties;

    @Value("${spring.servlet.multipart.max-file-size:50MB}")
    private DataSize maxFileSize;

    @Value("${spring.servlet.multipart.max-request-size:100MB}")
    private DataSize maxRequestSize;

    public RAGSettingsController(RAGDefaultProperties ragDefaultProperties,
                                 RAGConfigProperties ragConfigProperties,
                                 RagRateLimitProperties ragRateLimitProperties,
                                 RagMemoryProperties memoryProperties,
                                 RagMcpProperties ragMcpProperties,
                                 AIModelProperties aiModelProperties) {
        this.ragDefaultProperties = ragDefaultProperties;
        this.ragConfigProperties = ragConfigProperties;
        this.ragRateLimitProperties = ragRateLimitProperties;
        this.memoryProperties = memoryProperties;
        this.ragMcpProperties = ragMcpProperties;
        this.aiModelProperties = aiModelProperties;
    }

    @GetMapping("/rag/settings")
    public R<SystemSettingsVO> settings() {
        SystemSettingsVO response = new SystemSettingsVO(
                new UploadSettings(maxFileSize.toBytes(), maxRequestSize.toBytes()),
                new RagSettings(
                        new DefaultSettings(
                                ragDefaultProperties.getCollectionName(),
                                ragDefaultProperties.getDimension(),
                                ragDefaultProperties.getMetricType(),
                                ragDefaultProperties.getSseTimeoutMs()
                        ),
                        new QueryRewriteSettings(ragConfigProperties.getQueryRewriteEnabled()),
                        new RateLimitSettings(new GlobalRateLimit(
                                ragRateLimitProperties.getGlobalEnabled(),
                                ragRateLimitProperties.getGlobalMaxConcurrent(),
                                ragRateLimitProperties.getGlobalMaxWaitSeconds(),
                                ragRateLimitProperties.getGlobalLeaseSeconds(),
                                ragRateLimitProperties.getGlobalPollIntervalMs()
                        )),
                        new MemorySettings(
                                memoryProperties.getHistoryKeepTurns(),
                                memoryProperties.isSummaryEnabled(),
                                memoryProperties.getSummaryStartTurns(),
                                memoryProperties.getSummaryMaxChars(),
                                memoryProperties.getTitleMaxLength()
                        ),
                        new TraceSettings(true, 1000),
                        new McpSettings(ragMcpProperties.getServers() == null
                                ? List.of()
                                : ragMcpProperties.getServers().stream()
                                .map(server -> new SystemSettingsVO.ServerConfig(server.getName(), server.getUrl()))
                                .toList())
                ),
                toAISettings()
        );
        return R.ok(response);
    }

    private AISettings toAISettings() {
        Map<String, AISettings.ProviderConfig> providers = new java.util.LinkedHashMap<>();
        if (aiModelProperties.getProviders() != null) {
            aiModelProperties.getProviders().forEach((name, config) ->
                    providers.put(name, new AISettings.ProviderConfig(
                            config.getUrl(),
                            config.getApiKey(),
                            config.getEndpoints() == null ? Map.of() : Map.copyOf(config.getEndpoints())
                    )));
        }

        return new AISettings(
                providers,
                toModelGroup(aiModelProperties.getChat()),
                toModelGroup(aiModelProperties.getEmbedding()),
                toModelGroup(aiModelProperties.getRerank()),
                new AISettings.Selection(
                        aiModelProperties.getSelection().getFailureThreshold(),
                        aiModelProperties.getSelection().getOpenDurationMs()
                ),
                new AISettings.Stream(aiModelProperties.getStream().getMessageChunkSize())
        );
    }

    private AISettings.ModelGroup toModelGroup(AIModelProperties.ModelGroup group) {
        if (group == null) {
            return null;
        }
        return new AISettings.ModelGroup(
                group.getDefaultModel(),
                group.getDeepThinkingModel(),
                group.getCandidates() == null ? List.of() : group.getCandidates().stream()
                        .map(candidate -> new AISettings.ModelCandidate(
                                candidate.getId(),
                                candidate.getProvider(),
                                candidate.getModel(),
                                candidate.getUrl(),
                                candidate.getDimension(),
                                candidate.getPriority(),
                                candidate.getEnabled(),
                                candidate.getSupportsThinking()))
                        .collect(Collectors.toList())
        );
    }
}
