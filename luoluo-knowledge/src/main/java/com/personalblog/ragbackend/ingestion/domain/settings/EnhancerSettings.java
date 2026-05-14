package com.personalblog.ragbackend.ingestion.domain.settings;

import com.personalblog.ragbackend.ingestion.domain.enums.EnhanceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnhancerSettings {

    private String modelId;
    @Builder.Default
    private List<EnhanceTask> tasks = List.of();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnhanceTask {
        private EnhanceType type;
        private String systemPrompt;
        private String userPromptTemplate;
    }
}
