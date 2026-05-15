package com.personalblog.ragbackend.rag.core.retrieve;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.rag.core.retrieve.channel.SearchContext;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

@Deprecated
@Service
public class KnowledgeRetrievalEngine {
    private final MultiChannelRetrievalEngine multiChannelRetrievalEngine;

    public KnowledgeRetrievalEngine(MultiChannelRetrievalEngine multiChannelRetrievalEngine) {
        this.multiChannelRetrievalEngine = multiChannelRetrievalEngine;
    }

    public List<RetrievedChunk> retrieve(RetrieveRequest request) {
        return multiChannelRetrievalEngine.retrieveKnowledgeChannels(buildSearchContext(request));
    }

    private SearchContext buildSearchContext(RetrieveRequest request) {
        HashMap<String, Object> metadata = new HashMap<>();
        if (request.baseCode() != null) {
            metadata.put("baseCode", request.baseCode());
        }
        if (request.collectionName() != null) {
            metadata.put("collectionName", request.collectionName());
        }
        return SearchContext.builder()
                .originalQuestion(request.question())
                .rewrittenQuestion(request.question())
                .topK(request.topK())
                .metadata(metadata)
                .build();
    }
}
