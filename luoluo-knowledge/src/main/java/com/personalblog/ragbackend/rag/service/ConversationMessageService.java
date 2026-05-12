package com.personalblog.ragbackend.rag.service;

import com.personalblog.ragbackend.rag.controller.vo.ConversationMessageVO;
import com.personalblog.ragbackend.rag.enums.ConversationMessageOrder;

import java.util.List;

public interface ConversationMessageService {
    List<ConversationMessageVO> listMessages(String conversationId, String userId, Integer limit, ConversationMessageOrder order);
}
