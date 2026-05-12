package com.personalblog.ragbackend.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "rag.knowledge.schedule")
public class KnowledgeScheduleProperties {
    private Long scanDelayMs = 10000L;
    private Long lockSeconds = 900L;
    private Integer batchSize = 20;
    private Long minIntervalSeconds = 60L;
    private Long runningTimeoutMinutes = 30L;

    public Long getScanDelayMs() {
        return scanDelayMs;
    }

    public void setScanDelayMs(Long scanDelayMs) {
        this.scanDelayMs = scanDelayMs;
    }

    public Long getLockSeconds() {
        return lockSeconds;
    }

    public void setLockSeconds(Long lockSeconds) {
        this.lockSeconds = lockSeconds;
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
    }

    public Long getMinIntervalSeconds() {
        return minIntervalSeconds;
    }

    public void setMinIntervalSeconds(Long minIntervalSeconds) {
        this.minIntervalSeconds = minIntervalSeconds;
    }

    public Long getRunningTimeoutMinutes() {
        return runningTimeoutMinutes;
    }

    public void setRunningTimeoutMinutes(Long runningTimeoutMinutes) {
        this.runningTimeoutMinutes = runningTimeoutMinutes;
    }
}
