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
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeDocumentChunkLogView;
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

    public String createKnowledgeBase(KnowledgeBaseCreateRequest request) {
        return String.valueOf(knowledgeBaseAdminService.create(request));
    }

    public void updateKnowledgeBase(String kbId, KnowledgeBaseUpdateRequest request) {
        knowledgeBaseAdminService.update(parseId(kbId), request);
    }

    public void deleteKnowledgeBase(String kbId) {
        knowledgeBaseAdminService.delete(parseId(kbId));
    }

    public KnowledgeBaseView getKnowledgeBase(String kbId) {
        return knowledgeBaseAdminService.get(parseId(kbId));
    }

    public IPage<KnowledgeBaseView> pageKnowledgeBases(KnowledgeBasePageRequest request) {
        return knowledgeBaseAdminService.page(request);
    }

    public List<ChunkStrategyOption> listChunkStrategies() {
        return knowledgeBaseAdminService.listChunkStrategies();
    }

    public KnowledgeDocumentView uploadDocument(String kbId, KnowledgeDocumentUploadRequest request, MultipartFile file) {
        return knowledgeDocumentAdminService.upload(parseId(kbId), request, file);
    }

    public void startChunk(String documentId) {
        knowledgeDocumentAdminService.startChunk(parseId(documentId));
    }

    public void deleteDocument(String documentId) {
        knowledgeDocumentAdminService.delete(parseId(documentId));
    }

    public KnowledgeDocumentView getDocument(String documentId) {
        return knowledgeDocumentAdminService.get(parseId(documentId));
    }

    public void updateDocument(String documentId, KnowledgeDocumentUpdateRequest request) {
        knowledgeDocumentAdminService.update(parseId(documentId), request);
    }

    public IPage<KnowledgeDocumentView> pageDocuments(String kbId, KnowledgeDocumentPageRequest request) {
        return knowledgeDocumentAdminService.page(parseId(kbId), request);
    }

    public List<KnowledgeDocumentSearchView> searchDocuments(String keyword, int limit) {
        return knowledgeDocumentAdminService.search(keyword, limit);
    }

    public void enableDocument(String documentId, boolean enabled) {
        knowledgeDocumentAdminService.enable(parseId(documentId), enabled);
    }

    public IPage<KnowledgeDocumentChunkLogView> pageChunkLogs(String documentId, long current, long size) {
        return knowledgeDocumentAdminService.pageChunkLogs(parseId(documentId), current, size);
    }

    public IPage<KnowledgeChunkView> pageChunks(String documentId, KnowledgeChunkPageRequest request) {
        return knowledgeChunkAdminService.page(parseId(documentId), request);
    }

    public KnowledgeChunkView createChunk(String documentId, KnowledgeChunkCreateRequest request) {
        return knowledgeChunkAdminService.create(parseId(documentId), request);
    }

    public void updateChunk(String documentId, String chunkId, KnowledgeChunkUpdateRequest request) {
        knowledgeChunkAdminService.update(parseId(documentId), parseId(chunkId), request);
    }

    public void deleteChunk(String documentId, String chunkId) {
        knowledgeChunkAdminService.delete(parseId(documentId), parseId(chunkId));
    }

    public void enableChunk(String documentId, String chunkId, boolean enabled) {
        knowledgeChunkAdminService.enableChunk(parseId(documentId), parseId(chunkId), enabled);
    }

    public void batchEnableChunks(String documentId, KnowledgeChunkBatchRequest request, boolean enabled) {
        knowledgeChunkAdminService.batchToggleEnabled(parseId(documentId), request, enabled);
    }

    private Long parseId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("id not found");
        }
    }
}
