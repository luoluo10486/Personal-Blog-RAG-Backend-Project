package com.personalblog.ragbackend.infra.ai.rerank;

import com.personalblog.ragbackend.infra.ai.convention.RetrievedChunk;
import com.personalblog.ragbackend.infra.ai.model.ModelTarget;

import java.util.List;

public interface RerankClient {

    String provider();

    List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN, ModelTarget target);
}
