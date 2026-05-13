package com.personalblog.ragbackend.rag.core.memory;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.rag.config.MemoryProperties;
import com.personalblog.ragbackend.knowledge.dao.entity.RagConversationEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.RagConversationMessageEntity;
import com.personalblog.ragbackend.knowledge.mapper.RagConversationMapper;
import com.personalblog.ragbackend.knowledge.mapper.RagConversationMessageMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class JdbcConversationMemoryStore implements ConversationMemoryStore {
    private final RagConversationMapper conversationMapper;
    private final RagConversationMessageMapper conversationMessageMapper;
    private final MemoryProperties memoryProperties;

    public JdbcConversationMemoryStore(RagConversationMapper conversationMapper,
                                       RagConversationMessageMapper conversationMessageMapper,
                                       MemoryProperties memoryProperties) {
        this.conversationMapper = conversationMapper;
        this.conversationMessageMapper = conversationMessageMapper;
        this.memoryProperties = memoryProperties;
    }

    @Override
    public List<ChatMessage> loadHistory(String conversationId, Long userId) {
        if (StrUtil.isBlank(conversationId) || userId == null) {
            return List.of();
        }
        RagConversationEntity conversation = conversationMapper.selectOne(
                Wrappers.lambdaQuery(RagConversationEntity.class)
                        .eq(RagConversationEntity::getConversationId, conversationId)
                        .eq(RagConversationEntity::getUserId, userId)
                        .eq(RagConversationEntity::getDeleted, 0)
        );
        if (conversation == null) {
            return List.of();
        }

        int maxMessages = Math.max(1, memoryProperties.getHistoryKeepTurns()) * 2;
        List<RagConversationMessageEntity> records = conversationMessageMapper.selectList(
                Wrappers.lambdaQuery(RagConversationMessageEntity.class)
                        .eq(RagConversationMessageEntity::getConversationId, conversationId)
                        .eq(RagConversationMessageEntity::getUserId, userId)
                        .eq(RagConversationMessageEntity::getDeleted, 0)
                        .orderByDesc(RagConversationMessageEntity::getCreatedAt)
                        .last("limit " + maxMessages)
        );
        if (CollUtil.isEmpty(records)) {
            return List.of();
        }

        Collections.reverse(records);
        List<ChatMessage> result = records.stream()
                .map(this::toChatMessage)
                .filter(Objects::nonNull)
                .filter(this::isHistoryMessage)
                .collect(Collectors.toList());
        return normalizeHistory(result);
    }

    @Override
    public String append(String conversationId, Long userId, ChatMessage message) {
        if (StrUtil.isBlank(conversationId) || userId == null || message == null) {
            return null;
        }

        RagConversationMessageEntity record = new RagConversationMessageEntity();
        record.setConversationId(conversationId);
        record.setUserId(userId);
        record.setRole(message.getRole().name().toLowerCase());
        record.setContent(message.getContent());
        record.setThinkingContent(message.getThinkingContent());
        record.setThinkingDuration(message.getThinkingDuration());
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        record.setDeleted(0);
        conversationMessageMapper.insert(record);

        if (message.getRole() == ChatMessage.Role.USER) {
            upsertConversation(conversationId, userId, message.getContent());
        }
        return record.getId() == null ? null : String.valueOf(record.getId());
    }

    private void upsertConversation(String conversationId, Long userId, String question) {
        RagConversationEntity conversation = conversationMapper.selectOne(
                Wrappers.lambdaQuery(RagConversationEntity.class)
                        .eq(RagConversationEntity::getConversationId, conversationId)
                        .eq(RagConversationEntity::getUserId, userId)
                        .eq(RagConversationEntity::getDeleted, 0)
        );
        LocalDateTime now = LocalDateTime.now();
        if (conversation == null) {
            RagConversationEntity record = new RagConversationEntity();
            record.setConversationId(conversationId);
            record.setUserId(userId);
            record.setTitle(buildTitle(question));
            record.setLastTime(now);
            record.setDeleted(0);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            conversationMapper.insert(record);
            return;
        }

        conversation.setLastTime(now);
        if (StrUtil.isBlank(conversation.getTitle())) {
            conversation.setTitle(buildTitle(question));
        }
        conversation.setUpdatedAt(now);
        conversationMapper.updateById(conversation);
    }

    private ChatMessage toChatMessage(RagConversationMessageEntity record) {
        if (record == null || StrUtil.isBlank(record.getContent()) || StrUtil.isBlank(record.getRole())) {
            return null;
        }
        return switch (record.getRole().toLowerCase()) {
            case "user" -> ChatMessage.user(record.getContent());
            case "assistant" -> ChatMessage.assistant(
                    record.getContent(),
                    record.getThinkingContent(),
                    record.getThinkingDuration()
            );
            default -> null;
        };
    }

    private List<ChatMessage> normalizeHistory(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        int start = 0;
        while (start < messages.size() && messages.get(start).getRole() == ChatMessage.Role.ASSISTANT) {
            start++;
        }
        if (start >= messages.size()) {
            return List.of();
        }
        return messages.subList(start, messages.size());
    }

    private boolean isHistoryMessage(ChatMessage message) {
        return message != null
                && (message.getRole() == ChatMessage.Role.USER || message.getRole() == ChatMessage.Role.ASSISTANT)
                && StrUtil.isNotBlank(message.getContent());
    }

    private String buildTitle(String question) {
        String text = StrUtil.blankToDefault(question, "New conversation").trim();
        int maxLength = Math.max(1, memoryProperties.getTitleMaxLength());
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
