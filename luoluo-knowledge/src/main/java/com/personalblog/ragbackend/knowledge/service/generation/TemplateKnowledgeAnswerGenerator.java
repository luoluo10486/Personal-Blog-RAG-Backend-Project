package com.personalblog.ragbackend.knowledge.service.generation;

import com.personalblog.ragbackend.infra.ai.chat.LLMService;
import com.personalblog.ragbackend.infra.ai.convention.ChatMessage;
import com.personalblog.ragbackend.infra.ai.convention.ChatRequest;
import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TemplateKnowledgeAnswerGenerator implements KnowledgeAnswerGenerator {
    private static final int MAX_CONTEXT_CHARS = 6000;

    private final ObjectProvider<LLMService> llmServiceProvider;

    public TemplateKnowledgeAnswerGenerator(ObjectProvider<LLMService> llmServiceProvider) {
        this.llmServiceProvider = llmServiceProvider;
    }

    @Override
    @RagTraceNode(name = "answer-generate", type = "GENERATE")
    public String generate(String question, List<KnowledgeChunk> chunks) {
        return generate(question, List.of(), chunks);
    }

    @Override
    @RagTraceNode(name = "answer-generate", type = "GENERATE")
    public String generate(String question, List<ChatMessage> memory, List<KnowledgeChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "No sufficiently relevant knowledge was retrieved. Add documents, verify the knowledge base, or adjust retrieval settings and try again.";
        }

        LLMService llmService = llmServiceProvider.getIfAvailable();
        if (llmService == null) {
            return fallbackAnswer(chunks);
        }

        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system("You are a rigorous knowledge-base assistant. Answer only from the provided evidence. If the evidence is insufficient, say so directly. Keep the answer concise and cite evidence numbers for key conclusions."));
            if (memory != null && !memory.isEmpty()) {
                messages.addAll(memory);
            }
            messages.add(ChatMessage.user(buildPrompt(question, chunks)));

            ChatRequest request = ChatRequest.builder()
                    .messages(messages)
                    .temperature(0D)
                    .maxTokens(1024)
                    .build();
            return llmService.chat(request);
        } catch (RuntimeException ex) {
            return fallbackAnswer(chunks);
        }
    }

    private String buildPrompt(String question, List<KnowledgeChunk> chunks) {
        StringBuilder builder = new StringBuilder();
        builder.append("Question: ").append(question).append("\n\n");
        builder.append("Evidence:\n");
        int usedChars = 0;
        for (int index = 0; index < chunks.size(); index++) {
            KnowledgeChunk chunk = chunks.get(index);
            String content = safeTruncate(chunk.content(), 1200);
            usedChars += content.length();
            if (usedChars > MAX_CONTEXT_CHARS) {
                break;
            }
            builder.append("[").append(index + 1).append("] ")
                    .append(nullToEmpty(chunk.title()))
                    .append(" / chunk ").append(chunk.chunkIndex())
                    .append(" / score ").append(String.format("%.4f", chunk.score()))
                    .append("\n")
                    .append(content)
                    .append("\n\n");
        }
        builder.append("Answer the question using only the evidence above.");
        return builder.toString();
    }

    private String fallbackAnswer(List<KnowledgeChunk> chunks) {
        List<String> snippets = new ArrayList<>();
        for (int index = 0; index < Math.min(chunks.size(), 3); index++) {
            KnowledgeChunk chunk = chunks.get(index);
            snippets.add("[" + (index + 1) + "] " + safeTruncate(chunk.content(), 180));
        }
        return "Retrieved " + chunks.size() + " knowledge chunks. AI generation is unavailable, so here are the most relevant excerpts:\n"
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

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
