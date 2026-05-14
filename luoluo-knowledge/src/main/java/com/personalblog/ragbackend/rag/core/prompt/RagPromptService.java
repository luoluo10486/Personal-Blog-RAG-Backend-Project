package com.personalblog.ragbackend.rag.core.prompt;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.ChatRequest;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.knowledge.service.prompt.PromptTemplateLoader;
import com.personalblog.ragbackend.rag.core.intent.IntentNode;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import com.personalblog.ragbackend.rag.constant.RAGConstant;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class RAGPromptService {
    private static final String MCP_CONTEXT_HEADER = "## 动态数据片段";
    private static final String KB_CONTEXT_HEADER = "## 文档内容";

    private final PromptTemplateLoader promptTemplateLoader;

    public RAGPromptService(PromptTemplateLoader promptTemplateLoader) {
        this.promptTemplateLoader = promptTemplateLoader;
    }

    public ChatRequest buildChatRequest(PromptContext context,
                                        List<ChatMessage> history,
                                        boolean deepThinking) {
        return buildChatRequest(context, history, List.of(), deepThinking);
    }

    public ChatRequest buildChatRequest(PromptContext context,
                                        List<ChatMessage> history,
                                        List<String> subQuestions,
                                        boolean deepThinking) {
        PromptScene scene = resolveScene(context);
        return ChatRequest.builder()
                .messages(buildStructuredMessages(
                        context,
                        history,
                        scene,
                        context == null ? null : context.getQuestion(),
                        subQuestions
                ))
                .thinking(deepThinking)
                .temperature(resolveTemperature(scene))
                .topP(resolveTopP(scene))
                .build();
    }

    public List<ChatMessage> buildStructuredMessages(PromptContext context,
                                                     List<ChatMessage> history,
                                                     String question,
                                                     List<String> subQuestions) {
        return buildStructuredMessages(context, history, resolveScene(context), question, subQuestions);
    }

    private List<ChatMessage> buildStructuredMessages(PromptContext context,
                                                      List<ChatMessage> history,
                                                      PromptScene scene,
                                                      String question,
                                                      List<String> subQuestions) {
        List<ChatMessage> messages = new ArrayList<>();
        String systemPrompt = buildSystemPrompt(context, scene);
        if (StrUtil.isNotBlank(systemPrompt)) {
            messages.add(ChatMessage.system(systemPrompt));
        }
        if (context != null && StrUtil.isNotBlank(context.getMcpContext())) {
            messages.add(ChatMessage.system(formatEvidence(MCP_CONTEXT_HEADER, context.getMcpContext())));
        }
        if (context != null && StrUtil.isNotBlank(context.getKbContext())) {
            messages.add(ChatMessage.user(formatEvidence(KB_CONTEXT_HEADER, context.getKbContext())));
        }
        if (CollUtil.isNotEmpty(history)) {
            messages.addAll(history);
        }
        if (CollUtil.isNotEmpty(subQuestions) && subQuestions.size() > 1) {
            StringBuilder userMessage = new StringBuilder();
            userMessage.append("请基于上述文档内容，回答以下问题：\n\n");
            for (int i = 0; i < subQuestions.size(); i++) {
                userMessage.append(i + 1).append(". ").append(subQuestions.get(i)).append("\n");
            }
            messages.add(ChatMessage.user(userMessage.toString().trim()));
        } else if (StrUtil.isNotBlank(question)) {
            messages.add(ChatMessage.user(question));
        }
        return messages;
    }

    private String buildSystemPrompt(PromptContext context, PromptScene scene) {
        String customTemplate = switch (scene) {
            case KB_ONLY -> resolveSingleIntentTemplate(
                    context == null ? List.of() : context.getKbIntents(),
                    context == null ? Map.of() : context.getIntentChunks(),
                    RAGConstant.RAG_ENTERPRISE_PROMPT_PATH
            );
            case MCP_ONLY -> resolveSingleIntentTemplate(
                    context == null ? List.of() : context.getMcpIntents(),
                    Map.of(),
                    RAGConstant.MCP_ONLY_PROMPT_PATH
            );
            case MIXED -> promptTemplateLoader.load(RAGConstant.MCP_KB_MIXED_PROMPT_PATH);
            case EMPTY -> promptTemplateLoader.load(RAGConstant.CHAT_SYSTEM_PROMPT_PATH);
        };
        return normalizeTemplate(customTemplate);
    }

    private PromptScene resolveScene(PromptContext context) {
        if (context == null) {
            return PromptScene.EMPTY;
        }
        boolean hasKb = context.hasKb();
        boolean hasMcp = context.hasMcp();
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

    private String resolveSingleIntentTemplate(List<NodeScore> intents,
                                               Map<String, List<RetrievedChunk>> intentChunks,
                                               String defaultPath) {
        List<NodeScore> retained = retainHitIntents(intents, intentChunks);
        if (CollUtil.isEmpty(retained) || retained.size() != 1 || retained.get(0) == null || retained.get(0).node() == null) {
            return promptTemplateLoader.load(defaultPath);
        }
        IntentNode node = retained.get(0).node();
        if (StrUtil.isNotBlank(node.getPromptTemplate())) {
            return node.getPromptTemplate();
        }
        if (StrUtil.isNotBlank(node.getPromptSnippet())) {
            return node.getPromptSnippet();
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

    private List<NodeScore> retainHitIntents(List<NodeScore> intents, Map<String, List<RetrievedChunk>> intentChunks) {
        if (CollUtil.isEmpty(intents)) {
            return List.of();
        }
        if (intentChunks == null || intentChunks.isEmpty()) {
            return intents;
        }
        return intents.stream()
                .filter(intent -> {
                    if (intent == null || intent.node() == null) {
                        return false;
                    }
                    List<RetrievedChunk> chunks = intentChunks.get(nodeKey(intent.node()));
                    return CollUtil.isNotEmpty(chunks);
                })
                .toList();
    }

    private String nodeKey(IntentNode node) {
        if (node == null) {
            return "";
        }
        if (StrUtil.isNotBlank(node.getId())) {
            return node.getId();
        }
        if (StrUtil.isNotBlank(node.getIntentCode())) {
            return node.getIntentCode().trim();
        }
        if (StrUtil.isNotBlank(node.getCollectionName())) {
            return node.getCollectionName().trim();
        }
        return StrUtil.blankToDefault(node.getName(), "").trim();
    }
}
