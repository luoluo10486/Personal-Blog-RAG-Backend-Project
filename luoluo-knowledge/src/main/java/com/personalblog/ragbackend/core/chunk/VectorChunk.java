package com.personalblog.ragbackend.core.chunk;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VectorChunk {

    private String chunkId;

    private Integer index;

    private String content;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @JsonIgnore
    private float[] embedding;
}
