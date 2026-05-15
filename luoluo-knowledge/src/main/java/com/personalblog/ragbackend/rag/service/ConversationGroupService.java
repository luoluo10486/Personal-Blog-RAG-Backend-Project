package com.personalblog.ragbackend.rag.service;

import com.personalblog.ragbackend.rag.dao.entity.RagConversationEntity;
import com.personalblog.ragbackend.rag.dao.entity.RagConversationMessageEntity;
import com.personalblog.ragbackend.rag.dao.entity.RagConversationSummaryEntity;

import java.util.Date;
import java.util.List;

public interface ConversationGroupService {
    List<RagConversationMessageEntity> listLatestUserOnlyMessages(String conversationId, String userId, int limit);

    List<RagConversationMessageEntity> listMessagesBetweenIds(String conversationId, String userId, String afterId, String beforeId);

    String findMaxMessageIdAtOrBefore(String conversationId, String userId, Date at);

    long countUserMessages(String conversationId, String userId);

    RagConversationSummaryEntity findLatestSummary(String conversationId, String userId);

    RagConversationEntity findConversation(String conversationId, String userId);
}

