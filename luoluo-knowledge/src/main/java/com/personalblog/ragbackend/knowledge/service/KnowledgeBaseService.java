package com.personalblog.ragbackend.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeBaseCreateRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeBasePageRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeBaseUpdateRequest;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeBaseVO;

public interface KnowledgeBaseService {
    String create(KnowledgeBaseCreateRequest requestParam);
    void update(KnowledgeBaseUpdateRequest requestParam);
    void rename(String kbId, KnowledgeBaseUpdateRequest requestParam);
    void delete(String kbId);
    KnowledgeBaseVO queryById(String kbId);
    IPage<KnowledgeBaseVO> pageQuery(KnowledgeBasePageRequest requestParam);
}
