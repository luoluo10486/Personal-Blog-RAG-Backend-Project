package com.personalblog.ragbackend.infra.ai.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.infra.ai.config.AIModelProperties;
import com.personalblog.ragbackend.infra.ai.model.ModelTarget;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class HttpResponseHelper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpResponseHelper() {
    }

    public static String readBody(InputStream body) throws IOException {
        if (body == null) {
            return "";
        }
        return new String(body.readAllBytes(), StandardCharsets.UTF_8);
    }

    public static JsonNode parseJson(String body, String label) {
        if (body == null || body.isBlank()) {
            throw new ModelClientException(label + " 响应为空", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        try {
            return OBJECT_MAPPER.readTree(body);
        } catch (IOException ex) {
            throw new ModelClientException(label + " 响应不是有效 JSON", ModelClientErrorType.INVALID_RESPONSE, null, ex);
        }
    }

    public static AIModelProperties.ProviderConfig requireProvider(ModelTarget target, String label) {
        if (target == null || target.provider() == null) {
            throw new IllegalStateException(label + " provider configuration is missing");
        }
        return target.provider();
    }

    public static void requireApiKey(AIModelProperties.ProviderConfig provider, String label) {
        if (provider.getApiKey() == null || provider.getApiKey().isBlank()) {
            throw new IllegalStateException(label + " API key is missing");
        }
    }

    public static String requireModel(ModelTarget target, String label) {
        if (target == null || target.candidate() == null || target.candidate().getModel() == null) {
            throw new IllegalStateException(label + " model is missing");
        }
        return target.candidate().getModel();
    }
}
