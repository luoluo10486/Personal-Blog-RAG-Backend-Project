package com.personalblog.ragbackend.knowledge.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeDocumentPageRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeDocumentUpdateRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeDocumentUploadRequest;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentChunkLogVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentSearchVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentVO;
import com.personalblog.ragbackend.knowledge.service.KnowledgeDocumentService;
import com.personalblog.ragbackend.knowledge.service.admin.KnowledgeDocumentAdminService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {
    private final KnowledgeDocumentAdminService knowledgeDocumentAdminService;

    public KnowledgeDocumentServiceImpl(KnowledgeDocumentAdminService knowledgeDocumentAdminService) {
        this.knowledgeDocumentAdminService = knowledgeDocumentAdminService;
    }

    @Override
    public KnowledgeDocumentVO upload(String kbId, KnowledgeDocumentUploadRequest requestParam, MultipartFile file) {
        return knowledgeDocumentAdminService.upload(parseId(kbId), requestParam, file);
    }

    @Override
    public void startChunk(String docId) {
        knowledgeDocumentAdminService.startChunk(parseId(docId));
    }

    @Override
    public void executeChunk(String docId) {
        knowledgeDocumentAdminService.executeChunk(parseId(docId));
    }

    @Override
    public void delete(String docId) {
        knowledgeDocumentAdminService.delete(parseId(docId));
    }

    @Override
    public KnowledgeDocumentVO get(String docId) {
        return knowledgeDocumentAdminService.get(parseId(docId));
    }

    @Override
    public void update(String docId, KnowledgeDocumentUpdateRequest requestParam) {
        knowledgeDocumentAdminService.update(parseId(docId), requestParam);
    }

    @Override
    public IPage<KnowledgeDocumentVO> page(String kbId, KnowledgeDocumentPageRequest requestParam) {
        return knowledgeDocumentAdminService.page(parseId(kbId), requestParam);
    }

    @Override
    public void enable(String docId, boolean enabled) {
        knowledgeDocumentAdminService.enable(parseId(docId), enabled);
    }

    @Override
    public List<KnowledgeDocumentSearchVO> search(String keyword, int limit) {
        return knowledgeDocumentAdminService.search(keyword, limit);
    }

    @Override
    public IPage<KnowledgeDocumentChunkLogVO> getChunkLogs(String docId, Page<KnowledgeDocumentChunkLogVO> page) {
        return knowledgeDocumentAdminService.pageChunkLogs(parseId(docId), page.getCurrent(), page.getSize());
    }

    private Long parseId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        return Long.valueOf(value.trim());
    }
}
