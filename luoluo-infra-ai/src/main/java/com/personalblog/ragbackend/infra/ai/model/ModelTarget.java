package com.personalblog.ragbackend.infra.ai.model;

import com.personalblog.ragbackend.infra.ai.config.AIModelProperties;

public record ModelTarget(
        String id,
        AIModelProperties.ModelCandidate candidate,
        AIModelProperties.ProviderConfig provider
) {
}
