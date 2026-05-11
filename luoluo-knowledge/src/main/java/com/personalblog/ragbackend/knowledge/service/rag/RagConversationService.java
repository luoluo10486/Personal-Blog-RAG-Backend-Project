package com.personalblog.ragbackend.knowledge.service.rag;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.infra.ai.convention.ChatMessage;
import com.personalblog.ragbackend.knowledge.dao.entity.RagConversationEntity;
import com.personalblog.ragbackend.knowledge.mapper.RagConversationMapper;
import com.personalblog.ragbackend.knowledge.service.rag.memory.ConversationMemoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RagConversationService {
    private final ConversationMemoryService conversationMemoryService;
    private final RagConversationMapper ragConversationMapper;

    public RagConversationService(ConversationMemoryService conversationMemoryService,
                                  RagConversationMapper ragConversationMapper) {
        this.conversationMemoryService = conversationMemoryService;
        this.ragConversationMapper = ragConversationMapper;
    }

    public List<ChatMessage> loadMemory(String conversationId) {
        Long userId = resolveCurrentUserId();
        if (StrUtil.isBlank(conversationId) || userId == null) {
            return List.of();
        }
        return conversationMemoryService.load(conversationId.trim(), userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ConversationPersistResult persistExchange(String conversationId,
                                                     String question,
                                                     String answer,
                                                     String baseCode,
                                                     int citationCount) {
        return persistExchange(conversationId, question, answer, baseCode, citationCount, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public ConversationPersistResult persistExchange(String conversationId,
                                                     String question,
                                                     String answer,
                                                     String baseCode,
                                                     int citationCount,
                                                     String thinkingContent,
                                                     Integer thinkingDuration) {
        Long userId = resolveCurrentUserId();
        if (StrUtil.isBlank(conversationId) || userId == null) {
            return new ConversationPersistResult(null, null);
        }

        String normalizedConversationId = conversationId.trim();
        if (StrUtil.isNotBlank(question)) {
            conversationMemoryService.append(normalizedConversationId, userId, ChatMessage.user(question.trim()));
        }
        String assistantMessageId = conversationMemoryService.append(
                normalizedConversationId,
                userId,
                ChatMessage.assistant(
                        StrUtil.blankToDefault(answer, "").trim(),
                        StrUtil.isBlank(thinkingContent) ? null : thinkingContent.trim(),
                        thinkingDuration
                )
        );
        return new ConversationPersistResult(assistantMessageId, findConversationTitle(normalizedConversationId, userId));
    }

    @Transactional(rollbackFor = Exception.class)
    public ConversationPersistResult persistAssistantAnswer(String conversationId,
                                                           String answer,
                                                           String baseCode,
                                                           int citationCount,
                                                           String thinkingContent,
                                                           Integer thinkingDuration) {
        Long userId = resolveCurrentUserId();
        if (StrUtil.isBlank(conversationId) || userId == null) {
            return new ConversationPersistResult(null, null);
        }
        String normalizedConversationId = conversationId.trim();
        String assistantMessageId = conversationMemoryService.append(
                normalizedConversationId,
                userId,
                ChatMessage.assistant(
                        StrUtil.blankToDefault(answer, "").trim(),
                        StrUtil.isBlank(thinkingContent) ? null : thinkingContent.trim(),
                        thinkingDuration
                )
        );
        return new ConversationPersistResult(assistantMessageId, findConversationTitle(normalizedConversationId, userId));
    }

    public Long resolveCurrentUserIdValue() {
        return resolveCurrentUserId();
    }

    public String findConversationTitle(String conversationId) {
        Long userId = resolveCurrentUserId();
        if (StrUtil.isBlank(conversationId) || userId == null) {
            return null;
        }
        return findConversationTitle(conversationId.trim(), userId);
    }

    private Long resolveCurrentUserId() {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            return null;
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String findConversationTitle(String conversationId, Long userId) {
        RagConversationEntity conversation = ragConversationMapper.selectOne(
                Wrappers.lambdaQuery(RagConversationEntity.class)
                        .eq(RagConversationEntity::getConversationId, conversationId)
                        .eq(RagConversationEntity::getUserId, userId)
                        .eq(RagConversationEntity::getDeleted, 0)
        );
        return conversation == null ? null : conversation.getTitle();
    }
}
