package com.personalblog.ragbackend.infra.ai.embedding;

import com.personalblog.ragbackend.infra.ai.model.ModelTarget;

import java.util.List;

public interface EmbeddingClient {

    String provider();

    List<Float> embed(String text, ModelTarget target);

    List<List<Float>> embedBatch(List<String> texts, ModelTarget target);
}
