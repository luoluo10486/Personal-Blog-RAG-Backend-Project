package com.personalblog.ragbackend.knowledge.service.rag;

import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.infra.ai.convention.ChatMessage;
import com.personalblog.ragbackend.knowledge.service.rag.memory.ConversationMemoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RagConversationService {
    private final ConversationMemoryService conversationMemoryService;

    public RagConversationService(ConversationMemoryService conversationMemoryService) {
        this.conversationMemoryService = conversationMemoryService;
    }

    public List<ChatMessage> loadMemory(String conversationId) {
        Long userId = resolveCurrentUserId();
        if (StrUtil.isBlank(conversationId) || userId == null) {
            return List.of();
        }
        return conversationMemoryService.load(conversationId.trim(), userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistExchange(String conversationId,
                                String question,
                                String answer,
                                String baseCode,
                                int citationCount) {
        Long userId = resolveCurrentUserId();
        if (StrUtil.isBlank(conversationId) || userId == null) {
            return;
        }

        String normalizedConversationId = conversationId.trim();
        if (StrUtil.isNotBlank(question)) {
            conversationMemoryService.append(normalizedConversationId, userId, ChatMessage.user(question.trim()));
        }
        conversationMemoryService.append(normalizedConversationId, userId, ChatMessage.assistant(StrUtil.blankToDefault(answer, "").trim()));
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
}
