package com.personalblog.ragbackend.rag.core.retrieve.postprocessor;

import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.config.SearchChannelProperties;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchChannelResult;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Deprecated(forRemoval = false)
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
        return 5;
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        return context != null && StrUtil.equalsIgnoreCase(context.getMetadataString("legacyConfidenceThreshold"), "true");
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks, List<SearchChannelResult> results, SearchContext context) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        double threshold = searchChannelProperties.getChannels().getVectorGlobal().getConfidenceThreshold();
        int fallbackTopK = context == null ? 1 : Math.max(context.getTopK(), 1);
        List<RetrievedChunk> filtered = chunks.stream()
                .filter(chunk -> chunk.getScore() != null && chunk.getScore() >= threshold)
                .toList();
        if (filtered.isEmpty()) {
            return chunks.stream().limit(fallbackTopK).toList();
        }
        return filtered;
    }
}
