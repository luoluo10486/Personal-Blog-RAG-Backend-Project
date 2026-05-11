package com.personalblog.ragbackend.knowledge.service.generation;

import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import com.personalblog.ragbackend.knowledge.service.rag.intent.NodeScore;

import java.util.List;

public record RagPromptContext(
        String question,
        List<KnowledgeChunk> chunks,
        List<NodeScore> kbIntents,
        List<NodeScore> mcpIntents,
        String mcpContext
) {
}
