package com.personalblog.ragbackend.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalblog.ragbackend.rag.dao.entity.RagConversationEntity;
import com.personalblog.ragbackend.rag.dao.entity.RagConversationMessageEntity;
import com.personalblog.ragbackend.rag.dao.entity.RagConversationSummaryEntity;
import com.personalblog.ragbackend.rag.dao.mapper.RagConversationMapper;
import com.personalblog.ragbackend.rag.dao.mapper.RagConversationMessageMapper;
import com.personalblog.ragbackend.rag.dao.mapper.RagConversationSummaryMapper;
import com.personalblog.ragbackend.rag.service.ConversationGroupService;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Service
public class ConversationGroupServiceImpl implements ConversationGroupService {
    private final RagConversationMessageMapper messageMapper;
    private final RagConversationSummaryMapper summaryMapper;
    private final RagConversationMapper conversationMapper;

    public ConversationGroupServiceImpl(RagConversationMessageMapper messageMapper,
                                        RagConversationSummaryMapper summaryMapper,
                                        RagConversationMapper conversationMapper) {
        this.messageMapper = messageMapper;
        this.summaryMapper = summaryMapper;
        this.conversationMapper = conversationMapper;
    }

    @Override
    public List<RagConversationMessageEntity> listLatestUserOnlyMessages(String conversationId, String userId, int limit) {
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId) || limit <= 0) {
            return List.of();
        }
        return messageMapper.selectList(
                Wrappers.lambdaQuery(RagConversationMessageEntity.class)
                        .eq(RagConversationMessageEntity::getConversationId, conversationId)
                        .eq(RagConversationMessageEntity::getUserId, Long.valueOf(userId))
                        .eq(RagConversationMessageEntity::getRole, "user")
                        .eq(RagConversationMessageEntity::getDeleted, 0)
                        .orderByDesc(RagConversationMessageEntity::getCreatedAt)
                        .last("limit " + limit)
        );
    }

    @Override
    public List<RagConversationMessageEntity> listMessagesBetweenIds(String conversationId, String userId, String afterId, String beforeId) {
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId)) {
            return List.of();
        }
        var query = Wrappers.lambdaQuery(RagConversationMessageEntity.class)
                .eq(RagConversationMessageEntity::getConversationId, conversationId)
                .eq(RagConversationMessageEntity::getUserId, Long.valueOf(userId))
                .in(RagConversationMessageEntity::getRole, "user", "assistant")
                .eq(RagConversationMessageEntity::getDeleted, 0);
        if (afterId != null) {
            query.gt(RagConversationMessageEntity::getId, Long.valueOf(afterId));
        }
        if (beforeId != null) {
            query.lt(RagConversationMessageEntity::getId, Long.valueOf(beforeId));
        }
        return messageMapper.selectList(query.orderByAsc(RagConversationMessageEntity::getId));
    }

    @Override
    public String findMaxMessageIdAtOrBefore(String conversationId, String userId, Date at) {
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId) || at == null) {
            return null;
        }
        RagConversationMessageEntity record = messageMapper.selectOne(
                Wrappers.lambdaQuery(RagConversationMessageEntity.class)
                        .eq(RagConversationMessageEntity::getConversationId, conversationId)
                        .eq(RagConversationMessageEntity::getUserId, Long.valueOf(userId))
                        .eq(RagConversationMessageEntity::getDeleted, 0)
                        .le(RagConversationMessageEntity::getCreatedAt, at.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                        .orderByDesc(RagConversationMessageEntity::getId)
                        .last("limit 1")
        );
        return record == null ? null : String.valueOf(record.getId());
    }

    @Override
    public long countUserMessages(String conversationId, String userId) {
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId)) {
            return 0;
        }
        return messageMapper.selectCount(
                Wrappers.lambdaQuery(RagConversationMessageEntity.class)
                        .eq(RagConversationMessageEntity::getConversationId, conversationId)
                        .eq(RagConversationMessageEntity::getUserId, Long.valueOf(userId))
                        .eq(RagConversationMessageEntity::getRole, "user")
                        .eq(RagConversationMessageEntity::getDeleted, 0)
        );
    }

    @Override
    public RagConversationSummaryEntity findLatestSummary(String conversationId, String userId) {
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId)) {
            return null;
        }
        return summaryMapper.selectOne(
                Wrappers.lambdaQuery(RagConversationSummaryEntity.class)
                        .eq(RagConversationSummaryEntity::getConversationId, conversationId)
                        .eq(RagConversationSummaryEntity::getUserId, Long.valueOf(userId))
                        .eq(RagConversationSummaryEntity::getDeleted, 0)
                        .orderByDesc(RagConversationSummaryEntity::getId)
                        .last("limit 1")
        );
    }

    @Override
    public RagConversationEntity findConversation(String conversationId, String userId) {
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId)) {
            return null;
        }
        return conversationMapper.selectOne(
                Wrappers.lambdaQuery(RagConversationEntity.class)
                        .eq(RagConversationEntity::getConversationId, conversationId)
                        .eq(RagConversationEntity::getUserId, Long.valueOf(userId))
                        .eq(RagConversationEntity::getDeleted, 0)
        );
    }
}

