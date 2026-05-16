package com.personalblog.ragbackend.rag.core.prompt;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.core.prompt.PromptTemplateLoader;
import com.personalblog.ragbackend.rag.constant.RAGConstant;
import com.personalblog.ragbackend.rag.core.intent.IntentNode;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class RAGPromptService {
    private final PromptTemplateLoader promptTemplateLoader;

    public RAGPromptService(PromptTemplateLoader promptTemplateLoader) {
        this.promptTemplateLoader = promptTemplateLoader;
    }

    public String buildSystemPrompt(PromptContext context) {
        PromptBuildPlan plan = plan(context);
        String template = StrUtil.isNotBlank(plan.getBaseTemplate())
                ? plan.getBaseTemplate()
                : defaultTemplate(plan.getScene());
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
        if (CollUtil.isNotEmpty(history)) {
            messages.addAll(history);
        }

        String evidenceBody = buildEvidenceBody(context);
        String userQuestion = buildUserQuestion(question, subQuestions);
        String mergedUserContent = mergeEvidenceAndQuestion(evidenceBody, userQuestion);
        if (StrUtil.isNotBlank(mergedUserContent)) {
            messages.add(ChatMessage.user(mergedUserContent));
        }
        return messages;
    }

    private PromptBuildPlan plan(PromptContext context) {
        if (context == null) {
            throw new IllegalStateException("PromptContext requires MCP or KB context.");
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
        throw new IllegalStateException("PromptContext requires MCP or KB context.");
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

    private String buildEvidenceBody(PromptContext context) {
        if (context == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(context.getMcpContext())) {
            sb.append(renderSection("mcp-evidence", Map.of("body", context.getMcpContext().trim())));
        }
        if (StrUtil.isNotBlank(context.getKbContext())) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(renderSection("kb-evidence", Map.of("body", context.getKbContext().trim())));
        }
        return sb.toString().trim();
    }

    private String buildUserQuestion(String question, List<String> subQuestions) {
        if (CollUtil.isNotEmpty(subQuestions) && subQuestions.size() > 1) {
            String numbered = IntStream.range(0, subQuestions.size())
                    .mapToObj(i -> (i + 1) + ". " + subQuestions.get(i))
                    .collect(Collectors.joining("\n"));
            return renderSection("multi-questions", Map.of("questions", numbered));
        }
        if (StrUtil.isBlank(question)) {
            return "";
        }
        return renderSection("single-question", Map.of("question", question));
    }

    private String mergeEvidenceAndQuestion(String evidenceBody, String question) {
        if (StrUtil.isBlank(evidenceBody)) {
            return question;
        }
        if (StrUtil.isBlank(question)) {
            return evidenceBody;
        }
        return evidenceBody + "\n\n" + question;
    }

    private String renderSection(String section, Map<String, String> slots) {
        return promptTemplateLoader.renderSection(RAGConstant.CONTEXT_FORMAT_PATH, section, slots);
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
