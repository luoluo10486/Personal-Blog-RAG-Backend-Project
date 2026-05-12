package com.personalblog.ragbackend.mcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.mcp.catalog.McpCapabilityCatalog;
import com.personalblog.ragbackend.knowledge.application.KnowledgeDocumentApplicationService;
import com.personalblog.ragbackend.knowledge.application.KnowledgeRagApplicationService;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeAskRequest;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunkResponse;
import com.personalblog.ragbackend.rag.core.retrieve.KnowledgeRetriever;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpace;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceResolver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagMcpTools {
    private static final int DEFAULT_TOP_K = 5;

    private final ObjectMapper objectMapper;
    private final KnowledgeProperties knowledgeProperties;
    private final KnowledgeRagApplicationService knowledgeRagApplicationService;
    private final KnowledgeRetriever knowledgeRetriever;
    private final KnowledgeVectorSpaceResolver knowledgeVectorSpaceResolver;
    private final KnowledgeDocumentApplicationService knowledgeDocumentApplicationService;
    private final McpCapabilityCatalog mcpCapabilityCatalog;

    public RagMcpTools(ObjectMapper objectMapper,
                       KnowledgeProperties knowledgeProperties,
                       KnowledgeRagApplicationService knowledgeRagApplicationService,
                       KnowledgeRetriever knowledgeRetriever,
                       KnowledgeVectorSpaceResolver knowledgeVectorSpaceResolver,
                       KnowledgeDocumentApplicationService knowledgeDocumentApplicationService,
                       McpCapabilityCatalog mcpCapabilityCatalog) {
        this.objectMapper = objectMapper;
        this.knowledgeProperties = knowledgeProperties;
        this.knowledgeRagApplicationService = knowledgeRagApplicationService;
        this.knowledgeRetriever = knowledgeRetriever;
        this.knowledgeVectorSpaceResolver = knowledgeVectorSpaceResolver;
        this.knowledgeDocumentApplicationService = knowledgeDocumentApplicationService;
        this.mcpCapabilityCatalog = mcpCapabilityCatalog;
    }

    public String getRagStatus() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("health", knowledgeRagApplicationService.health());
        payload.put("defaultTopK", knowledgeProperties.getSearch().getTopK());
        payload.put("topKMultiplier", knowledgeProperties.getSearch().getTopKMultiplier());
        payload.put("confidenceThreshold", knowledgeProperties.getSearch().getConfidenceThreshold());
        payload.put("rerankEnabled", knowledgeProperties.getSearch().getRerank().isEnabled());
        payload.put("vectorType", knowledgeProperties.getVector().getType());
        payload.put("pgEnabled", knowledgeProperties.getVector().getPg().isEnabled());
        payload.put("pgSchema", knowledgeProperties.getVector().getPg().getSchema());
        payload.put("vectorTable", knowledgeProperties.getVector().getPg().getTableName());
        payload.put("collectionPrefix", knowledgeProperties.getVector().getPg().getCollectionPrefix());
        return toJson(payload);
    }

    public String searchKnowledgeBase(
            String query,
            Integer topK,
            String baseCode
    ) {
        requireText(query, "检索问题");
        String normalizedBaseCode = normalizeBaseCode(baseCode);
        KnowledgeVectorSpace vectorSpace = knowledgeVectorSpaceResolver.resolve(normalizedBaseCode);
        List<KnowledgeChunk> chunks = knowledgeRetriever.retrieve(normalizedBaseCode, query, normalizeTopK(topK));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("baseCode", normalizedBaseCode);
        payload.put("vectorSpace", vectorSpace);
        payload.put("query", query);
        payload.put("resultCount", chunks.size());
        payload.put("results", chunks);
        return toJson(payload);
    }

    public String generateKnowledgeAnswer(
            String query,
            Integer topK,
            String baseCode
    ) {
        requireText(query, "用户问题");
        return toJson(knowledgeRagApplicationService.ask(
                new KnowledgeAskRequest(query, normalizeBaseCode(baseCode), normalizeTopK(topK))
        ));
    }

    public String chunkPlainText(
            String content
    ) {
        requireText(content, "需要切块的原始文本内容");
        DocumentChunkResponse response = knowledgeDocumentApplicationService.chunkText(content);
        return toJson(response);
    }

    public String previewKnowledgeCitations(
            String query,
            Integer topK,
            String baseCode
    ) {
        requireText(query, "检索问题");
        List<KnowledgeChunk> chunks = knowledgeRetriever.retrieve(normalizeBaseCode(baseCode), query, normalizeTopK(topK));
        List<Map<String, Object>> payload = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("documentId", chunk.documentId());
            item.put("title", chunk.title());
            item.put("chunkIndex", chunk.chunkIndex());
            item.put("score", chunk.score());
            item.put("content", chunk.content());
            payload.add(item);
        }
        return toJson(payload);
    }

    public String describeMcpCapabilities() {
        return toJson(mcpCapabilityCatalog.snapshot());
    }

    private String normalizeBaseCode(String baseCode) {
        if (baseCode == null || baseCode.isBlank()) {
            return knowledgeProperties.getDefaultBaseCode();
        }
        return knowledgeVectorSpaceResolver.normalizeBaseCode(baseCode);
    }

    private int normalizeTopK(Integer topK) {
        if (topK == null) {
            return DEFAULT_TOP_K;
        }
        return Math.max(1, Math.min(20, topK));
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize MCP tool response", exception);
        }
    }

    private void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
    }
}
