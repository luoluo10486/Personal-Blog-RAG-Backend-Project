package com.personalblog.ragbackend.rag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagRateLimitProperties {
    @Value("${rag.rate-limit.global.enabled:true}")
    private Boolean globalEnabled;

    @Value("${rag.rate-limit.global.max-concurrent:50}")
    private Integer globalMaxConcurrent;

    @Value("${rag.rate-limit.global.max-wait-seconds:20}")
    private Integer globalMaxWaitSeconds;

    @Value("${rag.rate-limit.global.lease-seconds:600}")
    private Integer globalLeaseSeconds;

    @Value("${rag.rate-limit.global.poll-interval-ms:200}")
    private Integer globalPollIntervalMs;

    public Boolean getGlobalEnabled() {
        return globalEnabled;
    }

    public Integer getGlobalMaxConcurrent() {
        return globalMaxConcurrent;
    }

    public Integer getGlobalMaxWaitSeconds() {
        return globalMaxWaitSeconds;
    }

    public Integer getGlobalLeaseSeconds() {
        return globalLeaseSeconds;
    }

    public Integer getGlobalPollIntervalMs() {
        return globalPollIntervalMs;
    }
}
