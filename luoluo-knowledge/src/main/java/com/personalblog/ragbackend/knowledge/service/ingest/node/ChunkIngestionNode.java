package com.personalblog.ragbackend.knowledge.service.ingest.node;

import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunkResponse;
import com.personalblog.ragbackend.knowledge.dto.document.ParseResult;
import com.personalblog.ragbackend.knowledge.service.document.KnowledgeDocumentChunkService;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionContext;
import com.personalblog.ragbackend.knowledge.service.ingest.KnowledgeIngestionNode;
import org.springframework.stereotype.Component;

@Component
public class ChunkIngestionNode implements KnowledgeIngestionNode {
    private final KnowledgeDocumentChunkService knowledgeDocumentChunkService;

    public ChunkIngestionNode(KnowledgeDocumentChunkService knowledgeDocumentChunkService) {
        this.knowledgeDocumentChunkService = knowledgeDocumentChunkService;
    }

    @Override
    public String getNodeType() {
        return "chunk";
    }

    @Override
    public int getOrder() {
        return 30;
    }

    @Override
    public void execute(KnowledgeIngestionContext context) {
        if (context.isPlanOnly()) {
            return;
        }

        ParseResult parseResult = context.getParseResult();
        if (parseResult == null) {
            context.setChunkResponse(DocumentChunkResponse.failure("瑙ｆ瀽缁撴灉涓嶈兘涓虹┖"));
            return;
        }

        context.setChunkResponse(knowledgeDocumentChunkService.chunkParsedResult(parseResult));
    }
}
