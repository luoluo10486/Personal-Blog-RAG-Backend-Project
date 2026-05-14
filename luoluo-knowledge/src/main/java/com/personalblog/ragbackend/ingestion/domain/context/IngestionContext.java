package com.personalblog.ragbackend.ingestion.domain.context;

import com.personalblog.ragbackend.core.chunk.VectorChunk;
import com.personalblog.ragbackend.ingestion.domain.enums.IngestionStatus;
import com.personalblog.ragbackend.rag.core.vector.VectorSpaceId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngestionContext {

    private String taskId;

    private String pipelineId;

    private DocumentSource source;

    private byte[] rawBytes;

    private String mimeType;

    private String rawText;

    private StructuredDocument document;

    private List<VectorChunk> chunks;

    private String enhancedText;

    private List<String> keywords;

    private List<String> questions;

    private Map<String, Object> metadata;

    private VectorSpaceId vectorSpaceId;

    private IngestionStatus status;

    private List<NodeLog> logs;

    private Throwable error;

    @Builder.Default
    private boolean skipIndexerWrite = false;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getPipelineId() {
        return pipelineId;
    }

    public void setPipelineId(String pipelineId) {
        this.pipelineId = pipelineId;
    }

    public DocumentSource getSource() {
        return source;
    }

    public void setSource(DocumentSource source) {
        this.source = source;
    }

    public byte[] getRawBytes() {
        return rawBytes;
    }

    public void setRawBytes(byte[] rawBytes) {
        this.rawBytes = rawBytes;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public StructuredDocument getDocument() {
        return document;
    }

    public void setDocument(StructuredDocument document) {
        this.document = document;
    }

    public List<VectorChunk> getChunks() {
        return chunks;
    }

    public void setChunks(List<VectorChunk> chunks) {
        this.chunks = chunks;
    }

    public String getEnhancedText() {
        return enhancedText;
    }

    public void setEnhancedText(String enhancedText) {
        this.enhancedText = enhancedText;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public List<String> getQuestions() {
        return questions;
    }

    public void setQuestions(List<String> questions) {
        this.questions = questions;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public VectorSpaceId getVectorSpaceId() {
        return vectorSpaceId;
    }

    public void setVectorSpaceId(VectorSpaceId vectorSpaceId) {
        this.vectorSpaceId = vectorSpaceId;
    }

    public IngestionStatus getStatus() {
        return status;
    }

    public void setStatus(IngestionStatus status) {
        this.status = status;
    }

    public List<NodeLog> getLogs() {
        return logs;
    }

    public void setLogs(List<NodeLog> logs) {
        this.logs = logs;
    }

    public Throwable getError() {
        return error;
    }

    public void setError(Throwable error) {
        this.error = error;
    }

    public boolean isSkipIndexerWrite() {
        return skipIndexerWrite;
    }

    public void setSkipIndexerWrite(boolean skipIndexerWrite) {
        this.skipIndexerWrite = skipIndexerWrite;
    }
}
