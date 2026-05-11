package com.personalblog.ragbackend.rag.service;

import com.personalblog.ragbackend.knowledge.dao.entity.RagTraceNodeEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.RagTraceRunEntity;

import java.time.LocalDateTime;

public interface RagTraceRecordService {

    void startRun(RagTraceRunEntity run);

    void finishRun(String traceId,
                   String status,
                   String errorMessage,
                   LocalDateTime endedAt,
                   long durationMs,
                   String extraData);

    void startNode(RagTraceNodeEntity node);

    void finishNode(String traceId,
                    String nodeId,
                    String status,
                    String errorMessage,
                    LocalDateTime endedAt,
                    long durationMs,
                    String extraData);
}
