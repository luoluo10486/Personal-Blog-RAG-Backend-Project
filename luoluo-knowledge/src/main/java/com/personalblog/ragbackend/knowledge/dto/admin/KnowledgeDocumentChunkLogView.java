package com.personalblog.ragbackend.knowledge.dto.admin;

import java.util.Date;

public record KnowledgeDocumentChunkLogView(
        String id,
        String docId,
        String status,
        String processMode,
        String chunkStrategy,
        String pipelineId,
        String pipelineName,
        Long extractDuration,
        Long chunkDuration,
        Long embedDuration,
        Long persistDuration,
        Long otherDuration,
        Long totalDuration,
        Integer chunkCount,
        String errorMessage,
        Date startTime,
        Date endTime,
        Date createTime
) {
}
