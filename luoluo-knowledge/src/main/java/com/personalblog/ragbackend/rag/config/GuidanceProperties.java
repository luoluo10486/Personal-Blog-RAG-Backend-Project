package com.personalblog.ragbackend.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rag.guidance")
public class GuidanceProperties {

    private Boolean enabled = true;
    private Double ambiguityScoreRatio = 0.8D;
    private Double ambiguityMargin = 0.15D;
    private Integer maxOptions = 3;
}
