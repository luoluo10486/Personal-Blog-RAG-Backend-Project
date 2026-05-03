package com.personalblog.ragbackend.knowledge.controller;

import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.knowledge.application.KnowledgeRagApplicationService;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeAskRequest;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeAskResponse;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeHealthResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/luoluo/knowledge", "/luoluo/rag"})
public class KnowledgeRagController {
    private final KnowledgeRagApplicationService knowledgeRagApplicationService;

    public KnowledgeRagController(KnowledgeRagApplicationService knowledgeRagApplicationService) {
        this.knowledgeRagApplicationService = knowledgeRagApplicationService;
    }

    @GetMapping("/health")
    public R<KnowledgeHealthResponse> health() {
        return R.ok(knowledgeRagApplicationService.health());
    }

    @PostMapping("/ask")
    public R<KnowledgeAskResponse> ask(@Valid @RequestBody KnowledgeAskRequest request) {
        return R.ok(knowledgeRagApplicationService.ask(request));
    }
}

