package com.personalblog.ragbackend.rag.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalblog.ragbackend.rag.dao.entity.RagConversationEntity;
import com.personalblog.ragbackend.rag.dao.entity.RagConversationMessageEntity;
import com.personalblog.ragbackend.rag.dao.entity.RagConversationSummaryEntity;
import com.personalblog.ragbackend.rag.dao.mapper.RagConversationMessageMapper;
import com.personalblog.ragbackend.rag.dao.mapper.RagConversationMapper;
import com.personalblog.ragbackend.rag.dao.mapper.RagConversationSummaryMapper;
import com.personalblog.ragbackend.rag.controller.vo.ConversationMessageVO;
import com.personalblog.ragbackend.rag.enums.ConversationMessageOrder;
import com.personalblog.ragbackend.rag.service.bo.ConversationMessageBO;
import com.personalblog.ragbackend.rag.service.bo.ConversationSummaryBO;
import com.personalblog.ragbackend.rag.service.MessageFeedbackService;
import com.personalblog.ragbackend.rag.service.ConversationMessageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ConversationMessageServiceImpl implements ConversationMessageService {
    private final RagConversationMessageMapper messageMapper;
    private final RagConversationMapper conversationMapper;
    private final RagConversationSummaryMapper summaryMapper;
    private final MessageFeedbackService feedbackService;

    public ConversationMessageServiceImpl(RagConversationMessageMapper messageMapper,
                                          RagConversationMapper conversationMapper,
                                          RagConversationSummaryMapper summaryMapper,
                                          MessageFeedbackService feedbackService) {
        this.messageMapper = messageMapper;
        this.conversationMapper = conversationMapper;
        this.summaryMapper = summaryMapper;
        this.feedbackService = feedbackService;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public String addMessage(ConversationMessageBO conversationMessage) {
        if (conversationMessage == null || StrUtil.isBlank(conversationMessage.getConversationId()) || StrUtil.isBlank(conversationMessage.getUserId())) {
            return null;
        }
        RagConversationMessageEntity entity = BeanUtil.toBean(conversationMessage, RagConversationMessageEntity.class);
        entity.setUserId(Long.valueOf(conversationMessage.getUserId()));
        entity.setDeleted(0);
        entity.setCreatedAt(java.time.LocalDateTime.now());
        entity.setUpdatedAt(java.time.LocalDateTime.now());
        messageMapper.insert(entity);
        return entity.getId() == null ? null : String.valueOf(entity.getId());
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

        List<String> assistantMessageIds = records.stream()
                .filter(record -> "assistant".equalsIgnoreCase(record.getRole()))
                .map(RagConversationMessageEntity::getId)
                .map(String::valueOf)
                .toList();
        Map<String, Integer> votesByMessageId = feedbackService.getUserVotes(userId, assistantMessageIds);

        return records.stream()
                .map(record -> ConversationMessageVO.builder()
                        .id(String.valueOf(record.getId()))
                        .conversationId(record.getConversationId())
                        .role(record.getRole())
                        .content(record.getContent())
                        .thinkingContent(record.getThinkingContent())
                        .thinkingDuration(record.getThinkingDuration())
                        .vote(votesByMessageId.get(String.valueOf(record.getId())))
                        .createTime(toDate(record.getCreatedAt()))
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void addMessageSummary(ConversationSummaryBO conversationSummary) {
        if (conversationSummary == null || StrUtil.isBlank(conversationSummary.getConversationId()) || StrUtil.isBlank(conversationSummary.getUserId())) {
            return;
        }
        RagConversationSummaryEntity entity = BeanUtil.toBean(conversationSummary, RagConversationSummaryEntity.class);
        entity.setUserId(Long.valueOf(conversationSummary.getUserId()));
        entity.setLastMessageId(conversationSummary.getLastMessageId() == null ? null : Long.valueOf(conversationSummary.getLastMessageId()));
        entity.setDeleted(0);
        entity.setCreatedAt(java.time.LocalDateTime.now());
        entity.setUpdatedAt(java.time.LocalDateTime.now());
        summaryMapper.insert(entity);
    }

    private Date toDate(java.time.LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
    }
}

