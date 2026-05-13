package com.personalblog.ragbackend.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalblog.ragbackend.knowledge.dao.entity.RagTraceNodeEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.RagTraceRunEntity;
import com.personalblog.ragbackend.knowledge.mapper.RagTraceNodeMapper;
import com.personalblog.ragbackend.knowledge.mapper.RagTraceRunMapper;
import com.personalblog.ragbackend.member.domain.MemberUser;
import com.personalblog.ragbackend.member.mapper.MemberUserMapper;
import com.personalblog.ragbackend.rag.controller.request.RagTraceRunPageRequest;
import com.personalblog.ragbackend.rag.controller.vo.RagTraceDetailVO;
import com.personalblog.ragbackend.rag.controller.vo.RagTraceNodeVO;
import com.personalblog.ragbackend.rag.controller.vo.RagTraceRunVO;
import com.personalblog.ragbackend.rag.service.RagTraceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RagTraceQueryServiceImpl implements RagTraceQueryService {

    private final RagTraceRunMapper runMapper;
    private final RagTraceNodeMapper nodeMapper;
    private final MemberUserMapper memberUserMapper;

    @Override
    public IPage<RagTraceRunVO> pageRuns(RagTraceRunPageRequest request) {
        QueryWrapper<RagTraceRunEntity> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("create_time");

        if (StrUtil.isNotBlank(request.getTraceId())) {
            wrapper.eq("trace_id", request.getTraceId());
        }
        if (StrUtil.isNotBlank(request.getConversationId())) {
            wrapper.eq("conversation_id", request.getConversationId());
        }
        if (StrUtil.isNotBlank(request.getTaskId())) {
            wrapper.eq("task_id", request.getTaskId());
        }
        if (StrUtil.isNotBlank(request.getStatus())) {
            wrapper.eq("status", request.getStatus());
        }

        IPage<RagTraceRunEntity> pageResult = runMapper.selectPage(request, wrapper);
        Map<String, String> usernameMap = loadUsernameMap(pageResult.getRecords());
        return pageResult.convert(run -> toRunVO(run, usernameMap));
    }

    @Override
    public RagTraceDetailVO detail(String traceId) {
        RagTraceRunEntity run = runMapper.selectOne(new QueryWrapper<RagTraceRunEntity>()
                .eq("trace_id", traceId)
                .last("limit 1"));
        if (run == null) {
            return null;
        }
        Map<String, String> usernameMap = loadUsernameMap(List.of(run));
        return RagTraceDetailVO.builder()
                .run(toRunVO(run, usernameMap))
                .nodes(listNodes(traceId))
                .build();
    }

    @Override
    public List<RagTraceNodeVO> listNodes(String traceId) {
        List<RagTraceNodeEntity> nodes = nodeMapper.selectList(new QueryWrapper<RagTraceNodeEntity>()
                .eq("trace_id", traceId)
                .orderByAsc("create_time")
                .orderByAsc("id"));
        return nodes.stream().map(this::toNodeVO).toList();
    }

    private RagTraceRunVO toRunVO(RagTraceRunEntity run, Map<String, String> usernameMap) {
        return RagTraceRunVO.builder()
                .traceId(run.traceId)
                .traceName(run.traceName)
                .entryMethod(run.entryMethod)
                .conversationId(run.conversationId)
                .taskId(run.taskId)
                .userId(run.userId == null ? null : String.valueOf(run.userId))
                .username(resolveUsername(run.userId, usernameMap))
                .status(run.status)
                .errorMessage(run.errorMessage)
                .durationMs(run.durationMs)
                .startTime(toDate(run.createdAt))
                .endTime(toDate(run.updatedAt))
                .build();
    }

    private RagTraceNodeVO toNodeVO(RagTraceNodeEntity node) {
        return RagTraceNodeVO.builder()
                .traceId(node.traceId)
                .nodeId(node.nodeId)
                .parentNodeId(node.parentNodeId)
                .depth(node.depth)
                .nodeType(node.nodeType)
                .nodeName(node.nodeName)
                .className(node.className)
                .methodName(node.methodName)
                .status(node.status)
                .errorMessage(node.errorMessage)
                .durationMs(node.durationMs)
                .startTime(toDate(node.createdAt))
                .endTime(toDate(node.updatedAt))
                .build();
    }

    private Map<String, String> loadUsernameMap(List<RagTraceRunEntity> runs) {
        if (runs == null || runs.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> userIds = runs.stream()
                .map(item -> item.userId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<MemberUser> users = memberUserMapper.selectList(Wrappers.lambdaQuery(MemberUser.class)
                .in(MemberUser::getUserId, userIds)
                .select(MemberUser::getUserId, MemberUser::getUsername));
        if (users == null || users.isEmpty()) {
            return Collections.emptyMap();
        }

        return users.stream().collect(Collectors.toMap(
                user -> String.valueOf(user.getUserId()),
                MemberUser::getUsername,
                (left, right) -> left
        ));
    }

    private String resolveUsername(Long userId, Map<String, String> usernameMap) {
        if (userId == null || usernameMap == null || usernameMap.isEmpty()) {
            return null;
        }
        return usernameMap.get(String.valueOf(userId));
    }

    private java.util.Date toDate(java.time.LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return java.util.Date.from(time.atZone(java.time.ZoneId.systemDefault()).toInstant());
    }
}
