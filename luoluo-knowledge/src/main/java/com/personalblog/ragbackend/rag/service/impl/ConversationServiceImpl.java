package com.personalblog.ragbackend.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.rag.config.RagMemoryProperties;
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
    private final RagMemoryProperties memoryProperties;

    public ConversationServiceImpl(RagConversationMapper conversationMapper,
                                   RagConversationMessageMapper messageMapper,
                                   RagConversationSummaryMapper summaryMapper,
                                   RagMemoryProperties memoryProperties) {
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
}
