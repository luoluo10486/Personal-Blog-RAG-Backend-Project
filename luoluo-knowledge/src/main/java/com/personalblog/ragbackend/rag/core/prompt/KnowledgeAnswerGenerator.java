package com.personalblog.ragbackend.rag.core.prompt;

import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.ChatRequest;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;

import java.util.List;

public interface KnowledgeAnswerGenerator {
    String generate(String question, List<RetrievedChunk> chunks);

    default String generate(String question, List<ChatMessage> memory, List<RetrievedChunk> chunks) {
        return generate(question, chunks);
    }

    default String generate(String question, List<ChatMessage> memory, List<RetrievedChunk> chunks, String mcpContext) {
        return generate(question, memory, chunks);
    }

    default ChatRequest buildRequest(String question,
                                     List<ChatMessage> memory,
                                     List<RetrievedChunk> chunks,
                                     List<NodeScore> kbIntents,
                                     List<NodeScore> mcpIntents,
                                     String mcpContext,
                                     List<String> subQuestions,
                                     boolean deepThinking) {
        return null;
    }
}
