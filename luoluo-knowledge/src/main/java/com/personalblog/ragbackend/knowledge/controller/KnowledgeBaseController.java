package com.personalblog.ragbackend.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.knowledge.application.KnowledgeAdminApplicationService;
import com.personalblog.ragbackend.knowledge.dto.admin.ChunkStrategyVO;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeBaseCreateRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeBasePageRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeBaseUpdateRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeBaseVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@MemberLoginRequired
public class KnowledgeBaseController {
    private final KnowledgeAdminApplicationService knowledgeAdminApplicationService;

    public KnowledgeBaseController(KnowledgeAdminApplicationService knowledgeAdminApplicationService) {
        this.knowledgeAdminApplicationService = knowledgeAdminApplicationService;
    }

    @PostMapping("/knowledge-base")
    public R<String> createKnowledgeBase(@RequestBody KnowledgeBaseCreateRequest request) {
        return R.ok(knowledgeAdminApplicationService.createKnowledgeBase(request));
    }

    @PutMapping("/knowledge-base/{kb-id}")
    public R<Void> renameKnowledgeBase(@PathVariable("kb-id") String kbId,
                                       @RequestBody KnowledgeBaseUpdateRequest request) {
        knowledgeAdminApplicationService.updateKnowledgeBase(kbId, request);
        return R.ok();
    }

    @DeleteMapping("/knowledge-base/{kb-id}")
    public R<Void> deleteKnowledgeBase(@PathVariable("kb-id") String kbId) {
        knowledgeAdminApplicationService.deleteKnowledgeBase(kbId);
        return R.ok();
    }

    @GetMapping("/knowledge-base/{kb-id}")
    public R<KnowledgeBaseVO> queryKnowledgeBase(@PathVariable("kb-id") String kbId) {
        return R.ok(knowledgeAdminApplicationService.getKnowledgeBase(kbId));
    }

    @GetMapping("/knowledge-base")
    public R<IPage<KnowledgeBaseVO>> pageQuery(KnowledgeBasePageRequest request) {
        return R.ok(knowledgeAdminApplicationService.pageKnowledgeBases(request));
    }

    @GetMapping("/knowledge-base/chunk-strategies")
    public R<List<ChunkStrategyVO>> listChunkStrategies() {
        return R.ok(knowledgeAdminApplicationService.listChunkStrategies());
    }
}
