package com.personalblog.ragbackend.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag.trace")
public class RagTraceProperties {

    private boolean enabled = true;

    private int maxErrorLength = 1000;
}
