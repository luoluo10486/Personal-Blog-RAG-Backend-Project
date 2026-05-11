package com.personalblog.ragbackend.infra.rerank;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.infra.model.ModelTarget;

import java.util.List;

public interface RerankClient {

    String provider();

    List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN, ModelTarget target);
}
