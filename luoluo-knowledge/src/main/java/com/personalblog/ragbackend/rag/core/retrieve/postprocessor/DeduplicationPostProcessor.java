package com.personalblog.ragbackend.rag.core.retrieve.postprocessor;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchChannelResult;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchChannelType;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DeduplicationPostProcessor implements SearchResultPostProcessor {

    @Override
    public String getName() {
        return "Deduplication";
    }

    @Override
    public int getOrder() {
        return 1;
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks, List<SearchChannelResult> results, SearchContext context) {
        if (results == null || results.isEmpty()) {
            return deduplicateInOrder(chunks);
        }

        Map<String, RetrievedChunk> deduplicated = new LinkedHashMap<>();
        results.stream()
                .sorted((left, right) -> Integer.compare(
                        channelPriority(left == null ? null : left.getChannelType()),
                        channelPriority(right == null ? null : right.getChannelType())
                ))
                .forEach(result -> {
                    if (result == null || result.getChunks() == null) {
                        return;
                    }
                    for (RetrievedChunk chunk : result.getChunks()) {
                        if (chunk == null) {
                            continue;
                        }
                        String key = generateChunkKey(chunk);
                        RetrievedChunk existing = deduplicated.get(key);
                        if (existing == null || shouldReplace(existing, chunk)) {
                            deduplicated.put(key, chunk);
                        }
                    }
                });
        return new ArrayList<>(deduplicated.values());
    }

    private List<RetrievedChunk> deduplicateInOrder(List<RetrievedChunk> chunks) {
        Map<String, RetrievedChunk> deduplicated = new LinkedHashMap<>();
        if (chunks == null) {
            return List.of();
        }
        for (RetrievedChunk chunk : chunks) {
            if (chunk == null) {
                continue;
            }
            String key = generateChunkKey(chunk);
            RetrievedChunk existing = deduplicated.get(key);
            if (existing == null || shouldReplace(existing, chunk)) {
                deduplicated.put(key, chunk);
            }
        }
        return new ArrayList<>(deduplicated.values());
    }

    private boolean shouldReplace(RetrievedChunk existing, RetrievedChunk candidate) {
        Float existingScore = existing.getScore();
        Float candidateScore = candidate.getScore();
        if (existingScore == null) {
            return candidateScore != null;
        }
        if (candidateScore == null) {
            return false;
        }
        return candidateScore > existingScore;
    }

    private String generateChunkKey(RetrievedChunk chunk) {
        return chunk.getId() != null
                ? chunk.getId()
                : String.valueOf(chunk.getText() == null ? 0 : chunk.getText().hashCode());
    }

    private int channelPriority(SearchChannelType type) {
        if (type == null) {
            return Integer.MAX_VALUE;
        }
        return switch (type) {
            case INTENT_DIRECTED -> 1;
            case KEYWORD_ES -> 2;
            case VECTOR_GLOBAL -> 3;
            case HYBRID -> 4;
        };
    }
}
