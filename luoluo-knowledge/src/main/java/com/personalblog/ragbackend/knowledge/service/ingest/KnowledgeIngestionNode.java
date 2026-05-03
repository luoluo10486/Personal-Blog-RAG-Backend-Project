package com.personalblog.ragbackend.knowledge.service.ingest;

public interface KnowledgeIngestionNode {

    String getNodeType();

    int getOrder();

    void execute(KnowledgeIngestionContext context);
}
