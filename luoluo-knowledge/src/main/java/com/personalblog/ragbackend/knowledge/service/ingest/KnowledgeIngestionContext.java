package com.personalblog.ragbackend.knowledge.service.ingest;

import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeChunkEntity;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunk;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentChunkResponse;
import com.personalblog.ragbackend.knowledge.dto.document.DocumentIngestionSummary;
import com.personalblog.ragbackend.knowledge.dto.document.ParseResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

public class KnowledgeIngestionContext {
    private final String baseCode;
    private final MultipartFile file;
    private final KnowledgeIngestionMode mode;

    private KnowledgeIngestionPlan plan;
    private ParseResult parseResult;
    private DocumentChunkResponse chunkResponse;
    private List<DocumentChunk> chunks = List.of();
    private List<List<Float>> embeddings = List.of();
    private List<KnowledgeChunkEntity> persistedChunks = List.of();
    private List<String> staleVectorIds = List.of();
    private Long knowledgeBaseId;
    private Long documentId;
    private boolean vectorIndexed;
    private DocumentIngestionSummary ingestionSummary;

    public KnowledgeIngestionContext(String baseCode, MultipartFile file, KnowledgeIngestionMode mode) {
        this.baseCode = baseCode;
        this.file = file;
        this.mode = mode == null ? KnowledgeIngestionMode.PREVIEW : mode;
    }

    public String getBaseCode() {
        return baseCode;
    }

    public MultipartFile getFile() {
        return file;
    }

    public KnowledgeIngestionMode getMode() {
        return mode;
    }

    public boolean isPlanOnly() {
        return mode == KnowledgeIngestionMode.PLAN_ONLY;
    }

    public boolean isPreviewMode() {
        return mode == KnowledgeIngestionMode.PREVIEW;
    }

    public boolean isIngestMode() {
        return mode == KnowledgeIngestionMode.INGEST;
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
        this.chunks = chunkResponse == null || chunkResponse.chunks() == null
                ? List.of()
                : List.copyOf(chunkResponse.chunks());
    }

    public List<DocumentChunk> getChunks() {
        return chunks;
    }

    public void setChunks(List<DocumentChunk> chunks) {
        this.chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }

    public List<List<Float>> getEmbeddings() {
        return embeddings;
    }

    public void setEmbeddings(List<List<Float>> embeddings) {
        if (embeddings == null || embeddings.isEmpty()) {
            this.embeddings = List.of();
            return;
        }
        List<List<Float>> copied = new ArrayList<>(embeddings.size());
        for (List<Float> embedding : embeddings) {
            copied.add(embedding == null ? List.of() : List.copyOf(embedding));
        }
        this.embeddings = List.copyOf(copied);
    }

    public List<KnowledgeChunkEntity> getPersistedChunks() {
        return persistedChunks;
    }

    public void setPersistedChunks(List<KnowledgeChunkEntity> persistedChunks) {
        this.persistedChunks = persistedChunks == null ? List.of() : List.copyOf(persistedChunks);
    }

    public List<String> getStaleVectorIds() {
        return staleVectorIds;
    }

    public void setStaleVectorIds(List<String> staleVectorIds) {
        this.staleVectorIds = staleVectorIds == null ? List.of() : List.copyOf(staleVectorIds);
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public boolean isVectorIndexed() {
        return vectorIndexed;
    }

    public void setVectorIndexed(boolean vectorIndexed) {
        this.vectorIndexed = vectorIndexed;
    }

    public DocumentIngestionSummary getIngestionSummary() {
        return ingestionSummary;
    }

    public void setIngestionSummary(DocumentIngestionSummary ingestionSummary) {
        this.ingestionSummary = ingestionSummary;
    }
}
