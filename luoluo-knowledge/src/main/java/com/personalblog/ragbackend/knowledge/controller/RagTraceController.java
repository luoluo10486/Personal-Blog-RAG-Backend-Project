package com.personalblog.ragbackend.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.knowledge.dto.rag.RagTraceDetailView;
import com.personalblog.ragbackend.knowledge.dto.rag.RagTraceNodeView;
import com.personalblog.ragbackend.knowledge.dto.rag.RagTraceRunPageRequest;
import com.personalblog.ragbackend.knowledge.dto.rag.RagTraceRunView;
import com.personalblog.ragbackend.knowledge.service.rag.RagTraceQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping
@MemberLoginRequired
public class RagTraceController {
    private final RagTraceQueryService ragTraceQueryService;

    public RagTraceController(RagTraceQueryService ragTraceQueryService) {
        this.ragTraceQueryService = ragTraceQueryService;
    }

    @GetMapping("/rag/traces/runs")
    public R<IPage<RagTraceRunView>> pageRuns(RagTraceRunPageRequest request) {
        return R.ok(ragTraceQueryService.pageRuns(request));
    }

    @GetMapping("/rag/traces/runs/{traceId}")
    public R<RagTraceDetailView> detail(@PathVariable String traceId) {
        return R.ok(ragTraceQueryService.detail(traceId));
    }

    @GetMapping("/rag/traces/runs/{traceId}/nodes")
    public R<List<RagTraceNodeView>> nodes(@PathVariable String traceId) {
        return R.ok(ragTraceQueryService.listNodes(traceId));
    }
}
