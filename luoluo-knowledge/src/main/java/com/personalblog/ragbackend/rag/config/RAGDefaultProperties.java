package com.personalblog.ragbackend.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rag.default")
public class RAGDefaultProperties {

    private String collectionName;
    private Integer dimension;
    private String metricType;
    private Long sseTimeoutMs = 300000L;
}
