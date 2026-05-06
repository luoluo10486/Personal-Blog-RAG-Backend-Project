package com.personalblog.ragbackend.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.knowledge.application.KnowledgeAdminApplicationService;
import com.personalblog.ragbackend.knowledge.dto.admin.ChunkStrategyOption;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeBaseCreateRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeBasePageRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeBaseUpdateRequest;
import com.personalblog.ragbackend.knowledge.dto.admin.KnowledgeBaseView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/luoluo/knowledge")
public class KnowledgeBaseController {
    private final KnowledgeAdminApplicationService knowledgeAdminApplicationService;

    public KnowledgeBaseController(KnowledgeAdminApplicationService knowledgeAdminApplicationService) {
        this.knowledgeAdminApplicationService = knowledgeAdminApplicationService;
    }

    @PostMapping("/knowledge-base")
    public R<Long> createKnowledgeBase(@Valid @RequestBody KnowledgeBaseCreateRequest request) {
        return R.ok(knowledgeAdminApplicationService.createKnowledgeBase(request));
    }

    @PutMapping("/knowledge-base/{kbId}")
    public R<Void> updateKnowledgeBase(@PathVariable Long kbId,
                                       @RequestBody KnowledgeBaseUpdateRequest request) {
        knowledgeAdminApplicationService.updateKnowledgeBase(kbId, request);
        return R.ok();
    }

    @DeleteMapping("/knowledge-base/{kbId}")
    public R<Void> deleteKnowledgeBase(@PathVariable Long kbId) {
        knowledgeAdminApplicationService.deleteKnowledgeBase(kbId);
        return R.ok();
    }

    @GetMapping("/knowledge-base/{kbId}")
    public R<KnowledgeBaseView> getKnowledgeBase(@PathVariable Long kbId) {
        return R.ok(knowledgeAdminApplicationService.getKnowledgeBase(kbId));
    }

    @GetMapping("/knowledge-base")
    public R<IPage<KnowledgeBaseView>> pageKnowledgeBases(KnowledgeBasePageRequest request) {
        return R.ok(knowledgeAdminApplicationService.pageKnowledgeBases(request));
    }

    @GetMapping("/knowledge-base/chunk-strategies")
    public R<List<ChunkStrategyOption>> listChunkStrategies() {
        return R.ok(knowledgeAdminApplicationService.listChunkStrategies());
    }
}
