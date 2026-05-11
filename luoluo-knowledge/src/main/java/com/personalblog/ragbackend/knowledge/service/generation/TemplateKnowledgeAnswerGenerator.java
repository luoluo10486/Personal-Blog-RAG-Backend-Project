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
    private static final int MAX_MCP_CONTEXT_CHARS = 4000;

    private final ObjectProvider<LLMService> llmServiceProvider;

    public TemplateKnowledgeAnswerGenerator(ObjectProvider<LLMService> llmServiceProvider) {
        this.llmServiceProvider = llmServiceProvider;
    }

    @Override
    @RagTraceNode(name = "answer-generate", type = "GENERATE")
    public String generate(String question, List<KnowledgeChunk> chunks) {
        return generate(question, List.of(), chunks, "");
    }

    @Override
    @RagTraceNode(name = "answer-generate", type = "GENERATE")
    public String generate(String question, List<ChatMessage> memory, List<KnowledgeChunk> chunks) {
        return generate(question, memory, chunks, "");
    }

    @Override
    @RagTraceNode(name = "answer-generate", type = "GENERATE")
    public String generate(String question, List<ChatMessage> memory, List<KnowledgeChunk> chunks, String mcpContext) {
        if ((chunks == null || chunks.isEmpty()) && (mcpContext == null || mcpContext.isBlank())) {
            return "No sufficiently relevant knowledge was retrieved. Add documents, verify the knowledge base, or adjust retrieval settings and try again.";
        }

        LLMService llmService = llmServiceProvider.getIfAvailable();
        if (llmService == null) {
            return fallbackAnswer(chunks, mcpContext);
        }

        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system("You are a rigorous RAG assistant. Answer only from the provided knowledge evidence and dynamic tool evidence. If the evidence is insufficient, say so directly. Keep the answer concise and cite [Kx] or [Mx] when making key claims."));
            if (memory != null && !memory.isEmpty()) {
                messages.addAll(memory);
            }
            messages.add(ChatMessage.user(buildPrompt(question, chunks, mcpContext)));

            ChatRequest request = ChatRequest.builder()
                    .messages(messages)
                    .temperature(0D)
                    .maxTokens(1024)
                    .build();
            return llmService.chat(request);
        } catch (RuntimeException ex) {
            return fallbackAnswer(chunks, mcpContext);
        }
    }

    private String buildPrompt(String question, List<KnowledgeChunk> chunks, String mcpContext) {
        StringBuilder builder = new StringBuilder();
        builder.append("Question: ").append(question).append("\n\n");
        if (chunks != null && !chunks.isEmpty()) {
            builder.append("Knowledge evidence:\n");
            int usedChars = 0;
            for (int index = 0; index < chunks.size(); index++) {
                KnowledgeChunk chunk = chunks.get(index);
                String content = safeTruncate(chunk.content(), 1200);
                usedChars += content.length();
                if (usedChars > MAX_CONTEXT_CHARS) {
                    break;
                }
                builder.append("[K").append(index + 1).append("] ")
                        .append(nullToEmpty(chunk.title()))
                        .append(" / chunk ").append(chunk.chunkIndex())
                        .append(" / score ").append(String.format("%.4f", chunk.score()))
                        .append("\n")
                        .append(content)
                        .append("\n\n");
            }
        }
        if (mcpContext != null && !mcpContext.isBlank()) {
            builder.append("Dynamic tool evidence:\n");
            builder.append(safeTruncate(mcpContext, MAX_MCP_CONTEXT_CHARS)).append("\n\n");
        }
        builder.append("Answer the question using only the evidence above.");
        return builder.toString();
    }

    private String fallbackAnswer(List<KnowledgeChunk> chunks, String mcpContext) {
        List<String> snippets = new ArrayList<>();
        if (chunks != null) {
            for (int index = 0; index < Math.min(chunks.size(), 3); index++) {
                KnowledgeChunk chunk = chunks.get(index);
                snippets.add("[K" + (index + 1) + "] " + safeTruncate(chunk.content(), 180));
            }
        }
        if (mcpContext != null && !mcpContext.isBlank()) {
            snippets.add("[M1] " + safeTruncate(mcpContext, 300));
        }
        return "Retrieved " + (chunks == null ? 0 : chunks.size()) + " knowledge chunks. AI generation is unavailable, so here are the most relevant excerpts:\n"
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
