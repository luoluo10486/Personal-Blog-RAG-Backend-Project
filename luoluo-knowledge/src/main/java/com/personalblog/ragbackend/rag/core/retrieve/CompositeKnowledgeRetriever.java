package com.personalblog.ragbackend.rag.core.retrieve;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

@Service
@Primary
public class CompositeKnowledgeRetriever implements KnowledgeRetriever {
    private final MultiChannelRetrievalEngine multiChannelRetrievalEngine;

    public CompositeKnowledgeRetriever(MultiChannelRetrievalEngine multiChannelRetrievalEngine) {
        this.multiChannelRetrievalEngine = multiChannelRetrievalEngine;
    }

    @Override
    public List<RetrievedChunk> retrieve(String baseCode, String question, int topK) {
        HashMap<String, Object> metadata = new HashMap<>();
        if (baseCode != null) {
            metadata.put("baseCode", baseCode);
            metadata.put("collectionName", baseCode);
        }
        return multiChannelRetrievalEngine.retrieveKnowledgeChannels(SearchContext.builder()
                .originalQuestion(question)
                .rewrittenQuestion(question)
                .topK(topK)
                .metadata(metadata)
                .build());
    }
}
