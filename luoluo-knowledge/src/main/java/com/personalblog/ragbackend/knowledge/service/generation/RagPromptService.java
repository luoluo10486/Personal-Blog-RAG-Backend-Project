package com.personalblog.ragbackend.knowledge.service.generation;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.ai.convention.ChatMessage;
import com.personalblog.ragbackend.infra.ai.convention.ChatRequest;
import com.personalblog.ragbackend.knowledge.service.prompt.PromptTemplateLoader;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RagPromptService {
    private static final String CHAT_SYSTEM_PROMPT_PATH = "prompt/answer-chat-system.st";
    private static final String KB_PROMPT_PATH = "prompt/answer-chat-kb.st";
    private static final String MCP_PROMPT_PATH = "prompt/answer-chat-mcp.st";
    private static final String MIXED_PROMPT_PATH = "prompt/answer-chat-mcp-kb-mixed.st";

    private final PromptTemplateLoader promptTemplateLoader;
    private final KnowledgeContextFormatter knowledgeContextFormatter;

    public RagPromptService(PromptTemplateLoader promptTemplateLoader,
                            KnowledgeContextFormatter knowledgeContextFormatter) {
        this.promptTemplateLoader = promptTemplateLoader;
        this.knowledgeContextFormatter = knowledgeContextFormatter;
    }

    public ChatRequest buildChatRequest(RagPromptContext promptContext,
                                        List<ChatMessage> history,
                                        boolean deepThinking) {
        PromptScene scene = resolveScene(promptContext);
        return ChatRequest.builder()
                .messages(buildStructuredMessages(promptContext, history, scene))
                .thinking(deepThinking)
                .temperature(resolveTemperature(scene))
                .topP(resolveTopP(scene))
                .maxTokens(1024)
                .build();
    }

    public List<ChatMessage> buildStructuredMessages(RagPromptContext promptContext,
                                                     List<ChatMessage> history) {
        return buildStructuredMessages(promptContext, history, resolveScene(promptContext));
    }

    private List<ChatMessage> buildStructuredMessages(RagPromptContext promptContext,
                                                      List<ChatMessage> history,
                                                      PromptScene scene) {
        List<ChatMessage> messages = new ArrayList<>();
        String systemPrompt = buildSystemPrompt(promptContext, scene);
        if (StrUtil.isNotBlank(systemPrompt)) {
            messages.add(ChatMessage.system(systemPrompt));
        }

        String mcpContext = knowledgeContextFormatter.formatMcpContext(promptContext.mcpContext());
        if (StrUtil.isNotBlank(mcpContext)) {
            messages.add(ChatMessage.system(formatEvidence("## 动态数据片段", mcpContext)));
        }

        String kbContext = knowledgeContextFormatter.formatKbContext(promptContext.kbIntents(), promptContext.chunks());
        if (StrUtil.isNotBlank(kbContext)) {
            messages.add(ChatMessage.user(formatEvidence("## 文档内容", kbContext)));
        }

        if (CollUtil.isNotEmpty(history)) {
            messages.addAll(history);
        }

        String userPrompt = buildUserPrompt(promptContext);
        if (StrUtil.isNotBlank(userPrompt)) {
            messages.add(ChatMessage.user(userPrompt));
        }
        return messages;
    }

    private String buildUserPrompt(RagPromptContext promptContext) {
        if (promptContext.hasSubQuestions() && promptContext.subQuestions().size() > 1) {
            StringBuilder builder = new StringBuilder();
            builder.append("请基于上述文档内容，回答以下问题：\n\n");
            for (int i = 0; i < promptContext.subQuestions().size(); i++) {
                builder.append(i + 1).append(". ").append(promptContext.subQuestions().get(i)).append("\n");
            }
            return builder.toString().trim();
        }
        return StrUtil.blankToDefault(promptContext.question(), "");
    }

    private String buildSystemPrompt(RagPromptContext promptContext, PromptScene scene) {
        String customTemplate = switch (scene) {
            case KB_ONLY -> resolveSingleIntentTemplate(promptContext.kbIntents(), KB_PROMPT_PATH);
            case MCP_ONLY -> resolveSingleIntentTemplate(promptContext.mcpIntents(), MCP_PROMPT_PATH);
            case MIXED -> promptTemplateLoader.load(MIXED_PROMPT_PATH);
            case EMPTY -> promptTemplateLoader.load(CHAT_SYSTEM_PROMPT_PATH);
        };
        return normalizeTemplate(customTemplate);
    }

    private PromptScene resolveScene(RagPromptContext promptContext) {
        if (promptContext == null) {
            return PromptScene.EMPTY;
        }
        boolean hasKb = promptContext.hasKb();
        boolean hasMcp = promptContext.hasMcp();
        if (hasKb && hasMcp) {
            return PromptScene.MIXED;
        }
        if (hasMcp) {
            return PromptScene.MCP_ONLY;
        }
        if (hasKb) {
            return PromptScene.KB_ONLY;
        }
        return PromptScene.EMPTY;
    }

    private String resolveSingleIntentTemplate(List<com.personalblog.ragbackend.knowledge.service.rag.intent.NodeScore> intents,
                                               String defaultPath) {
        if (CollUtil.isEmpty(intents) || intents.size() != 1 || intents.get(0) == null || intents.get(0).node() == null) {
            return promptTemplateLoader.load(defaultPath);
        }
        var node = intents.get(0).node();
        if (StrUtil.isNotBlank(node.promptTemplate)) {
            return node.promptTemplate;
        }
        if (StrUtil.isNotBlank(node.promptSnippet)) {
            return node.promptSnippet;
        }
        return promptTemplateLoader.load(defaultPath);
    }

    private double resolveTemperature(PromptScene scene) {
        return switch (scene) {
            case MCP_ONLY, MIXED -> 0.3D;
            case KB_ONLY -> 0D;
            case EMPTY -> 0.7D;
        };
    }

    private double resolveTopP(PromptScene scene) {
        return switch (scene) {
            case MCP_ONLY, MIXED -> 0.8D;
            case KB_ONLY, EMPTY -> 1D;
        };
    }

    private String formatEvidence(String header, String body) {
        return header + "\n" + StrUtil.blankToDefault(body, "").trim();
    }

    private String normalizeTemplate(String template) {
        if (StrUtil.isBlank(template)) {
            return "";
        }
        return template.replace("\r\n", "\n").trim();
    }

    private enum PromptScene {
        KB_ONLY,
        MCP_ONLY,
        MIXED,
        EMPTY
    }
}
