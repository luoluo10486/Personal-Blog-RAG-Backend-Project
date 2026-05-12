package com.personalblog.ragbackend.knowledge.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "rag.semaphore")
public class RagSemaphoreProperties {
    @Valid
    private PermitExpirableConfig documentUpload = new PermitExpirableConfig();

    public PermitExpirableConfig getDocumentUpload() {
        return documentUpload;
    }

    public void setDocumentUpload(PermitExpirableConfig documentUpload) {
        this.documentUpload = documentUpload;
    }

    public static class PermitExpirableConfig {
        @NotBlank
        private String name = "rag:document:upload";
        @Min(1)
        private Integer maxConcurrent = 10;
        @Min(0)
        private Integer maxWaitSeconds = 30;
        @Min(1)
        private Integer leaseSeconds = 30;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getMaxConcurrent() {
            return maxConcurrent;
        }

        public void setMaxConcurrent(Integer maxConcurrent) {
            this.maxConcurrent = maxConcurrent;
        }

        public Integer getMaxWaitSeconds() {
            return maxWaitSeconds;
        }

        public void setMaxWaitSeconds(Integer maxWaitSeconds) {
            this.maxWaitSeconds = maxWaitSeconds;
        }

        public Integer getLeaseSeconds() {
            return leaseSeconds;
        }

        public void setLeaseSeconds(Integer leaseSeconds) {
            this.leaseSeconds = leaseSeconds;
        }
    }
}
