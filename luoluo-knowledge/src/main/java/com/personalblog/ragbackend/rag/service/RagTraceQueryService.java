package com.personalblog.ragbackend.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.knowledge.dao.entity.RagTraceNodeEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.RagTraceRunEntity;
import com.personalblog.ragbackend.knowledge.mapper.RagTraceNodeMapper;
import com.personalblog.ragbackend.knowledge.mapper.RagTraceRunMapper;
import com.personalblog.ragbackend.rag.controller.request.RagTraceRunPageRequest;
import com.personalblog.ragbackend.rag.controller.vo.RagTraceDetailVO;
import com.personalblog.ragbackend.rag.controller.vo.RagTraceNodeVO;
import com.personalblog.ragbackend.rag.controller.vo.RagTraceRunVO;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Service
public class RagTraceQueryService {
    private final RagTraceRunMapper ragTraceRunMapper;
    private final RagTraceNodeMapper ragTraceNodeMapper;

    public RagTraceQueryService(RagTraceRunMapper ragTraceRunMapper, RagTraceNodeMapper ragTraceNodeMapper) {
        this.ragTraceRunMapper = ragTraceRunMapper;
        this.ragTraceNodeMapper = ragTraceNodeMapper;
    }

    public IPage<RagTraceRunVO> pageRuns(RagTraceRunPageRequest request) {
        IPage<RagTraceRunEntity> page = ragTraceRunMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(request.getCurrent(), request.getSize()),
                new QueryWrapper<RagTraceRunEntity>()
                        .eq(request.getTraceId() != null && !request.getTraceId().isBlank(), "trace_id", request.getTraceId())
                        .eq(request.getConversationId() != null && !request.getConversationId().isBlank(), "conversation_id", request.getConversationId())
                        .eq(request.getTaskId() != null && !request.getTaskId().isBlank(), "task_id", request.getTaskId())
                        .eq(request.getStatus() != null && !request.getStatus().isBlank(), "status", request.getStatus())
                        .orderByDesc("create_time")
        );
        return mapPage(page, page.getRecords().stream().map(this::toRunView).toList());
    }

    public RagTraceDetailVO detail(String traceId) {
        RagTraceRunEntity run = ragTraceRunMapper.selectOne(new QueryWrapper<RagTraceRunEntity>()
                .eq("trace_id", traceId)
                .last("limit 1"));
        if (run == null) {
            throw new IllegalArgumentException("Trace 不存在");
        }
        return new RagTraceDetailVO(toRunView(run), listNodes(traceId));
    }

    public List<RagTraceNodeVO> listNodes(String traceId) {
        return ragTraceNodeMapper.selectList(new QueryWrapper<RagTraceNodeEntity>()
                        .eq("trace_id", traceId)
                        .orderByAsc("id"))
                .stream()
                .map(this::toNodeView)
                .toList();
    }

    private RagTraceRunVO toRunView(RagTraceRunEntity entity) {
        return RagTraceRunVO.builder()
                .traceId(entity.traceId)
                .traceName(entity.traceName)
                .entryMethod(entity.entryMethod)
                .conversationId(entity.conversationId)
                .taskId(entity.taskId)
                .userId(entity.userId == null ? null : String.valueOf(entity.userId))
                .username(null)
                .status(entity.status)
                .errorMessage(entity.errorMessage)
                .durationMs(entity.durationMs)
                .startTime(toDate(entity.startedAt))
                .endTime(toDate(entity.endedAt))
                .build();
    }

    private RagTraceNodeVO toNodeView(RagTraceNodeEntity entity) {
        return RagTraceNodeVO.builder()
                .traceId(entity.traceId)
                .nodeId(entity.nodeId)
                .parentNodeId(entity.parentNodeId)
                .depth(entity.depth)
                .nodeType(entity.nodeType)
                .nodeName(entity.nodeName)
                .className(entity.className)
                .methodName(entity.methodName)
                .status(entity.status)
                .errorMessage(entity.errorMessage)
                .durationMs(entity.durationMs)
                .startTime(toDate(entity.startedAt))
                .endTime(toDate(entity.endedAt))
                .build();
    }

    private <T> IPage<T> mapPage(IPage<?> source, List<T> records) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        page.setRecords(records);
        return page;
    }

    private Date toDate(java.time.LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
    }
}
