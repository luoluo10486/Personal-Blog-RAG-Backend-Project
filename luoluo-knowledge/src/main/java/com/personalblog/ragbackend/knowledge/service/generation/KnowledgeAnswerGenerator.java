package com.personalblog.ragbackend.knowledge.service.generation;

import com.personalblog.ragbackend.infra.ai.convention.ChatMessage;
import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;

import java.util.List;

public interface KnowledgeAnswerGenerator {
    String generate(String question, List<KnowledgeChunk> chunks);

    default String generate(String question, List<ChatMessage> memory, List<KnowledgeChunk> chunks) {
        return generate(question, chunks);
    }

    default String generate(String question, List<ChatMessage> memory, List<KnowledgeChunk> chunks, String mcpContext) {
        return generate(question, memory, chunks);
    }
}
