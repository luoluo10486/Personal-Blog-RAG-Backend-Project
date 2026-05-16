package com.personalblog.ragbackend.knowledge.service;

import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentDO;

public interface KnowledgeDocumentScheduleService {
    void upsertSchedule(KnowledgeDocumentDO documentDO);
    void syncScheduleIfExists(KnowledgeDocumentDO documentDO);
    void deleteByDocId(String docId);
}

