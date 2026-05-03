package com.personalblog.ragbackend.knowledge.service.retrieval.postprocessor;

import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import com.personalblog.ragbackend.knowledge.service.retrieval.RetrieveRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DeduplicatePostProcessor implements SearchResultPostProcessor {

    @Override
    public String getName() {
        return "Deduplicate";
    }

    @Override
    public int getOrder() {
        return 5;
    }

    @Override
    public List<KnowledgeChunk> process(List<KnowledgeChunk> chunks, RetrieveRequest request) {
        Map<String, KnowledgeChunk> deduplicated = new LinkedHashMap<>();
        for (KnowledgeChunk chunk : chunks) {
            String key = chunk.id();
            if (key == null || key.isBlank()) {
                key = chunk.documentId() + "#" + chunk.chunkIndex();
            }
            KnowledgeChunk existing = deduplicated.get(key);
            if (existing == null || chunk.score() > existing.score()) {
                deduplicated.put(key, chunk);
            }
        }
        return List.copyOf(deduplicated.values());
    }
}
