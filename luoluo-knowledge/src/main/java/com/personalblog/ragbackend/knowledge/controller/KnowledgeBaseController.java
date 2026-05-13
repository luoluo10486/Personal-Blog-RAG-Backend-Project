package com.personalblog.ragbackend.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeBaseCreateRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeBasePageRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeBaseUpdateRequest;
import com.personalblog.ragbackend.knowledge.controller.vo.ChunkStrategyVO;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeBaseVO;
import com.personalblog.ragbackend.knowledge.service.KnowledgeBaseService;
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
    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping("/knowledge-base")
    public R<String> createKnowledgeBase(@RequestBody KnowledgeBaseCreateRequest request) {
        return R.ok(knowledgeBaseService.create(request));
    }

    @PutMapping("/knowledge-base/{kb-id}")
    public R<Void> renameKnowledgeBase(@PathVariable("kb-id") String kbId,
                                       @RequestBody KnowledgeBaseUpdateRequest request) {
        knowledgeBaseService.rename(kbId, request);
        return R.ok();
    }

    @DeleteMapping("/knowledge-base/{kb-id}")
    public R<Void> deleteKnowledgeBase(@PathVariable("kb-id") String kbId) {
        knowledgeBaseService.delete(kbId);
        return R.ok();
    }

    @GetMapping("/knowledge-base/{kb-id}")
    public R<KnowledgeBaseVO> queryKnowledgeBase(@PathVariable("kb-id") String kbId) {
        return R.ok(knowledgeBaseService.queryById(kbId));
    }

    @GetMapping("/knowledge-base")
    public R<IPage<KnowledgeBaseVO>> pageQuery(KnowledgeBasePageRequest request) {
        return R.ok(knowledgeBaseService.pageQuery(request));
    }

    @GetMapping("/knowledge-base/chunk-strategies")
    public R<List<ChunkStrategyVO>> listChunkStrategies() {
        return R.ok(knowledgeBaseService.listChunkStrategies());
    }
}
