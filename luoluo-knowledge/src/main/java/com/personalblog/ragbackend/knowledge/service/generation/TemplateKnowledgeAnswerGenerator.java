package com.personalblog.ragbackend.knowledge.service.generation;

import com.personalblog.ragbackend.infra.ai.chat.LLMService;
import com.personalblog.ragbackend.infra.ai.convention.ChatMessage;
import com.personalblog.ragbackend.infra.ai.convention.ChatRequest;
import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
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
    public String generate(String question, List<KnowledgeChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "知识库没有检索到足够相关的内容，建议补充文档、检查知识库编码或降低检索阈值后再试。";
        }

        LLMService llmService = llmServiceProvider.getIfAvailable();
        if (llmService == null) {
            return fallbackAnswer(chunks);
        }

        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(
                            ChatMessage.system("你是一个严谨的知识库问答助手。只能依据给定资料回答；资料不足时直接说明不足。回答要简洁，并在关键结论后标注引用编号。"),
                            ChatMessage.user(buildPrompt(question, chunks))
                    ))
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
        builder.append("问题：").append(question).append("\n\n");
        builder.append("资料：\n");
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
        builder.append("请基于以上资料回答问题。");
        return builder.toString();
    }

    private String fallbackAnswer(List<KnowledgeChunk> chunks) {
        List<String> snippets = new ArrayList<>();
        for (int index = 0; index < Math.min(chunks.size(), 3); index++) {
            KnowledgeChunk chunk = chunks.get(index);
            snippets.add("[" + (index + 1) + "] " + safeTruncate(chunk.content(), 180));
        }
        return "已召回 " + chunks.size() + " 条知识片段，当前 AI 生成不可用，先返回最相关摘录：\n"
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
