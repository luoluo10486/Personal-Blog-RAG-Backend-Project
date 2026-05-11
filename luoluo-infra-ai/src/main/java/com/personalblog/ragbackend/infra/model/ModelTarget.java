package com.personalblog.ragbackend.infra.model;

import com.personalblog.ragbackend.infra.config.AIModelProperties;

public record ModelTarget(
        String id,
        AIModelProperties.ModelCandidate candidate,
        AIModelProperties.ProviderConfig provider
) {
}
