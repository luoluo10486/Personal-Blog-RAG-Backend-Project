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

    public ChunkingMode getStrategy() {
        return strategy;
    }

    public void setStrategy(ChunkingMode strategy) {
        this.strategy = strategy;
    }

    public Integer getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(Integer chunkSize) {
        this.chunkSize = chunkSize;
    }

    public Integer getOverlapSize() {
        return overlapSize;
    }

    public void setOverlapSize(Integer overlapSize) {
        this.overlapSize = overlapSize;
    }

    public String getSeparator() {
        return separator;
    }

    public void setSeparator(String separator) {
        this.separator = separator;
    }
}
