package com.personalblog.ragbackend.rag.core.prompt;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.ChatRequest;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.knowledge.service.prompt.PromptTemplateLoader;
import com.personalblog.ragbackend.rag.constant.RAGConstant;
import com.personalblog.ragbackend.rag.core.intent.IntentNode;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class RAGPromptService {
    private static final String MCP_CONTEXT_HEADER = "## 动态数据片段";
    private static final String KB_CONTEXT_HEADER = "## 知识库内容";

    private final PromptTemplateLoader promptTemplateLoader;

    public RAGPromptService(PromptTemplateLoader promptTemplateLoader) {
        this.promptTemplateLoader = promptTemplateLoader;
    }

    public String buildSystemPrompt(PromptContext context) {
        PromptPlan plan = planPrompt(context == null ? List.of() : context.getKbIntents(),
                context == null ? Map.of() : context.getIntentChunks());
        String template;
        if (context == null) {
            template = "";
        } else if (context.hasMcp() && !context.hasKb()) {
            template = resolveMcpOnlyTemplate(context);
        } else if (!context.hasMcp() && context.hasKb()) {
            template = plan.getBaseTemplate() != null ? plan.getBaseTemplate() : defaultTemplate(PromptScene.KB_ONLY);
        } else if (context.hasMcp() && context.hasKb()) {
            template = defaultTemplate(PromptScene.MIXED);
        } else {
            template = "";
        }
        return StrUtil.isBlank(template) ? "" : PromptTemplateUtils.cleanupPrompt(template);
    }

    public List<ChatMessage> buildStructuredMessages(PromptContext context,
                                                     List<ChatMessage> history,
                                                     String question,
                                                     List<String> subQuestions) {
        List<ChatMessage> messages = new ArrayList<>();
        String systemPrompt = buildSystemPrompt(context);
        if (StrUtil.isNotBlank(systemPrompt)) {
            messages.add(ChatMessage.system(systemPrompt));
        }
        if (StrUtil.isNotBlank(context.getMcpContext())) {
            messages.add(ChatMessage.system(formatEvidence(MCP_CONTEXT_HEADER, context.getMcpContext())));
        }
        if (StrUtil.isNotBlank(context.getKbContext())) {
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

    private PromptBuildPlan plan(PromptContext context) {
        if (context == null) {
            return PromptBuildPlan.builder().scene(PromptScene.EMPTY).build();
        }
        if (context.hasMcp() && !context.hasKb()) {
            return planMcpOnly(context);
        }
        if (!context.hasMcp() && context.hasKb()) {
            return planKbOnly(context);
        }
        if (context.hasMcp() && context.hasKb()) {
            return planMixed(context);
        }
        return PromptBuildPlan.builder().scene(PromptScene.EMPTY).build();
    }

    private PromptBuildPlan planKbOnly(PromptContext context) {
        PromptPlan plan = planPrompt(context.getKbIntents(), context.getIntentChunks());
        return PromptBuildPlan.builder()
                .scene(PromptScene.KB_ONLY)
                .baseTemplate(plan.getBaseTemplate())
                .mcpContext(context.getMcpContext())
                .kbContext(context.getKbContext())
                .question(context.getQuestion())
                .build();
    }

    private PromptBuildPlan planMcpOnly(PromptContext context) {
        List<NodeScore> intents = context.getMcpIntents();
        String baseTemplate = null;
        if (CollUtil.isNotEmpty(intents) && intents.size() == 1) {
            IntentNode node = intents.get(0).node();
            if (node != null && StrUtil.isNotBlank(node.getPromptTemplate())) {
                baseTemplate = node.getPromptTemplate();
            }
        }
        return PromptBuildPlan.builder()
                .scene(PromptScene.MCP_ONLY)
                .baseTemplate(baseTemplate)
                .mcpContext(context.getMcpContext())
                .kbContext(context.getKbContext())
                .question(context.getQuestion())
                .build();
    }

    private PromptBuildPlan planMixed(PromptContext context) {
        return PromptBuildPlan.builder()
                .scene(PromptScene.MIXED)
                .mcpContext(context.getMcpContext())
                .kbContext(context.getKbContext())
                .question(context.getQuestion())
                .build();
    }

    private String resolveMcpOnlyTemplate(PromptContext context) {
        List<NodeScore> intents = context.getMcpIntents();
        if (CollUtil.isNotEmpty(intents) && intents.size() == 1) {
            IntentNode node = intents.get(0).node();
            if (node != null && StrUtil.isNotBlank(node.getPromptTemplate())) {
                return node.getPromptTemplate();
            }
        }
        return defaultTemplate(PromptScene.MCP_ONLY);
    }

    private PromptPlan planPrompt(List<NodeScore> intents, Map<String, List<RetrievedChunk>> intentChunks) {
        List<NodeScore> safeIntents = intents == null ? Collections.emptyList() : intents;
        List<NodeScore> retained = safeIntents.stream()
                .filter(ns -> {
                    IntentNode node = ns == null ? null : ns.node();
                    String key = nodeKey(node);
                    List<RetrievedChunk> chunks = intentChunks == null ? null : intentChunks.get(key);
                    return CollUtil.isNotEmpty(chunks);
                })
                .toList();

        if (retained.isEmpty()) {
            return new PromptPlan(Collections.emptyList(), null);
        }
        if (retained.size() == 1) {
            IntentNode only = retained.get(0).node();
            String tpl = only == null ? "" : StrUtil.emptyIfNull(only.getPromptTemplate()).trim();
            if (StrUtil.isNotBlank(tpl)) {
                return new PromptPlan(retained, tpl);
            }
            return new PromptPlan(retained, null);
        }
        return new PromptPlan(retained, null);
    }

    private String defaultTemplate(PromptScene scene) {
        return switch (scene) {
            case KB_ONLY -> promptTemplateLoader.load(RAGConstant.RAG_ENTERPRISE_PROMPT_PATH);
            case MCP_ONLY -> promptTemplateLoader.load(RAGConstant.MCP_ONLY_PROMPT_PATH);
            case MIXED -> promptTemplateLoader.load(RAGConstant.MCP_KB_MIXED_PROMPT_PATH);
            case EMPTY -> "";
        };
    }

    private String formatEvidence(String header, String body) {
        return header + "\n" + StrUtil.blankToDefault(body, "").trim();
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
