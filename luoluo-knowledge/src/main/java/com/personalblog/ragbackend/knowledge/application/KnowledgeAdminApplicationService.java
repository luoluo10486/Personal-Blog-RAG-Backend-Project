package com.personalblog.ragbackend.knowledge.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.knowledge.dto.admin.ChunkStrategyOption;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeBaseCreateRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeBasePageRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeBaseUpdateRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeBaseView;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeChunkBatchRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeChunkCreateRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeChunkPageRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeChunkUpdateRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeChunkView;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentPageRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentSearchView;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentUpdateRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentUploadRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentView;
import com.personalblog.ragbackend.knowledge.service.admin.KnowledgeBaseAdminService;
import com.personalblog.ragbackend.knowledge.service.admin.KnowledgeChunkAdminService;
import com.personalblog.ragbackend.knowledge.service.admin.KnowledgeDocumentAdminService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class KnowledgeAdminApplicationService {
    private final KnowledgeBaseAdminService knowledgeBaseAdminService;
    private final KnowledgeDocumentAdminService knowledgeDocumentAdminService;
    private final KnowledgeChunkAdminService knowledgeChunkAdminService;

    public KnowledgeAdminApplicationService(KnowledgeBaseAdminService knowledgeBaseAdminService,
                                            KnowledgeDocumentAdminService knowledgeDocumentAdminService,
                                            KnowledgeChunkAdminService knowledgeChunkAdminService) {
        this.knowledgeBaseAdminService = knowledgeBaseAdminService;
        this.knowledgeDocumentAdminService = knowledgeDocumentAdminService;
        this.knowledgeChunkAdminService = knowledgeChunkAdminService;
    }

    public Long createKnowledgeBase(KnowledgeBaseCreateRequest request) {
        return knowledgeBaseAdminService.create(request);
    }

    public void updateKnowledgeBase(Long kbId, KnowledgeBaseUpdateRequest request) {
        knowledgeBaseAdminService.update(kbId, request);
    }

    public void deleteKnowledgeBase(Long kbId) {
        knowledgeBaseAdminService.delete(kbId);
    }

    public KnowledgeBaseView getKnowledgeBase(Long kbId) {
        return knowledgeBaseAdminService.get(kbId);
    }

    public IPage<KnowledgeBaseView> pageKnowledgeBases(KnowledgeBasePageRequest request) {
        return knowledgeBaseAdminService.page(request);
    }

    public List<ChunkStrategyOption> listChunkStrategies() {
        return knowledgeBaseAdminService.listChunkStrategies();
    }

    public KnowledgeDocumentView uploadDocument(Long kbId, KnowledgeDocumentUploadRequest request, MultipartFile file) {
        return knowledgeDocumentAdminService.upload(kbId, request, file);
    }

    public void startChunk(Long documentId) {
        knowledgeDocumentAdminService.startChunk(documentId);
    }

    public void deleteDocument(Long documentId) {
        knowledgeDocumentAdminService.delete(documentId);
    }

    public KnowledgeDocumentView getDocument(Long documentId) {
        return knowledgeDocumentAdminService.get(documentId);
    }

    public void updateDocument(Long documentId, KnowledgeDocumentUpdateRequest request) {
        knowledgeDocumentAdminService.update(documentId, request);
    }

    public IPage<KnowledgeDocumentView> pageDocuments(Long kbId, KnowledgeDocumentPageRequest request) {
        return knowledgeDocumentAdminService.page(kbId, request);
    }

    public List<KnowledgeDocumentSearchView> searchDocuments(String keyword, int limit) {
        return knowledgeDocumentAdminService.search(keyword, limit);
    }

    public void enableDocument(Long documentId, boolean enabled) {
        knowledgeDocumentAdminService.enable(documentId, enabled);
    }

    public IPage<KnowledgeChunkView> pageChunks(Long documentId, KnowledgeChunkPageRequest request) {
        return knowledgeChunkAdminService.page(documentId, request);
    }

    public KnowledgeChunkView createChunk(Long documentId, KnowledgeChunkCreateRequest request) {
        return knowledgeChunkAdminService.create(documentId, request);
    }

    public void updateChunk(Long documentId, Long chunkId, KnowledgeChunkUpdateRequest request) {
        knowledgeChunkAdminService.update(documentId, chunkId, request);
    }

    public void deleteChunk(Long documentId, Long chunkId) {
        knowledgeChunkAdminService.delete(documentId, chunkId);
    }

    public void enableChunk(Long documentId, Long chunkId, boolean enabled) {
        knowledgeChunkAdminService.enableChunk(documentId, chunkId, enabled);
    }

    public void batchEnableChunks(Long documentId, KnowledgeChunkBatchRequest request, boolean enabled) {
        knowledgeChunkAdminService.batchToggleEnabled(documentId, request, enabled);
    }
}
