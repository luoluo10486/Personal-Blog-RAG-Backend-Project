package com.personalblog.ragbackend.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeDocumentPageRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeDocumentUpdateRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeDocumentUploadRequest;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentChunkLogVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentSearchVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeDocumentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KnowledgeDocumentService {
    KnowledgeDocumentVO upload(String kbId, KnowledgeDocumentUploadRequest requestParam, MultipartFile file);
    void startChunk(String docId);
    void executeChunk(String docId);
    void delete(String docId);
    KnowledgeDocumentVO get(String docId);
    void update(String docId, KnowledgeDocumentUpdateRequest requestParam);
    IPage<KnowledgeDocumentVO> page(String kbId, KnowledgeDocumentPageRequest requestParam);
    void enable(String docId, boolean enabled);
    List<KnowledgeDocumentSearchVO> search(String keyword, int limit);
    IPage<KnowledgeDocumentChunkLogVO> getChunkLogs(String docId, Page<KnowledgeDocumentChunkLogVO> page);
}
