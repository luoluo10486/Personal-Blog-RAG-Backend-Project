package com.personalblog.ragbackend.mcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.dto.document.DocumentChunkResponse;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchRequest;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchResponse;
import com.personalblog.ragbackend.dto.rag.RagGenerationRequest;
import com.personalblog.ragbackend.dto.rag.RagGenerationResponse;
import com.personalblog.ragbackend.rag.config.RagProperties;
import com.personalblog.ragbackend.service.DocumentChunkService;
import com.personalblog.ragbackend.service.RagGenerationDemoService;
import com.personalblog.ragbackend.service.SiliconFlowEmbeddingDemoService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RagMcpTools {
    private static final int DEFAULT_TOP_K = 5;

    private final ObjectMapper objectMapper;
    private final RagProperties ragProperties;
    private final SiliconFlowEmbeddingDemoService siliconFlowEmbeddingDemoService;
    private final RagGenerationDemoService ragGenerationDemoService;
    private final DocumentChunkService documentChunkService;

    public RagMcpTools(
            ObjectMapper objectMapper,
            RagProperties ragProperties,
            SiliconFlowEmbeddingDemoService siliconFlowEmbeddingDemoService,
            RagGenerationDemoService ragGenerationDemoService,
            DocumentChunkService documentChunkService
    ) {
        this.objectMapper = objectMapper;
        this.ragProperties = ragProperties;
        this.siliconFlowEmbeddingDemoService = siliconFlowEmbeddingDemoService;
        this.ragGenerationDemoService = ragGenerationDemoService;
        this.documentChunkService = documentChunkService;
    }

    @Tool(description = "查看当前 Luoluo RAG MCP 服务状态、模型配置和检索配置。当用户想确认服务是否可用、当前使用的模型、Milvus 是否开启时使用。")
    public String getRagStatus() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", ragProperties.isEnabled());
        payload.put("chatModel", ragProperties.getModel());
        payload.put("embeddingModel", ragProperties.getEmbeddingModel());
        payload.put("embeddingProvider", ragProperties.getEmbeddingProvider());
        payload.put("apiUrl", ragProperties.getApiUrl());
        payload.put("embeddingApiUrl", ragProperties.getEmbeddingApiUrl());
        payload.put("milvusEnabled", ragProperties.getMilvus().isEnabled());
        payload.put("milvusUri", ragProperties.getMilvus().getUri());
        payload.put("collectionName", ragProperties.getMilvus().getCollectionName());
        payload.put("retrievalMode", ragProperties.getRetrieval().getMode().name());
        payload.put("rerankEnabled", ragProperties.getRerank().isEnabled());
        payload.put("rerankProvider", ragProperties.getRerank().getProvider());
        payload.put("rerankModel", ragProperties.getRerank().getModel());
        return toJson(payload);
    }

    @Tool(description = "在当前知识库中执行向量检索，返回召回片段、分数和元数据。当用户想先看命中的知识片段，再决定是否生成最终答案时使用。")
    public String searchKnowledgeBase(
            @ToolParam(description = "检索问题，例如：会员积分可以提现吗") String query,
            @ToolParam(description = "返回结果数量，范围 1 到 20，留空时默认 5") Integer topK
    ) {
        RagEmbeddingSearchResponse response = siliconFlowEmbeddingDemoService.search(
                new RagEmbeddingSearchRequest(query, normalizeTopK(topK))
        );
        return toJson(response);
    }

    @Tool(description = "基于当前知识库执行完整 RAG 生成，返回最终答案、引用和本次检索信息。当用户想直接得到可回答用户的问题答案时使用。")
    public String generateKnowledgeAnswer(
            @ToolParam(description = "用户问题，例如：订单发货后多久能看到物流信息") String query,
            @ToolParam(description = "检索并参与生成的候选片段数，范围 1 到 20，留空时默认 5") Integer topK,
            @ToolParam(description = "可选的系统提示词，留空时使用默认电商客服提示词") String systemPrompt
    ) {
        RagGenerationResponse response = ragGenerationDemoService.generate(
                new RagGenerationRequest(query, normalizeTopK(topK), normalizeOptionalText(systemPrompt))
        );
        return toJson(response);
    }

    @Tool(description = "按当前 RAG 切块规则对纯文本进行分块，适合在入库前预览 chunk 效果。当用户想把长文本拆成适合 embedding 的片段时使用。")
    public String chunkPlainText(
            @ToolParam(description = "需要切块的原始文本内容") String content
    ) {
        DocumentChunkResponse response = documentChunkService.chunkText(content);
        return toJson(response);
    }

    private int normalizeTopK(Integer topK) {
        if (topK == null) {
            return DEFAULT_TOP_K;
        }
        return Math.max(1, Math.min(20, topK));
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize MCP tool response", exception);
        }
    }
}
