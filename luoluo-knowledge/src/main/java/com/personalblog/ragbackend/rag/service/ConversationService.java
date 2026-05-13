package com.personalblog.ragbackend.rag.service;

import com.personalblog.ragbackend.rag.controller.request.ConversationUpdateRequest;
import com.personalblog.ragbackend.rag.controller.vo.ConversationVO;
import com.personalblog.ragbackend.rag.service.bo.ConversationCreateBO;

import java.util.List;

public interface ConversationService {
    List<ConversationVO> listByUserId(String userId);

    void createOrUpdate(ConversationCreateBO request);

    void rename(String conversationId, ConversationUpdateRequest request);

    void delete(String conversationId);
}
