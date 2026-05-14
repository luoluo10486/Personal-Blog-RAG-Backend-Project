package com.personalblog.ragbackend.ingestion.controller.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class IngestionPipelineNodeRequest {
    private String nodeId;
    private String nodeType;
    private JsonNode settings;
    private JsonNode condition;
    private String nextNodeId;
}
