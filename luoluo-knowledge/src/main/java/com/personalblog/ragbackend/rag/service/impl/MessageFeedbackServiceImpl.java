package com.personalblog.ragbackend.rag.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.knowledge.dao.entity.RagConversationMessageEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.RagMessageFeedbackEntity;
import com.personalblog.ragbackend.knowledge.mapper.RagConversationMessageMapper;
import com.personalblog.ragbackend.knowledge.mapper.RagMessageFeedbackMapper;
import com.personalblog.ragbackend.knowledge.mq.MessageWrapper;
import com.personalblog.ragbackend.rag.controller.request.MessageFeedbackRequest;
import com.personalblog.ragbackend.rag.mq.event.MessageFeedbackEvent;
import com.personalblog.ragbackend.rag.service.MessageFeedbackService;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
    public void submitFeedbackAsync(Long messageId, MessageFeedbackRequest request) {
        Long userId = requireCurrentUserId();
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
    public void submitFeedback(Long messageId, MessageFeedbackRequest request) {
        Long userId = requireCurrentUserId();
        validateRequest(messageId, request);
        RagConversationMessageEntity message = loadAssistantMessage(messageId, userId);
        upsertFeedback(messageId, userId, message.getConversationId(), request.getVote(),
                request.getReason(), request.getComment(), System.currentTimeMillis());
    }

    @Override
    public void submitFeedbackByEvent(MessageFeedbackEvent event) {
        if (event == null || event.getMessageId() == null || event.getUserId() == null) {
            throw new IllegalArgumentException("反馈事件参数不完整");
        }
        RagConversationMessageEntity message = loadAssistantMessage(event.getMessageId(), event.getUserId());
        upsertFeedback(event.getMessageId(), event.getUserId(), message.getConversationId(),
                event.getVote(), event.getReason(), event.getComment(), event.getSubmitTime());
    }

    @Override
    public Map<Long, Integer> getUserVotes(Long userId, List<Long> messageIds) {
        if (userId == null || messageIds == null || messageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return feedbackMapper.selectList(Wrappers.<RagMessageFeedbackEntity>lambdaQuery()
                        .eq(RagMessageFeedbackEntity::getUserId, userId)
                        .in(RagMessageFeedbackEntity::getMessageId, messageIds))
                .stream()
                .collect(Collectors.toMap(
                        RagMessageFeedbackEntity::getMessageId,
                        RagMessageFeedbackEntity::getVote,
                        (first, second) -> first
                ));
    }

    private void upsertFeedback(Long messageId, Long userId, String conversationId,
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

    private RagConversationMessageEntity loadAssistantMessage(Long messageId, Long userId) {
        RagConversationMessageEntity message = conversationMessageMapper.selectOne(Wrappers.<RagConversationMessageEntity>lambdaQuery()
                .eq(RagConversationMessageEntity::getId, messageId)
                .eq(RagConversationMessageEntity::getUserId, userId)
                .eq(RagConversationMessageEntity::getRole, "assistant")
                .last("limit 1"));
        if (message == null) {
            throw new IllegalArgumentException("只允许对当前用户的助手消息反馈");
        }
        return message;
    }

    private Long requireCurrentUserId() {
        String userId = UserContext.getUserId();
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("当前用户未登录");
        }
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("用户ID格式错误");
        }
    }

    private void validateRequest(Long messageId, MessageFeedbackRequest request) {
        if (messageId == null) {
            throw new IllegalArgumentException("messageId 不能为空");
        }
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        validateFeedback(request.getVote());
    }

    private void validateFeedback(Integer vote) {
        if (vote == null) {
            throw new IllegalArgumentException("vote 不能为空");
        }
        if (vote != 1 && vote != -1) {
            throw new IllegalArgumentException("vote 只能是 1 或 -1");
        }
    }
}
