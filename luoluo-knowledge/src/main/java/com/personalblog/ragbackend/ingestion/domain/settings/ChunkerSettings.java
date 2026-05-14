package com.personalblog.ragbackend.ingestion.domain.settings;

import com.personalblog.ragbackend.knowledge.core.chunk.ChunkingMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChunkerSettings {

    private ChunkingMode strategy;
    private Integer chunkSize;
    private Integer overlapSize;
    private String separator;
}
