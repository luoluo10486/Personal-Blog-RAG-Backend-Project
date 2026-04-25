package com.personalblog.ragbackend.infra.ai.rerank;

import com.personalblog.ragbackend.infra.ai.convention.RetrievedChunk;

import java.util.List;

public interface RerankService {

    List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN);
}
