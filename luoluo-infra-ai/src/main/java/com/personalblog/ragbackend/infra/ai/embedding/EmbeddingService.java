package com.personalblog.ragbackend.infra.ai.embedding;

import java.util.List;

public interface EmbeddingService {

    List<Float> embed(String text);

    List<Float> embed(String text, String modelId);

    List<List<Float>> embedBatch(List<String> texts);

    List<List<Float>> embedBatch(List<String> texts, String modelId);

    default int dimension() {
        return 0;
    }
}
