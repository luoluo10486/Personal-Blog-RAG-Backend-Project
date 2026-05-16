package com.personalblog.ragbackend.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.framework.exception.ClientException;
import com.personalblog.ragbackend.infra.chat.LLMService;
import com.personalblog.ragbackend.infra.convention.ChatMessage;
import com.personalblog.ragbackend.infra.convention.ChatRequest;
import com.personalblog.ragbackend.rag.dao.entity.RagConversationEntity;
import com.personalblog.ragbackend.rag.dao.entity.RagConversationMessageEntity;
import com.personalblog.ragbackend.rag.dao.entity.RagConversationSummaryEntity;
import com.personalblog.ragbackend.rag.dao.mapper.RagConversationMapper;
import com.personalblog.ragbackend.rag.dao.mapper.RagConversationMessageMapper;
import com.personalblog.ragbackend.rag.dao.mapper.RagConversationSummaryMapper;
import com.personalblog.ragbackend.rag.core.prompt.PromptTemplateLoader;
import com.personalblog.ragbackend.rag.config.MemoryProperties;
import com.personalblog.ragbackend.rag.controller.request.ConversationUpdateRequest;
import com.personalblog.ragbackend.rag.controller.vo.ConversationVO;
import com.personalblog.ragbackend.rag.service.ConversationService;
import com.personalblog.ragbackend.rag.service.bo.ConversationCreateBO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {
    private static final String CONVERSATION_TITLE_PROMPT_PATH = "prompt/conversation-title.st";

    private final RagConversationMapper conversationMapper;
    private final RagConversationMessageMapper messageMapper;
    private final RagConversationSummaryMapper summaryMapper;
    private final MemoryProperties memoryProperties;
    private final PromptTemplateLoader promptTemplateLoader;
    private final LLMService llmService;

    @Override
    public List<ConversationVO> listByUserId(String userId) {
        if (StrUtil.isBlank(userId)) {
            return List.of();
        }
        List<RagConversationEntity> records = conversationMapper.selectList(
                Wrappers.lambdaQuery(RagConversationEntity.class)
                        .eq(RagConversationEntity::getUserId, Long.valueOf(userId))
                        .eq(RagConversationEntity::getDeleted, 0)
                        .orderByDesc(RagConversationEntity::getLastTime)
        );
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream()
                .map(item -> ConversationVO.builder()
                        .conversationId(item.getConversationId())
                        .title(item.getTitle())
                        .lastTime(toDate(item.getLastTime()))
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOrUpdate(ConversationCreateBO request) {
        if (request == null || StrUtil.isBlank(request.getUserId()) || StrUtil.isBlank(request.getConversationId())) {
            return;
        }
        Long userId = parseUserId(request.getUserId());
        if (userId == null) {
            return;
        }
        RagConversationEntity existing = conversationMapper.selectOne(
                Wrappers.lambdaQuery(RagConversationEntity.class)
                        .eq(RagConversationEntity::getConversationId, request.getConversationId())
                        .eq(RagConversationEntity::getUserId, userId)
                        .eq(RagConversationEntity::getDeleted, 0)
                        .last("limit 1")
        );
        if (existing == null) {
            RagConversationEntity record = new RagConversationEntity();
            record.setConversationId(request.getConversationId());
            record.setUserId(userId);
            record.setTitle(resolveTitle(request.getQuestion()));
            record.setLastTime(toLocalDateTime(request.getLastTime()));
            record.setDeleted(0);
            conversationMapper.insert(record);
            return;
        }
        existing.setLastTime(toLocalDateTime(request.getLastTime()));
        conversationMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rename(String conversationId, ConversationUpdateRequest request) {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId) || request == null || StrUtil.isBlank(request.getTitle())) {
            throw new ClientException("Conversation not found");
        }
        int maxLen = memoryProperties.getTitleMaxLength();
        if (maxLen <= 0) {
            maxLen = 30;
        }
        String title = request.getTitle().trim();
        if (title.length() > maxLen) {
            throw new ClientException("Conversation title length cannot exceed " + maxLen + " characters");
        }

        RagConversationEntity record = conversationMapper.selectOne(
                Wrappers.lambdaQuery(RagConversationEntity.class)
                        .eq(RagConversationEntity::getConversationId, conversationId)
                        .eq(RagConversationEntity::getUserId, Long.valueOf(userId))
                        .eq(RagConversationEntity::getDeleted, 0)
                        .last("limit 1")
        );
        if (record == null) {
            throw new ClientException("Conversation not found");
        }
        record.setTitle(title);
        conversationMapper.updateById(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String conversationId) {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId)) {
            throw new ClientException("Conversation not found");
        }
        RagConversationEntity record = conversationMapper.selectOne(
                Wrappers.lambdaQuery(RagConversationEntity.class)
                        .eq(RagConversationEntity::getConversationId, conversationId)
                        .eq(RagConversationEntity::getUserId, Long.valueOf(userId))
                        .eq(RagConversationEntity::getDeleted, 0)
                        .last("limit 1")
        );
        if (record == null) {
            throw new ClientException("Conversation not found");
        }

        conversationMapper.deleteById(record.getId());
        messageMapper.delete(
                Wrappers.lambdaQuery(RagConversationMessageEntity.class)
                        .eq(RagConversationMessageEntity::getConversationId, conversationId)
                        .eq(RagConversationMessageEntity::getUserId, Long.valueOf(userId))
                        .eq(RagConversationMessageEntity::getDeleted, 0)
        );
        summaryMapper.delete(
                Wrappers.lambdaQuery(RagConversationSummaryEntity.class)
                        .eq(RagConversationSummaryEntity::getConversationId, conversationId)
                        .eq(RagConversationSummaryEntity::getUserId, Long.valueOf(userId))
                        .eq(RagConversationSummaryEntity::getDeleted, 0)
        );
    }

    private Date toDate(java.time.LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
    }

    private java.time.LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private Long parseUserId(String userId) {
        try {
            return Long.parseLong(userId.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveTitle(String question) {
        int maxLength = memoryProperties.getTitleMaxLength();
        if (maxLength <= 0) {
            maxLength = 30;
        }
        String prompt = promptTemplateLoader.render(
                CONVERSATION_TITLE_PROMPT_PATH,
                Map.of(
                        "title_max_chars", String.valueOf(maxLength),
                        "question", StrUtil.blankToDefault(question, "")
                )
        );
        try {
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(List.of(ChatMessage.user(prompt)))
                    .temperature(0.7D)
                    .topP(0.3D)
                    .thinking(false)
                    .build();
            return llmService.chat(chatRequest);
        } catch (Exception ignored) {
            return "New Conversation";
        }
    }
}

