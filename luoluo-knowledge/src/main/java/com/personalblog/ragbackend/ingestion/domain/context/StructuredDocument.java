package com.personalblog.ragbackend.ingestion.domain.context;

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
public class StructuredDocument {
    private String text;
    private List<StructuredSection> sections;
    private List<StructuredTable> tables;
    private Map<String, Object> metadata;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StructuredSection {
        private String title;
        private Integer level;
        private String content;
        private Integer startOffset;
        private Integer endOffset;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StructuredTable {
        private String title;
        private List<List<String>> rows;
        private Integer startOffset;
        private Integer endOffset;
    }
}
