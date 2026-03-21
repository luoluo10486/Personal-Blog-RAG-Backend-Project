package com.personalblog.ragbackend.controller;

import com.personalblog.ragbackend.dto.rag.RagQueryRequest;
import com.personalblog.ragbackend.dto.rag.RagQueryResponse;
import com.personalblog.ragbackend.service.RagService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * RagController 控制器，负责处理对外 HTTP 请求。
 */
@RestController
@RequestMapping("${app.api-prefix}/rag")
public class RagController {
    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/query")
    @ResponseStatus(HttpStatus.OK)
    public RagQueryResponse query(@Valid @RequestBody RagQueryRequest request) {
        return ragService.query(request.question());
    }
}

