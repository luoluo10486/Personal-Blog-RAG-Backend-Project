package com.personalblog.ragbackend.knowledge.dto.rag;

import java.time.LocalDateTime;

public record RagTraceNodeView(
        Long id,
        String traceId,
        String nodeId,
        String parentNodeId,
        Integer depth,
        String nodeType,
        String nodeName,
        String className,
        String methodName,
        String status,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long durationMs,
        String extraData,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
