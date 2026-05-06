package com.personalblog.ragbackend.knowledge.service.rag;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.knowledge.dao.entity.RagTraceNodeEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.RagTraceRunEntity;
import com.personalblog.ragbackend.knowledge.dto.rag.RagTraceDetailView;
import com.personalblog.ragbackend.knowledge.dto.rag.RagTraceNodeView;
import com.personalblog.ragbackend.knowledge.dto.rag.RagTraceRunPageRequest;
import com.personalblog.ragbackend.knowledge.dto.rag.RagTraceRunView;
import com.personalblog.ragbackend.knowledge.mapper.RagTraceNodeMapper;
import com.personalblog.ragbackend.knowledge.mapper.RagTraceRunMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagTraceQueryService {
    private final RagTraceRunMapper ragTraceRunMapper;
    private final RagTraceNodeMapper ragTraceNodeMapper;

    public RagTraceQueryService(RagTraceRunMapper ragTraceRunMapper, RagTraceNodeMapper ragTraceNodeMapper) {
        this.ragTraceRunMapper = ragTraceRunMapper;
        this.ragTraceNodeMapper = ragTraceNodeMapper;
    }

    public IPage<RagTraceRunView> pageRuns(RagTraceRunPageRequest request) {
        IPage<RagTraceRunEntity> page = ragTraceRunMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(request.getCurrent(), request.getSize()),
                new QueryWrapper<RagTraceRunEntity>()
                        .eq(request.getTraceId() != null && !request.getTraceId().isBlank(), "trace_id", request.getTraceId())
                        .eq(request.getConversationId() != null && !request.getConversationId().isBlank(), "conversation_id", request.getConversationId())
                        .eq(request.getTaskId() != null && !request.getTaskId().isBlank(), "task_id", request.getTaskId())
                        .eq(request.getStatus() != null && !request.getStatus().isBlank(), "status", request.getStatus())
                        .orderByDesc("created_at")
        );
        return mapPage(page, page.getRecords().stream().map(this::toRunView).toList());
    }

    public RagTraceDetailView detail(String traceId) {
        RagTraceRunEntity run = ragTraceRunMapper.selectOne(new QueryWrapper<RagTraceRunEntity>()
                .eq("trace_id", traceId)
                .last("limit 1"));
        if (run == null) {
            throw new IllegalArgumentException("Trace 不存在");
        }
        return new RagTraceDetailView(toRunView(run), listNodes(traceId));
    }

    public List<RagTraceNodeView> listNodes(String traceId) {
        return ragTraceNodeMapper.selectList(new QueryWrapper<RagTraceNodeEntity>()
                        .eq("trace_id", traceId)
                        .orderByAsc("id"))
                .stream()
                .map(this::toNodeView)
                .toList();
    }

    private RagTraceRunView toRunView(RagTraceRunEntity entity) {
        return new RagTraceRunView(entity.id, entity.traceId, entity.traceName, entity.entryMethod,
                entity.conversationId, entity.taskId, entity.userId, entity.status, entity.errorMessage,
                entity.startedAt, entity.endedAt, entity.durationMs, entity.extraData, entity.createdAt, entity.updatedAt);
    }

    private RagTraceNodeView toNodeView(RagTraceNodeEntity entity) {
        return new RagTraceNodeView(entity.id, entity.traceId, entity.nodeId, entity.parentNodeId, entity.depth,
                entity.nodeType, entity.nodeName, entity.className, entity.methodName, entity.status,
                entity.errorMessage, entity.startedAt, entity.endedAt, entity.durationMs, entity.extraData,
                entity.createdAt, entity.updatedAt);
    }

    private <T> IPage<T> mapPage(IPage<?> source, List<T> records) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        page.setRecords(records);
        return page;
    }
}
