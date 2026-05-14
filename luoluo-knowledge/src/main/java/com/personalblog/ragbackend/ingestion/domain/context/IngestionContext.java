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
}
