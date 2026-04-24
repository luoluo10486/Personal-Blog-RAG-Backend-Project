package com.personalblog.ragbackend.knowledge.service.generation;

import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;

import java.util.List;

public interface KnowledgeAnswerGenerator {
    String generate(String question, List<KnowledgeChunk> chunks);
}
