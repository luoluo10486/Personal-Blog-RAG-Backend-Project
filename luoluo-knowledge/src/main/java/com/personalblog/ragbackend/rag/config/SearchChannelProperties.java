package com.personalblog.ragbackend.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rag.search")
public class SearchChannelProperties {

    private Channels channels = new Channels();

    @Data
    public static class Channels {
        private VectorGlobal vectorGlobal = new VectorGlobal();
        private IntentDirected intentDirected = new IntentDirected();
    }

    @Data
    public static class VectorGlobal {
        private boolean enabled = true;
        private double confidenceThreshold = 0.6;
        private double singleIntentSupplementThreshold = 0.8;
        private int topKMultiplier = 3;
    }

    @Data
    public static class IntentDirected {
        private boolean enabled = true;
        private double minIntentScore = 0.4;
        private int topKMultiplier = 2;
    }
}
