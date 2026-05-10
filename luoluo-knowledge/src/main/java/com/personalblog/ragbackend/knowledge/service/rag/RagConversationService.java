package com.personalblog.ragbackend.knowledge.service.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RagConversationService {

    private static final Logger log = LoggerFactory.getLogger(RagConversationService.class);

    public void persistExchange(String conversationId,
                                String question,
                                String answer,
                                String baseCode,
                                int citationCount) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        log.debug("RAG conversation exchange persisted as no-op, conversationId={}, baseCode={}, citationCount={}",
                conversationId, baseCode, citationCount);
    }
}
