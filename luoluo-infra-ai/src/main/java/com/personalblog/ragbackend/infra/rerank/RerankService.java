package com.personalblog.ragbackend.infra.rerank;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;

import java.util.List;

public interface RerankService {

    List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN);
}
