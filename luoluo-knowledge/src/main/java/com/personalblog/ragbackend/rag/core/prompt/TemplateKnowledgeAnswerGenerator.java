package com.personalblog.ragbackend.rag.core.prompt;

import com.personalblog.ragbackend.infra.chat.LLMService;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.ChatRequest;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TemplateKnowledgeAnswerGenerator implements KnowledgeAnswerGenerator {
    private final ObjectProvider<LLMService> llmServiceProvider;
    private final RAGPromptService ragPromptService;
    private final KnowledgeContextFormatter knowledgeContextFormatter;

    public TemplateKnowledgeAnswerGenerator(ObjectProvider<LLMService> llmServiceProvider,
                                            RAGPromptService ragPromptService,
                                            KnowledgeContextFormatter knowledgeContextFormatter) {
        this.llmServiceProvider = llmServiceProvider;
        this.ragPromptService = ragPromptService;
        this.knowledgeContextFormatter = knowledgeContextFormatter;
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
            return "閺堫亝顥呯槐銏犲煂娑撳酣妫舵０妯兼祲閸忓磭娈戦弬鍥ㄣ€傞崘鍛啇閵?";
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
        return ragPromptService.buildChatRequest(
                PromptContext.builder()
                        .question(question)
                        .mcpContext(mcpContext)
                        .kbContext(knowledgeContextFormatter.formatKbContext(kbIntents, chunks))
                        .mcpIntents(mcpIntents)
                        .kbIntents(kbIntents)
                        .intentChunks(Map.of())
                        .build(),
                memory,
                subQuestions,
                deepThinking
        );
    }

    private String fallbackAnswer(List<RetrievedChunk> chunks, String mcpContext) {
        List<String> snippets = new java.util.ArrayList<>();
        if (chunks != null) {
            for (int index = 0; index < Math.min(chunks.size(), 3); index++) {
                RetrievedChunk chunk = chunks.get(index);
                snippets.add("[K" + (index + 1) + "] " + safeTruncate(chunk.getText(), 180));
            }
        }
        if (mcpContext != null && !mcpContext.isBlank()) {
            snippets.add("[M1] " + safeTruncate(mcpContext, 300));
        }
        return "濡€崇€烽弳鍌欑瑝閸欘垳鏁ら敍灞藉帥缂佹瑤缍橀惄绋垮彠閻楀洦顔岄敍?n"
                + snippets.stream().collect(Collectors.joining("\n"));
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
}
