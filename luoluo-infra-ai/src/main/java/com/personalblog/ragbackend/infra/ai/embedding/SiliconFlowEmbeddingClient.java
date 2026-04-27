package com.personalblog.ragbackend.infra.ai.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalblog.ragbackend.infra.ai.config.AIModelProperties;
import com.personalblog.ragbackend.infra.ai.enums.ModelProvider;
import com.personalblog.ragbackend.infra.ai.model.ModelTarget;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;

@Service
public class SiliconFlowEmbeddingClient extends AbstractOpenAIStyleEmbeddingClient {

    public SiliconFlowEmbeddingClient(@Qualifier("aiHttpClient") HttpClient httpClient,
                                      ObjectMapper objectMapper,
                                      AIModelProperties aiProperties) {
        super(httpClient, objectMapper, aiProperties);
    }

    @Override
    public String provider() {
        return ModelProvider.SILICON_FLOW.getId();
    }

    @Override
    protected void customizeRequestBody(ObjectNode body, ModelTarget target) {
        body.put("encoding_format", "float");
    }

    @Override
    protected int maxBatchSize() {
        return 32;
    }
}
