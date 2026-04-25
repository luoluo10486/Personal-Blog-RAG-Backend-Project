package com.personalblog.ragbackend.infra.ai.rerank;

import com.personalblog.ragbackend.infra.ai.convention.RetrievedChunk;
import com.personalblog.ragbackend.infra.ai.enums.ModelProvider;
import com.personalblog.ragbackend.infra.ai.model.ModelTarget;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NoopRerankClient implements RerankClient {

    @Override
    public String provider() {
        return ModelProvider.NOOP.getId();
    }

    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN, ModelTarget target) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (topN <= 0 || candidates.size() <= topN) {
            return candidates;
        }
        return candidates.stream().limit(topN).toList();
    }
}
