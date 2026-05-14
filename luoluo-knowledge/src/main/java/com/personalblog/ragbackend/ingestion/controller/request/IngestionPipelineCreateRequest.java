package com.personalblog.ragbackend.ingestion.controller.request;

import lombok.Data;

import java.util.List;

@Data
public class IngestionPipelineCreateRequest {
    private String name;
    private String description;
    private List<IngestionPipelineNodeRequest> nodes;
}
