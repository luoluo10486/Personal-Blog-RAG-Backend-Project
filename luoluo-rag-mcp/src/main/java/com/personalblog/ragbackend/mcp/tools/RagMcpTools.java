package com.personalblog.ragbackend.mcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.knowledge.application.KnowledgeDocumentApplicationService;
import com.personalblog.ragbackend.knowledge.application.KnowledgeRagApplicationService;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeAskRequest;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunkResponse;
import com.personalblog.ragbackend.knowledge.service.retrieval.KnowledgeRetriever;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpace;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceResolver;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
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

    public RagMcpTools(ObjectMapper objectMapper,
                       KnowledgeProperties knowledgeProperties,
                       KnowledgeRagApplicationService knowledgeRagApplicationService,
                       KnowledgeRetriever knowledgeRetriever,
                       KnowledgeVectorSpaceResolver knowledgeVectorSpaceResolver,
                       KnowledgeDocumentApplicationService knowledgeDocumentApplicationService) {
        this.objectMapper = objectMapper;
        this.knowledgeProperties = knowledgeProperties;
        this.knowledgeRagApplicationService = knowledgeRagApplicationService;
        this.knowledgeRetriever = knowledgeRetriever;
        this.knowledgeVectorSpaceResolver = knowledgeVectorSpaceResolver;
        this.knowledgeDocumentApplicationService = knowledgeDocumentApplicationService;
    }

    @Tool(description = "查看当前正式知识库 RAG 服务状态、默认知识库和向量空间配置。")
    public String getRagStatus() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("health", knowledgeRagApplicationService.health());
        payload.put("defaultTopK", knowledgeProperties.getSearch().getTopK());
        payload.put("topKMultiplier", knowledgeProperties.getSearch().getTopKMultiplier());
        payload.put("confidenceThreshold", knowledgeProperties.getSearch().getConfidenceThreshold());
        payload.put("rerankEnabled", knowledgeProperties.getSearch().getRerank().isEnabled());
        payload.put("vectorType", knowledgeProperties.getVector().getType());
        payload.put("milvusEnabled", knowledgeProperties.getVector().getMilvus().isEnabled());
        payload.put("milvusUri", knowledgeProperties.getVector().getMilvus().getUri());
        payload.put("collectionPrefix", knowledgeProperties.getVector().getMilvus().getCollectionPrefix());
        return toJson(payload);
    }

    @Tool(description = "在正式知识库链路中执行检索，返回命中的知识片段和向量空间信息。")
    public String searchKnowledgeBase(
            @ToolParam(description = "检索问题，例如：会员积分可以提现吗") String query,
            @ToolParam(description = "返回结果数量，范围 1 到 20，留空时默认 5") Integer topK,
            @ToolParam(description = "可选知识库编码，留空时使用默认知识库") String baseCode
    ) {
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

    @Tool(description = "基于正式知识库链路生成最终回答，返回答案、引用和执行轨迹。")
    public String generateKnowledgeAnswer(
            @ToolParam(description = "用户问题，例如：订单发货后多久能看到物流信息") String query,
            @ToolParam(description = "检索并参与生成的候选片段数，范围 1 到 20，留空时默认 5") Integer topK,
            @ToolParam(description = "可选知识库编码，留空时使用默认知识库") String baseCode
    ) {
        return toJson(knowledgeRagApplicationService.ask(
                new KnowledgeAskRequest(query, normalizeBaseCode(baseCode), normalizeTopK(topK))
        ));
    }

    @Tool(description = "按正式知识库切块规则对纯文本进行切块，适合在入库前预览 chunk 效果。")
    public String chunkPlainText(
            @ToolParam(description = "需要切块的原始文本内容") String content
    ) {
        DocumentChunkResponse response = knowledgeDocumentApplicationService.chunkText(content);
        return toJson(response);
    }

    @Tool(description = "查看正式知识库链路中当前问题的候选片段预览，便于人工核对召回质量。")
    public String previewKnowledgeCitations(
            @ToolParam(description = "检索问题") String query,
            @ToolParam(description = "候选数量，范围 1 到 20，留空时默认 5") Integer topK,
            @ToolParam(description = "可选知识库编码，留空时使用默认知识库") String baseCode
    ) {
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
}
