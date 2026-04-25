package com.personalblog.ragbackend.infra.ai.http;

import com.personalblog.ragbackend.infra.ai.config.AIModelProperties;
import com.personalblog.ragbackend.infra.ai.enums.ModelCapability;

public final class ModelUrlResolver {

    private ModelUrlResolver() {
    }

    public static String resolveUrl(AIModelProperties.ProviderConfig provider,
                                    AIModelProperties.ModelCandidate candidate,
                                    ModelCapability capability) {
        if (candidate != null && hasText(candidate.getUrl())) {
            return candidate.getUrl();
        }
        if (provider == null || !hasText(provider.getBaseUrl())) {
            throw new IllegalStateException("Provider baseUrl is missing");
        }
        String path = switch (capability) {
            case CHAT -> provider.getEndpoints().getChat();
            case EMBEDDING -> provider.getEndpoints().getEmbedding();
            case RERANK -> provider.getEndpoints().getRerank();
        };
        if (!hasText(path)) {
            throw new IllegalStateException("Provider endpoint is missing: " + capability.name().toLowerCase());
        }
        return joinUrl(provider.getBaseUrl(), path);
    }

    private static String joinUrl(String baseUrl, String path) {
        if (baseUrl.endsWith("/") && path.startsWith("/")) {
            return baseUrl + path.substring(1);
        }
        if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
            return baseUrl + "/" + path;
        }
        return baseUrl + path;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
