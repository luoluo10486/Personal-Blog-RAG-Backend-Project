package com.personalblog.ragbackend.infra.ai.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.infra.ai.config.AIModelProperties;
import com.personalblog.ragbackend.infra.ai.enums.ModelProvider;
import com.personalblog.ragbackend.infra.ai.http.ModelClientErrorType;
import com.personalblog.ragbackend.infra.ai.http.ModelClientException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;

@Service
public class OllamaEmbeddingClient extends AbstractOpenAIStyleEmbeddingClient {

    public OllamaEmbeddingClient(@Qualifier("aiHttpClient") HttpClient httpClient,
                                 ObjectMapper objectMapper,
                                 AIModelProperties aiProperties) {
        super(httpClient, objectMapper, aiProperties);
    }

    @Override
    public String provider() {
        return ModelProvider.OLLAMA.getId();
    }

    @Override
    protected boolean requiresApiKey() {
        return false;
    }

    @Override
    protected List<List<Float>> parseEmbeddings(JsonNode response, int expectedSize) {
        JsonNode embeddings = response.path("embeddings");
        if (!embeddings.isArray() || embeddings.isEmpty() || embeddings.size() != expectedSize) {
            throw new ModelClientException(provider() + " embeddings response is invalid", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        List<List<Float>> results = new ArrayList<>(embeddings.size());
        for (JsonNode embedding : embeddings) {
            results.add(toVector(embedding));
        }
        return results;
    }
}
