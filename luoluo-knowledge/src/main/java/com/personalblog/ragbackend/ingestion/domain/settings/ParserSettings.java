package com.personalblog.ragbackend.ingestion.domain.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParserSettings {

    @Builder.Default
    private List<ParserRule> rules = List.of();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParserRule {
        private String mimeType;
        private Map<String, Object> options;
    }
}
