package com.personalblog.ragbackend.knowledge.service;

import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentEntity;

public interface KnowledgeDocumentScheduleService {
    void upsertSchedule(KnowledgeDocumentEntity documentDO);
    void syncScheduleIfExists(KnowledgeDocumentEntity documentDO);
    void deleteByDocId(String docId);
}
