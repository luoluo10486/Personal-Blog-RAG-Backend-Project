package com.personalblog.ragbackend.rag.core.prompt;

import com.personalblog.ragbackend.infra.chat.LLMService;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.ChatRequest;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import com.personalblog.ragbackend.rag.core.intent.IntentNode;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TemplateKnowledgeAnswerGenerator implements KnowledgeAnswerGenerator {
    private final ObjectProvider<LLMService> llmServiceProvider;
    private final RAGPromptService ragPromptService;
    private final ContextFormatter contextFormatter;

    public TemplateKnowledgeAnswerGenerator(ObjectProvider<LLMService> llmServiceProvider,
                                            RAGPromptService ragPromptService,
                                            ContextFormatter contextFormatter) {
        this.llmServiceProvider = llmServiceProvider;
        this.ragPromptService = ragPromptService;
        this.contextFormatter = contextFormatter;
    }

    @Override
    @RagTraceNode(name = "answer-generate", type = "GENERATE")
    public String generate(String question, List<RetrievedChunk> chunks) {
        return generate(question, List.of(), chunks, "", List.of(), List.of(), List.of(), false);
    }

    @Override
    @RagTraceNode(name = "answer-generate", type = "GENERATE")
    public String generate(String question, List<ChatMessage> memory, List<RetrievedChunk> chunks) {
        return generate(question, memory, chunks, "", List.of(), List.of(), List.of(), false);
    }

    @Override
    @RagTraceNode(name = "answer-generate", type = "GENERATE")
    public String generate(String question, List<ChatMessage> memory, List<RetrievedChunk> chunks, String mcpContext) {
        return generate(question, memory, chunks, mcpContext, List.of(), List.of(), List.of(), false);
    }

    public String generate(String question,
                           List<ChatMessage> memory,
                           List<RetrievedChunk> chunks,
                           String mcpContext,
                           List<String> subQuestions,
                           List<NodeScore> kbIntents,
                           List<NodeScore> mcpIntents,
                           boolean deepThinking) {
        if ((chunks == null || chunks.isEmpty()) && (mcpContext == null || mcpContext.isBlank())) {
            return "当前没有足够的知识库或工具结果可用于回答。";
        }

        LLMService llmService = llmServiceProvider.getIfAvailable();
        if (llmService == null) {
            return fallbackAnswer(chunks, mcpContext);
        }

        try {
            ChatRequest request = buildRequest(question, memory, chunks, kbIntents, mcpIntents, mcpContext, subQuestions, deepThinking);
            return llmService.chat(request);
        } catch (RuntimeException ex) {
            return fallbackAnswer(chunks, mcpContext);
        }
    }

    @Override
    public ChatRequest buildRequest(String question,
                                    List<ChatMessage> memory,
                                    List<RetrievedChunk> chunks,
                                    List<NodeScore> kbIntents,
                                    List<NodeScore> mcpIntents,
                                    String mcpContext,
                                    List<String> subQuestions,
                                    boolean deepThinking) {
        Map<String, List<RetrievedChunk>> intentChunks = buildIntentChunkMap(kbIntents, chunks);
        return ragPromptService.buildChatRequest(
                PromptContext.builder()
                        .question(question)
                        .mcpContext(mcpContext)
                        .kbContext(contextFormatter.formatKbContext(kbIntents, intentChunks, chunks == null ? 0 : chunks.size()))
                        .mcpIntents(mcpIntents)
                        .kbIntents(kbIntents)
                        .intentChunks(intentChunks)
                        .build(),
                memory,
                subQuestions,
                deepThinking
        );
    }

    private String fallbackAnswer(List<RetrievedChunk> chunks, String mcpContext) {
        List<String> snippets = new ArrayList<>();
        if (chunks != null) {
            for (int index = 0; index < Math.min(chunks.size(), 3); index++) {
                RetrievedChunk chunk = chunks.get(index);
                snippets.add("[K" + (index + 1) + "] " + safeTruncate(chunk.getText(), 180));
            }
        }
        if (mcpContext != null && !mcpContext.isBlank()) {
            snippets.add("[M1] " + safeTruncate(mcpContext, 300));
        }
        return "当前未能生成完整答案。\n" + snippets.stream().collect(Collectors.joining("\n"));
    }

    private String safeTruncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private Map<String, List<RetrievedChunk>> buildIntentChunkMap(List<NodeScore> kbIntents, List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return Map.of();
        }
        Map<String, List<RetrievedChunk>> intentChunks = new LinkedHashMap<>();
        if (kbIntents == null || kbIntents.isEmpty()) {
            intentChunks.put("multi_channel", chunks);
            return intentChunks;
        }
        for (NodeScore nodeScore : kbIntents) {
            if (nodeScore == null || nodeScore.node() == null) {
                continue;
            }
            String key = nodeKey(nodeScore.node());
            if (key.isBlank()) {
                continue;
            }
            intentChunks.put(key, chunks);
        }
        return intentChunks;
    }

    private String nodeKey(IntentNode node) {
        if (node == null) {
            return "";
        }
        if (node.getId() != null && !node.getId().isBlank()) {
            return node.getId().trim();
        }
        if (node.getIntentCode() != null && !node.getIntentCode().isBlank()) {
            return node.getIntentCode().trim();
        }
        if (node.getCollectionName() != null && !node.getCollectionName().isBlank()) {
            return node.getCollectionName().trim();
        }
        return node.getName() == null ? "" : node.getName().trim();
    }
}
