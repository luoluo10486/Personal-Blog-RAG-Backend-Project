package com.personalblog.ragbackend.ingestion.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.ingestion.domain.context.IngestionContext;
import com.personalblog.ragbackend.ingestion.domain.enums.EnhanceType;
import com.personalblog.ragbackend.ingestion.domain.enums.IngestionNodeType;
import com.personalblog.ragbackend.ingestion.domain.pipeline.NodeConfig;
import com.personalblog.ragbackend.ingestion.domain.result.NodeResult;
import com.personalblog.ragbackend.ingestion.domain.settings.EnhancerSettings;
import com.personalblog.ragbackend.ingestion.prompt.EnhancerPromptManager;
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
public class EnhancerNode implements IngestionNode {

    private final ObjectMapper objectMapper;
    private final LLMService llmService;

    public EnhancerNode(ObjectMapper objectMapper, LLMService llmService) {
        this.objectMapper = objectMapper;
        this.llmService = llmService;
    }

    @Override
    public String getNodeType() {
        return IngestionNodeType.ENHANCER.getValue();
    }

    @Override
    public NodeResult execute(IngestionContext context, NodeConfig config) {
        EnhancerSettings settings = parseSettings(config.getSettings());
        if (settings.getTasks() == null || settings.getTasks().isEmpty()) {
            return NodeResult.ok("no enhancer tasks");
        }
        if (context.getMetadata() == null) {
            context.setMetadata(new HashMap<>());
        }

        for (EnhancerSettings.EnhanceTask task : settings.getTasks()) {
            if (task == null || task.getType() == null) {
                continue;
            }
            EnhanceType type = task.getType();
            String input = resolveInputText(context, type);
            if (!StringUtils.hasText(input)) {
                continue;
            }
            String systemPrompt = StringUtils.hasText(task.getSystemPrompt())
                    ? task.getSystemPrompt()
                    : EnhancerPromptManager.systemPrompt(type);
            String userPrompt = buildUserPrompt(task.getUserPromptTemplate(), input, context);
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(ChatMessage.system(systemPrompt == null ? "" : systemPrompt), ChatMessage.user(userPrompt)))
                    .build();
            String response = llmService.chat(request, settings.getModelId());
            applyTaskResult(context, type, response);
        }
        return NodeResult.ok("enhancer completed");
    }

    private EnhancerSettings parseSettings(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isNull()) {
            return EnhancerSettings.builder().tasks(List.of()).build();
        }
        return objectMapper.convertValue(node, EnhancerSettings.class);
    }

    private String resolveInputText(IngestionContext context, EnhanceType type) {
        if (type == EnhanceType.CONTEXT_ENHANCE) {
            return context.getRawText();
        }
        return StringUtils.hasText(context.getEnhancedText()) ? context.getEnhancedText() : context.getRawText();
    }

    private String buildUserPrompt(String template, String input, IngestionContext context) {
        if (!StringUtils.hasText(template)) {
            return input;
        }
        Map<String, Object> vars = new HashMap<>();
        vars.put("text", input);
        vars.put("content", input);
        vars.put("mimeType", context.getMimeType());
        vars.put("taskId", context.getTaskId());
        vars.put("pipelineId", context.getPipelineId());
        return PromptTemplateRenderer.render(template, vars);
    }

    private void applyTaskResult(IngestionContext context, EnhanceType type, String response) {
        switch (type) {
            case CONTEXT_ENHANCE -> context.setEnhancedText(StringUtils.hasText(response) ? response.trim() : response);
            case KEYWORDS -> context.setKeywords(JsonResponseParser.parseStringList(response));
            case QUESTIONS -> context.setQuestions(JsonResponseParser.parseStringList(response));
            case METADATA -> context.getMetadata().putAll(JsonResponseParser.parseObject(response));
            default -> {
            }
        }
    }
}
