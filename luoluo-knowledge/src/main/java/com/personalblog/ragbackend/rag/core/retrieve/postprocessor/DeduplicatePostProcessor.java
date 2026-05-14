package com.personalblog.ragbackend.rag.core.retrieve.postprocessor;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.core.retrieve.RetrieveRequest;
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
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks, RetrieveRequest request) {
        Map<String, RetrievedChunk> deduplicated = new LinkedHashMap<>();
        for (RetrievedChunk chunk : chunks) {
            String key = chunk.getId();
            if (key == null || key.isBlank()) {
                key = chunk.getText() == null ? "" : String.valueOf(chunk.getText().hashCode());
            }
            RetrievedChunk existing = deduplicated.get(key);
            if (existing == null || (chunk.getScore() != null && (existing.getScore() == null || chunk.getScore() > existing.getScore()))) {
                deduplicated.put(key, chunk);
            }
        }
        return List.copyOf(deduplicated.values());
    }
}
