package com.personalblog.ragbackend.ingestion.node;

import com.personalblog.ragbackend.ingestion.domain.context.IngestionContext;
import com.personalblog.ragbackend.ingestion.domain.pipeline.NodeConfig;
import com.personalblog.ragbackend.ingestion.domain.result.NodeResult;

public interface IngestionNode {

    String getNodeType();

    NodeResult execute(IngestionContext context, NodeConfig config);
}
