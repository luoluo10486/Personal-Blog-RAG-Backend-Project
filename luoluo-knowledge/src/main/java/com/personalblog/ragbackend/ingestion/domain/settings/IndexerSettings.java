package com.personalblog.ragbackend.ingestion.domain.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndexerSettings {

    private String embeddingModel;
    @Builder.Default
    private List<String> metadataFields = List.of();
}
