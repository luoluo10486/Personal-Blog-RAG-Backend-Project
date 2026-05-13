package com.personalblog.ragbackend.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeChunkBatchRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeChunkCreateRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeChunkPageRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeChunkUpdateRequest;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeChunkVO;

import java.util.List;

public interface KnowledgeChunkService {
    IPage<KnowledgeChunkVO> pageQuery(String docId, KnowledgeChunkPageRequest requestParam);
    KnowledgeChunkVO create(String docId, KnowledgeChunkCreateRequest requestParam);
    void batchCreate(String docId, List<KnowledgeChunkCreateRequest> requestParams);
    void batchCreate(String docId, List<KnowledgeChunkCreateRequest> requestParams, boolean writeVector);
    void update(String docId, String chunkId, KnowledgeChunkUpdateRequest requestParam);
    void delete(String docId, String chunkId);
    void enableChunk(String docId, String chunkId, boolean enabled);
    void batchToggleEnabled(String docId, KnowledgeChunkBatchRequest requestParam, boolean enabled);
    void updateEnabledByDocId(String docId, String kbId, boolean enabled);
    List<KnowledgeChunkVO> listByDocId(String docId);
    void deleteByDocId(String docId);
}
