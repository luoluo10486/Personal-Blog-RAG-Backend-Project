package com.personalblog.ragbackend.knowledge.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeBaseCreateRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeBasePageRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeBaseUpdateRequest;
import com.personalblog.ragbackend.knowledge.controller.vo.ChunkStrategyVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeBaseVO;
import com.personalblog.ragbackend.knowledge.service.KnowledgeBaseService;
import com.personalblog.ragbackend.knowledge.service.admin.KnowledgeBaseAdminService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {
    private final KnowledgeBaseAdminService knowledgeBaseAdminService;

    public KnowledgeBaseServiceImpl(KnowledgeBaseAdminService knowledgeBaseAdminService) {
        this.knowledgeBaseAdminService = knowledgeBaseAdminService;
    }

    @Override
    public String create(KnowledgeBaseCreateRequest requestParam) {
        return String.valueOf(knowledgeBaseAdminService.create(requestParam));
    }

    @Override
    public void update(KnowledgeBaseUpdateRequest requestParam) {
        knowledgeBaseAdminService.update(parseId(requestParam.getId()), requestParam);
    }

    @Override
    public void rename(String kbId, KnowledgeBaseUpdateRequest requestParam) {
        knowledgeBaseAdminService.update(parseId(kbId), requestParam);
    }

    @Override
    public void delete(String kbId) {
        knowledgeBaseAdminService.delete(parseId(kbId));
    }

    @Override
    public KnowledgeBaseVO queryById(String kbId) {
        return knowledgeBaseAdminService.get(parseId(kbId));
    }

    @Override
    public IPage<KnowledgeBaseVO> pageQuery(KnowledgeBasePageRequest requestParam) {
        return knowledgeBaseAdminService.page(requestParam);
    }

    @Override
    public List<ChunkStrategyVO> listChunkStrategies() {
        return knowledgeBaseAdminService.listChunkStrategies();
    }

    private Long parseId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        return Long.valueOf(value.trim());
    }
}
