package com.personalblog.ragbackend.rag.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.framework.exception.ClientException;
import com.personalblog.ragbackend.rag.dao.entity.RagConversationMessageEntity;
import com.personalblog.ragbackend.rag.dao.entity.RagMessageFeedbackEntity;
import com.personalblog.ragbackend.rag.dao.mapper.RagConversationMessageMapper;
import com.personalblog.ragbackend.rag.dao.mapper.RagMessageFeedbackMapper;
import com.personalblog.ragbackend.knowledge.mq.MessageWrapper;
import com.personalblog.ragbackend.rag.controller.request.MessageFeedbackRequest;
import com.personalblog.ragbackend.rag.mq.event.MessageFeedbackEvent;
import com.personalblog.ragbackend.rag.service.MessageFeedbackService;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MessageFeedbackServiceImpl implements MessageFeedbackService {
    private final RagMessageFeedbackMapper feedbackMapper;
    private final RagConversationMessageMapper conversationMessageMapper;
    private final RocketMQTemplate rocketMQTemplate;

    @Value("message-feedback_topic${unique-name:}")
    private String feedbackTopic;

    public MessageFeedbackServiceImpl(RagMessageFeedbackMapper feedbackMapper,
                                      RagConversationMessageMapper conversationMessageMapper,
                                      RocketMQTemplate rocketMQTemplate) {
        this.feedbackMapper = feedbackMapper;
        this.conversationMessageMapper = conversationMessageMapper;
        this.rocketMQTemplate = rocketMQTemplate;
    }

    @Override
    public void submitFeedbackAsync(String messageId, MessageFeedbackRequest request) {
        String userId = requireCurrentUserId();
        validateRequest(messageId, request);

        MessageFeedbackEvent event = new MessageFeedbackEvent();
        event.setMessageId(messageId);
        event.setUserId(userId);
        event.setVote(request.getVote());
        event.setReason(request.getReason());
        event.setComment(request.getComment());
        event.setSubmitTime(System.currentTimeMillis());

        MessageWrapper<MessageFeedbackEvent> wrapper = new MessageWrapper<>();
        wrapper.setKeys(userId + ":" + messageId);
        wrapper.setBody(event);
        rocketMQTemplate.syncSend(
                feedbackTopic,
                MessageBuilder.withPayload(wrapper)
                        .setHeader(MessageConst.PROPERTY_KEYS, userId + ":" + messageId)
                        .build()
        );
    }

    @Override
    public void submitFeedback(String messageId, MessageFeedbackRequest request) {
        String userId = requireCurrentUserId();
        validateRequest(messageId, request);
        RagConversationMessageEntity message = loadAssistantMessage(messageId, userId);
        upsertFeedback(messageId, userId, message.getConversationId(), request.getVote(),
                request.getReason(), request.getComment(), System.currentTimeMillis());
    }

    @Override
    public void submitFeedbackByEvent(MessageFeedbackEvent event) {
        Assert.notNull(event, () -> new ClientException("feedback event is invalid"));
        Assert.notBlank(event.getMessageId(), () -> new ClientException("messageId cannot be blank"));
        Assert.notBlank(event.getUserId(), () -> new ClientException("userId cannot be blank"));
        Assert.notNull(event.getVote(), () -> new ClientException("vote cannot be null"));
        RagConversationMessageEntity message = loadAssistantMessage(event.getMessageId(), event.getUserId());
        upsertFeedback(event.getMessageId(), event.getUserId(), message.getConversationId(),
                event.getVote(), event.getReason(), event.getComment(), event.getSubmitTime());
    }

    @Override
    public Map<String, Integer> getUserVotes(String userId, List<String> messageIds) {
        if (StrUtil.isBlank(userId) || CollUtil.isEmpty(messageIds)) {
            return Collections.emptyMap();
        }
        return feedbackMapper.selectList(Wrappers.<RagMessageFeedbackEntity>lambdaQuery()
                        .eq(RagMessageFeedbackEntity::getUserId, userId)
                        .eq(RagMessageFeedbackEntity::getDeleted, 0)
                        .in(RagMessageFeedbackEntity::getMessageId, messageIds))
                .stream()
                .collect(Collectors.toMap(
                        RagMessageFeedbackEntity::getMessageId,
                        RagMessageFeedbackEntity::getVote,
                        (first, second) -> first
                ));
    }

    private void upsertFeedback(String messageId, String userId, String conversationId,
                                Integer vote, String reason, String comment, long submitTime) {
        validateFeedback(vote);
        LocalDateTime submitAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(submitTime), ZoneId.systemDefault());

        RagMessageFeedbackEntity existing = feedbackMapper.selectOne(Wrappers.<RagMessageFeedbackEntity>lambdaQuery()
                .eq(RagMessageFeedbackEntity::getMessageId, messageId)
                .eq(RagMessageFeedbackEntity::getUserId, userId)
                .last("limit 1"));
        if (existing == null) {
            RagMessageFeedbackEntity feedback = new RagMessageFeedbackEntity();
            feedback.setMessageId(messageId);
            feedback.setConversationId(conversationId);
            feedback.setUserId(userId);
            feedback.setVote(vote);
            feedback.setReason(reason);
            feedback.setComment(comment);
            feedback.setDeleted(0);
            feedback.setCreatedAt(submitAt);
            feedback.setUpdatedAt(submitAt);
            feedbackMapper.insert(feedback);
            return;
        }

        if (existing.getUpdatedAt() != null && existing.getUpdatedAt().isAfter(submitAt)) {
            return;
        }
        existing.setVote(vote);
        existing.setReason(reason);
        existing.setComment(comment);
        existing.setConversationId(conversationId);
        existing.setUpdatedAt(submitAt);
        feedbackMapper.updateById(existing);
    }

    private RagConversationMessageEntity loadAssistantMessage(String messageId, String userId) {
        RagConversationMessageEntity message = conversationMessageMapper.selectOne(Wrappers.<RagConversationMessageEntity>lambdaQuery()
                .eq(RagConversationMessageEntity::getId, messageId)
                .eq(RagConversationMessageEntity::getUserId, userId)
                .eq(RagConversationMessageEntity::getDeleted, 0)
                .eq(RagConversationMessageEntity::getRole, "assistant")
                .last("limit 1"));
        if (message == null) {
            throw new ClientException("assistant message not found");
        }
        return message;
    }

    private String requireCurrentUserId() {
        String userId = UserContext.getUserId();
        if (!StrUtil.isNotBlank(userId)) {
            throw new ClientException("current user not found");
        }
        return userId;
    }

    private void validateRequest(String messageId, MessageFeedbackRequest request) {
        Assert.notBlank(messageId, () -> new ClientException("messageId cannot be blank"));
        Assert.notNull(request, () -> new ClientException("request cannot be null"));
        validateFeedback(request.getVote());
    }

    private void validateFeedback(Integer vote) {
        if (vote == null) {
            throw new ClientException("vote cannot be null");
        }
        if (vote != 1 && vote != -1) {
            throw new ClientException("vote must be 1 or -1");
        }
    }
}

