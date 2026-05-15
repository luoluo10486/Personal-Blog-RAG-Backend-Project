package com.personalblog.ragbackend.ingestion.domain.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeLog {
    private String nodeId;
    private String nodeType;
    private String message;
    private long durationMs;
    private boolean success;
    private String error;
    private Map<String, Object> output;
}
