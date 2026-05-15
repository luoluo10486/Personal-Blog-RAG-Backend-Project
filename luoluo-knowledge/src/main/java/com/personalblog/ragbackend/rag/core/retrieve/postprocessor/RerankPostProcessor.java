package com.personalblog.ragbackend.rag.core.retrieve.postprocessor;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.infra.rerank.RerankService;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchChannelResult;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RerankPostProcessor implements SearchResultPostProcessor {
    private final ObjectProvider<RerankService> rerankServiceProvider;

    public RerankPostProcessor(ObjectProvider<RerankService> rerankServiceProvider) {
        this.rerankServiceProvider = rerankServiceProvider;
    }

    @Override
    public String getName() {
        return "Rerank";
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        return true;
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks, List<SearchChannelResult> results, SearchContext context) {
        if (chunks.isEmpty()) {
            return chunks;
        }
        RerankService rerankService = rerankServiceProvider.getIfAvailable();
        if (rerankService == null) {
            return chunks.stream().limit(Math.max(context.getTopK(), 1)).toList();
        }
        try {
            return rerankService.rerank(context.getMainQuestion(), chunks, Math.max(context.getTopK(), 1));
        } catch (RuntimeException ex) {
            return chunks.stream().limit(Math.max(context.getTopK(), 1)).toList();
        }
    }
}
