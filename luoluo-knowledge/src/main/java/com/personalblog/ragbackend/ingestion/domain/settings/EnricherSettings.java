package com.personalblog.ragbackend.ingestion.domain.settings;

import com.personalblog.ragbackend.ingestion.domain.enums.ChunkEnrichType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnricherSettings {

    private String modelId;
    private Boolean attachDocumentMetadata;
    @Builder.Default
    private List<ChunkEnrichTask> tasks = List.of();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChunkEnrichTask {
        private ChunkEnrichType type;
        private String systemPrompt;
        private String userPromptTemplate;
    }
}
