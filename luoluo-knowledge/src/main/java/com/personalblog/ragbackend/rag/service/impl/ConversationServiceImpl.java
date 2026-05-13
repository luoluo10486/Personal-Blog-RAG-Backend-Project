package com.personalblog.ragbackend.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.rag.config.MemoryProperties;
import com.personalblog.ragbackend.rag.service.bo.ConversationCreateBO;
import com.personalblog.ragbackend.knowledge.dao.entity.RagConversationEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.RagConversationMessageEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.RagConversationSummaryEntity;
import com.personalblog.ragbackend.knowledge.mapper.RagConversationMapper;
import com.personalblog.ragbackend.knowledge.mapper.RagConversationMessageMapper;
import com.personalblog.ragbackend.knowledge.mapper.RagConversationSummaryMapper;
import com.personalblog.ragbackend.rag.controller.request.ConversationUpdateRequest;
import com.personalblog.ragbackend.rag.controller.vo.ConversationVO;
import com.personalblog.ragbackend.rag.service.ConversationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Service
public class ConversationServiceImpl implements ConversationService {
    private final RagConversationMapper conversationMapper;
    private final RagConversationMessageMapper messageMapper;
    private final RagConversationSummaryMapper summaryMapper;
    private final MemoryProperties memoryProperties;

    public ConversationServiceImpl(RagConversationMapper conversationMapper,
                                   RagConversationMessageMapper messageMapper,
                                   RagConversationSummaryMapper summaryMapper,
                                   MemoryProperties memoryProperties) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.summaryMapper = summaryMapper;
        this.memoryProperties = memoryProperties;
    }

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
                .map(item -> new ConversationVO(
                        item.getConversationId(),
                        item.getTitle(),
                        toDate(item.getLastTime())))
                .toList();
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

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void rename(String conversationId, ConversationUpdateRequest request) {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId) || request == null || StrUtil.isBlank(request.title())) {
            throw new IllegalArgumentException("Conversation not found");
        }
        int maxLen = Math.max(1, memoryProperties.getTitleMaxLength());
        String title = request.title().trim();
        if (title.length() > maxLen) {
            title = title.substring(0, maxLen);
        }

        RagConversationEntity record = conversationMapper.selectOne(
                Wrappers.lambdaQuery(RagConversationEntity.class)
                        .eq(RagConversationEntity::getConversationId, conversationId)
                        .eq(RagConversationEntity::getUserId, Long.valueOf(userId))
                        .eq(RagConversationEntity::getDeleted, 0)
                        .last("limit 1")
        );
        if (record == null) {
            throw new IllegalArgumentException("Conversation not found");
        }
        record.setTitle(title);
        conversationMapper.updateById(record);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(String conversationId) {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId)) {
            throw new IllegalArgumentException("Conversation not found");
        }
        RagConversationEntity record = conversationMapper.selectOne(
                Wrappers.lambdaQuery(RagConversationEntity.class)
                        .eq(RagConversationEntity::getConversationId, conversationId)
                        .eq(RagConversationEntity::getUserId, Long.valueOf(userId))
                        .eq(RagConversationEntity::getDeleted, 0)
                        .last("limit 1")
        );
        if (record == null) {
            throw new IllegalArgumentException("Conversation not found");
        }

        conversationMapper.deleteById(record.getId());
        messageMapper.delete(
                Wrappers.lambdaQuery(RagConversationMessageEntity.class)
                        .eq(RagConversationMessageEntity::getConversationId, conversationId)
                        .eq(RagConversationMessageEntity::getUserId, Long.valueOf(userId))
        );
        summaryMapper.delete(
                Wrappers.lambdaQuery(RagConversationSummaryEntity.class)
                        .eq(RagConversationSummaryEntity::getConversationId, conversationId)
                        .eq(RagConversationSummaryEntity::getUserId, Long.valueOf(userId))
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
        if (StrUtil.isBlank(question)) {
            return "New Conversation";
        }
        String trimmed = question.trim();
        return trimmed.length() > memoryProperties.getTitleMaxLength()
                ? trimmed.substring(0, memoryProperties.getTitleMaxLength())
                : trimmed;
    }
}
