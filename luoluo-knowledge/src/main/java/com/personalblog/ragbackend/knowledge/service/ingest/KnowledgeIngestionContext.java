package com.personalblog.ragbackend.knowledge.service.ingest;

import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunkResponse;
import com.personalblog.ragbackend.knowledge.dto.document.ParseResult;
import org.springframework.web.multipart.MultipartFile;

public class KnowledgeIngestionContext {
    private final String baseCode;
    private final MultipartFile file;

    private KnowledgeIngestionPlan plan;
    private ParseResult parseResult;
    private DocumentChunkResponse chunkResponse;

    public KnowledgeIngestionContext(String baseCode, MultipartFile file) {
        this.baseCode = baseCode;
        this.file = file;
    }

    public String getBaseCode() {
        return baseCode;
    }

    public MultipartFile getFile() {
        return file;
    }

    public KnowledgeIngestionPlan getPlan() {
        return plan;
    }

    public void setPlan(KnowledgeIngestionPlan plan) {
        this.plan = plan;
    }

    public ParseResult getParseResult() {
        return parseResult;
    }

    public void setParseResult(ParseResult parseResult) {
        this.parseResult = parseResult;
    }

    public DocumentChunkResponse getChunkResponse() {
        return chunkResponse;
    }

    public void setChunkResponse(DocumentChunkResponse chunkResponse) {
        this.chunkResponse = chunkResponse;
    }
}
