package com.personalblog.ragbackend.rag.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.personalblog.ragbackend.knowledge.dao.entity.RagTraceNodeEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.RagTraceRunEntity;
import com.personalblog.ragbackend.knowledge.mapper.RagTraceNodeMapper;
import com.personalblog.ragbackend.knowledge.mapper.RagTraceRunMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class RagTraceRecordServiceImpl implements RagTraceRecordService {
    private final RagTraceRunMapper ragTraceRunMapper;
    private final RagTraceNodeMapper ragTraceNodeMapper;

    public RagTraceRecordServiceImpl(RagTraceRunMapper ragTraceRunMapper,
                                     RagTraceNodeMapper ragTraceNodeMapper) {
        this.ragTraceRunMapper = ragTraceRunMapper;
        this.ragTraceNodeMapper = ragTraceNodeMapper;
    }

    @Override
    public void startRun(RagTraceRunEntity run) {
        ragTraceRunMapper.insert(run);
    }

    @Override
    public void finishRun(String traceId,
                          String status,
                          String errorMessage,
                          LocalDateTime endedAt,
                          long durationMs,
                          String extraData) {
        RagTraceRunEntity update = new RagTraceRunEntity();
        update.status = status;
        update.errorMessage = errorMessage;
        update.endedAt = endedAt;
        update.durationMs = durationMs;
        update.extraData = extraData;
        update.updatedAt = LocalDateTime.now();
        ragTraceRunMapper.update(update, new UpdateWrapper<RagTraceRunEntity>()
                .eq("trace_id", traceId));
    }

    @Override
    public void startNode(RagTraceNodeEntity node) {
        ragTraceNodeMapper.insert(node);
    }

    @Override
    public void finishNode(String traceId,
                           String nodeId,
                           String status,
                           String errorMessage,
                           LocalDateTime endedAt,
                           long durationMs,
                           String extraData) {
        RagTraceNodeEntity update = new RagTraceNodeEntity();
        update.status = status;
        update.errorMessage = errorMessage;
        update.endedAt = endedAt;
        update.durationMs = durationMs;
        update.extraData = extraData;
        update.updatedAt = LocalDateTime.now();
        ragTraceNodeMapper.update(update, new UpdateWrapper<RagTraceNodeEntity>()
                .eq("trace_id", traceId)
                .eq("node_id", nodeId));
    }
}
