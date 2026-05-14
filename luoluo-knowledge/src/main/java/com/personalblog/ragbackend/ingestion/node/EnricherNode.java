package com.personalblog.ragbackend.ingestion.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.core.chunk.VectorChunk;
import com.personalblog.ragbackend.ingestion.domain.context.IngestionContext;
import com.personalblog.ragbackend.ingestion.domain.enums.ChunkEnrichType;
import com.personalblog.ragbackend.ingestion.domain.enums.IngestionNodeType;
import com.personalblog.ragbackend.ingestion.domain.pipeline.NodeConfig;
import com.personalblog.ragbackend.ingestion.domain.result.NodeResult;
import com.personalblog.ragbackend.ingestion.domain.settings.EnricherSettings;
import com.personalblog.ragbackend.ingestion.prompt.EnricherPromptManager;
import com.personalblog.ragbackend.ingestion.util.JsonResponseParser;
import com.personalblog.ragbackend.ingestion.util.PromptTemplateRenderer;
import com.personalblog.ragbackend.infra.chat.LLMService;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.ChatRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class EnricherNode implements IngestionNode {

    private final ObjectMapper objectMapper;
    private final LLMService llmService;

    public EnricherNode(ObjectMapper objectMapper, LLMService llmService) {
        this.objectMapper = objectMapper;
        this.llmService = llmService;
    }

    @Override
    public String getNodeType() {
        return IngestionNodeType.ENRICHER.getValue();
    }

    @Override
    public NodeResult execute(IngestionContext context, NodeConfig config) {
        List<VectorChunk> chunks = context.getChunks();
        if (chunks == null || chunks.isEmpty()) {
            return NodeResult.ok("no chunks to enrich");
        }
        EnricherSettings settings = parseSettings(config.getSettings());
        if (settings.getTasks() == null || settings.getTasks().isEmpty()) {
            return NodeResult.ok("no enricher tasks");
        }
        boolean attachMetadata = settings.getAttachDocumentMetadata() == null || settings.getAttachDocumentMetadata();
        for (VectorChunk chunk : chunks) {
            if (chunk == null || !StringUtils.hasText(chunk.getContent())) {
                continue;
            }
            if (chunk.getMetadata() == null) {
                chunk.setMetadata(new HashMap<>());
            }
            if (attachMetadata && context.getMetadata() != null) {
                chunk.getMetadata().putAll(context.getMetadata());
            }
            for (EnricherSettings.ChunkEnrichTask task : settings.getTasks()) {
                if (task == null || task.getType() == null) {
                    continue;
                }
                ChunkEnrichType type = task.getType();
                String systemPrompt = StringUtils.hasText(task.getSystemPrompt())
                        ? task.getSystemPrompt()
                        : EnricherPromptManager.systemPrompt(type);
                String userPrompt = buildUserPrompt(task.getUserPromptTemplate(), chunk, context);
                ChatRequest request = ChatRequest.builder()
                        .messages(List.of(ChatMessage.system(systemPrompt == null ? "" : systemPrompt), ChatMessage.user(userPrompt)))
                        .build();
                String response = llmService.chat(request, settings.getModelId());
                applyResult(chunk, type, response);
            }
        }
        return NodeResult.ok("enricher completed");
    }

    private EnricherSettings parseSettings(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isNull()) {
            return EnricherSettings.builder().tasks(List.of()).build();
        }
        return objectMapper.convertValue(node, EnricherSettings.class);
    }

    private String buildUserPrompt(String template, VectorChunk chunk, IngestionContext context) {
        String input = chunk.getContent();
        if (!StringUtils.hasText(template)) {
            return input;
        }
        Map<String, Object> vars = new HashMap<>();
        vars.put("text", input);
        vars.put("content", input);
        vars.put("chunkIndex", chunk.getIndex());
        vars.put("taskId", context.getTaskId());
        vars.put("pipelineId", context.getPipelineId());
        return PromptTemplateRenderer.render(template, vars);
    }

    private void applyResult(VectorChunk chunk, ChunkEnrichType type, String response) {
        switch (type) {
            case KEYWORDS -> chunk.getMetadata().put("keywords", JsonResponseParser.parseStringList(response));
            case SUMMARY -> chunk.getMetadata().put("summary", StringUtils.hasText(response) ? response.trim() : response);
            case METADATA -> chunk.getMetadata().putAll(JsonResponseParser.parseObject(response));
            default -> {
            }
        }
    }
}
