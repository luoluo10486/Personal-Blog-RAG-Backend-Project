package com.personalblog.ragbackend.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "rag.default")
public class RAGDefaultProperties {
    private String collectionName;
    private Integer dimension;
    private String metricType;
    private Long sseTimeoutMs = 300000L;

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public Integer getDimension() {
        return dimension;
    }

    public void setDimension(Integer dimension) {
        this.dimension = dimension;
    }

    public String getMetricType() {
        return metricType;
    }

    public void setMetricType(String metricType) {
        this.metricType = metricType;
    }

    public Long getSseTimeoutMs() {
        return sseTimeoutMs;
    }

    public void setSseTimeoutMs(Long sseTimeoutMs) {
        this.sseTimeoutMs = sseTimeoutMs;
    }
}
