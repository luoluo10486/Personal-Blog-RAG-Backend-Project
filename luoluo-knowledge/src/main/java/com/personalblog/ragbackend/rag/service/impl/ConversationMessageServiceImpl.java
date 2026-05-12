package com.personalblog.ragbackend.rag.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalblog.ragbackend.knowledge.dao.entity.RagConversationEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.RagConversationMessageEntity;
import com.personalblog.ragbackend.knowledge.mapper.RagConversationMessageMapper;
import com.personalblog.ragbackend.knowledge.mapper.RagConversationMapper;
import com.personalblog.ragbackend.rag.controller.vo.ConversationMessageVO;
import com.personalblog.ragbackend.rag.enums.ConversationMessageOrder;
import com.personalblog.ragbackend.rag.service.MessageFeedbackService;
import com.personalblog.ragbackend.rag.service.ConversationMessageService;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class ConversationMessageServiceImpl implements ConversationMessageService {
    private final RagConversationMessageMapper messageMapper;
    private final RagConversationMapper conversationMapper;
    private final MessageFeedbackService feedbackService;

    public ConversationMessageServiceImpl(RagConversationMessageMapper messageMapper,
                                          RagConversationMapper conversationMapper,
                                          MessageFeedbackService feedbackService) {
        this.messageMapper = messageMapper;
        this.conversationMapper = conversationMapper;
        this.feedbackService = feedbackService;
    }

    @Override
    public List<ConversationMessageVO> listMessages(String conversationId, String userId, Integer limit, ConversationMessageOrder order) {
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId)) {
            return List.of();
        }
        RagConversationEntity conversation = conversationMapper.selectOne(
                Wrappers.lambdaQuery(RagConversationEntity.class)
                        .eq(RagConversationEntity::getConversationId, conversationId)
                        .eq(RagConversationEntity::getUserId, Long.valueOf(userId))
                        .eq(RagConversationEntity::getDeleted, 0)
                        .last("limit 1")
        );
        if (conversation == null) {
            return List.of();
        }

        boolean asc = order == null || order == ConversationMessageOrder.ASC;
        List<RagConversationMessageEntity> records = messageMapper.selectList(
                Wrappers.lambdaQuery(RagConversationMessageEntity.class)
                        .eq(RagConversationMessageEntity::getConversationId, conversationId)
                        .eq(RagConversationMessageEntity::getUserId, Long.valueOf(userId))
                        .eq(RagConversationMessageEntity::getDeleted, 0)
                        .orderBy(true, asc, RagConversationMessageEntity::getCreatedAt)
                        .last(limit != null, "limit " + limit)
        );
        if (CollUtil.isEmpty(records)) {
            return List.of();
        }
        if (!asc) {
            Collections.reverse(records);
        }

        List<Long> assistantMessageIds = records.stream()
                .filter(record -> "assistant".equalsIgnoreCase(record.getRole()))
                .map(RagConversationMessageEntity::getId)
                .toList();
        Map<Long, Integer> votesByMessageId = feedbackService.getUserVotes(Long.valueOf(userId), assistantMessageIds);

        return records.stream()
                .map(record -> new ConversationMessageVO(
                        String.valueOf(record.getId()),
                        record.getConversationId(),
                        record.getRole(),
                        record.getContent(),
                        record.getThinkingContent(),
                        record.getThinkingDuration(),
                        votesByMessageId.get(record.getId()),
                        toDate(record.getCreatedAt())))
                .toList();
    }

    private Date toDate(java.time.LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
    }
}
