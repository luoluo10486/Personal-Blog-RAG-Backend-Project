package com.personalblog.ragbackend.rag.core.retrieve.postprocessor;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.config.SearchChannelProperties;
import com.personalblog.ragbackend.rag.core.retrieve.RetrieveRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConfidenceThresholdPostProcessor implements SearchResultPostProcessor {
    private final SearchChannelProperties searchChannelProperties;

    public ConfidenceThresholdPostProcessor(SearchChannelProperties searchChannelProperties) {
        this.searchChannelProperties = searchChannelProperties;
    }

    @Override
    public String getName() {
        return "ConfidenceThreshold";
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks, RetrieveRequest request) {
        double threshold = searchChannelProperties.getChannels().getVectorGlobal().getConfidenceThreshold();
        List<RetrievedChunk> filtered = chunks.stream()
                .filter(chunk -> chunk.getScore() != null && chunk.getScore() >= threshold)
                .toList();
        if (filtered.isEmpty()) {
            return chunks.stream().limit(request.topK()).toList();
        }
        return filtered;
    }
}
