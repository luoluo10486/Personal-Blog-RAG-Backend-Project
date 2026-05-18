package com.personalblog.ragbackend.mcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeAskResponse;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeCitation;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeHealthResponse;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeTrace;
import com.personalblog.ragbackend.knowledge.service.document.KnowledgeDocumentChunkService;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpace;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceResolver;
import com.personalblog.ragbackend.rag.config.RAGDefaultProperties;
import com.personalblog.ragbackend.rag.core.retrieve.RetrieveRequest;
import com.personalblog.ragbackend.rag.core.retrieve.RetrieverService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagMcpTools {
    private static final int DEFAULT_TOP_K = 5;

    private final ObjectMapper objectMapper;
    private final RAGDefaultProperties ragDefaultProperties;
    private final KnowledgeDocumentChunkService knowledgeDocumentChunkService;
    private final RetrieverService retrieverService;
    private final KnowledgeVectorSpaceResolver knowledgeVectorSpaceResolver;

    public RagMcpTools(ObjectMapper objectMapper,
                       RAGDefaultProperties ragDefaultProperties,
                       KnowledgeDocumentChunkService knowledgeDocumentChunkService,
                       RetrieverService retrieverService,
                       KnowledgeVectorSpaceResolver knowledgeVectorSpaceResolver) {
        this.objectMapper = objectMapper;
        this.ragDefaultProperties = ragDefaultProperties;
        this.knowledgeDocumentChunkService = knowledgeDocumentChunkService;
        this.retrieverService = retrieverService;
        this.knowledgeVectorSpaceResolver = knowledgeVectorSpaceResolver;
    }

    public String getRagStatus() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("health", new KnowledgeHealthResponse(
                true,
                ragDefaultProperties.getCollectionName(),
                "pg",
                ragDefaultProperties.getCollectionName(),
                null,
                null
        ));
        payload.put("defaultTopK", DEFAULT_TOP_K);
        payload.put("vectorType", "pg");
        payload.put("collectionName", ragDefaultProperties.getCollectionName());
        payload.put("dimension", ragDefaultProperties.getDimension());
        return toJson(payload);
    }

    public String searchKnowledgeBase(String query, Integer topK, String baseCode) {
        requireText(query, "搜索问题");
        String normalizedBaseCode = normalizeBaseCode(baseCode);
        KnowledgeVectorSpace vectorSpace = knowledgeVectorSpaceResolver.resolve(normalizedBaseCode);
        List<RetrievedChunk> chunks = retrieverService.retrieve(
                RetrieveRequest.builder()
                        .query(query)
                        .topK(normalizeTopK(topK))
                        .collectionName(vectorSpace.collectionName())
                        .build()
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("baseCode", normalizedBaseCode);
        payload.put("vectorSpace", vectorSpace);
        payload.put("query", query);
        payload.put("resultCount", chunks.size());
        payload.put("results", chunks);
        return toJson(payload);
    }

    public String generateKnowledgeAnswer(String query, Integer topK, String baseCode) {
        requireText(query, "用户问题");
        String normalizedBaseCode = normalizeBaseCode(baseCode);
        KnowledgeVectorSpace vectorSpace = knowledgeVectorSpaceResolver.resolve(normalizedBaseCode);
        List<RetrievedChunk> chunks = retrieverService.retrieve(
                RetrieveRequest.builder()
                        .query(query)
                        .topK(normalizeTopK(topK))
                        .collectionName(vectorSpace.collectionName())
                        .build()
        );
        List<KnowledgeCitation> citations = chunks.stream()
                .map(this::toCitation)
                .toList();
        KnowledgeAskResponse response = new KnowledgeAskResponse(
                buildAnswer(chunks),
                normalizedBaseCode,
                citations,
                new KnowledgeTrace(
                        null,
                        null,
                        "mcp-tools",
                        vectorSpace.vectorType(),
                        vectorSpace.collectionName(),
                        normalizeTopK(topK),
                        query,
                        query,
                        List.of("retrieved:" + chunks.size())
                ),
                null,
                null
        );
        return toJson(response);
    }

    public String chunkPlainText(String content) {
        requireText(content, "原始文本内容");
        return toJson(knowledgeDocumentChunkService.chunkText(content));
    }

    public String previewKnowledgeCitations(String query, Integer topK, String baseCode) {
        requireText(query, "搜索问题");
        String normalizedBaseCode = normalizeBaseCode(baseCode);
        KnowledgeVectorSpace vectorSpace = knowledgeVectorSpaceResolver.resolve(normalizedBaseCode);
        List<RetrievedChunk> chunks = retrieverService.retrieve(
                RetrieveRequest.builder()
                        .query(query)
                        .topK(normalizeTopK(topK))
                        .collectionName(vectorSpace.collectionName())
                        .build()
        );
        List<KnowledgeCitation> citations = chunks.stream()
                .map(this::toCitation)
                .toList();
        return toJson(citations);
    }

    public String describeMcpCapabilities() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("server", Map.of("name", "ragent-mcp-server", "version", "0.0.1"));
        payload.put("tools", List.of(
                Map.of("name", "getRagStatus", "description", "查看当前 RAG 服务状态、默认知识库和向量空间配置"),
                Map.of("name", "searchKnowledgeBase", "description", "在知识库中执行检索，返回命中的知识片段和向量空间信息"),
                Map.of("name", "generateKnowledgeAnswer", "description", "基于检索到的知识片段生成答案，并返回引用信息"),
                Map.of("name", "chunkPlainText", "description", "按知识库切块规则对纯文本进行切块，适合在入库前预览 chunk 效果"),
                Map.of("name", "previewKnowledgeCitations", "description", "查看当前问题命中的候选知识片段，便于人工核对召回质量"),
                Map.of("name", "describeMcpCapabilities", "description", "列出当前 MCP 服务提供的 tools 信息")
        ));
        return toJson(payload);
    }

    private String normalizeBaseCode(String baseCode) {
        if (baseCode == null || baseCode.isBlank()) {
            return ragDefaultProperties.getCollectionName();
        }
        return knowledgeVectorSpaceResolver.normalizeBaseCode(baseCode);
    }

    private int normalizeTopK(Integer topK) {
        if (topK == null) {
            return DEFAULT_TOP_K;
        }
        return Math.max(1, Math.min(20, topK));
    }

    private KnowledgeCitation toCitation(RetrievedChunk chunk) {
        if (chunk == null) {
            return new KnowledgeCitation(null, null, null, 0, 0.0D, null);
        }
        return new KnowledgeCitation(
                chunk.getId(),
                null,
                null,
                0,
                chunk.getScore() == null ? 0.0D : chunk.getScore(),
                chunk.getText()
        );
    }

    private String buildAnswer(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "未检索到相关知识片段";
        }
        RetrievedChunk first = chunks.get(0);
        String preview = first.getText();
        if (preview != null && preview.length() > 200) {
            preview = preview.substring(0, 200) + "...";
        }
        return "已检索到 " + chunks.size() + " 条相关知识片段，" +
                "可优先参考最相关内容：" + (preview == null ? "" : preview);
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
