package com.personalblog.ragbackend.controller;

import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.dto.rag.RagDemoChatRequest;
import com.personalblog.ragbackend.dto.rag.RagDemoChatResponse;
import com.personalblog.ragbackend.dto.rag.RagDemoHealthResponse;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchRequest;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchResponse;
import com.personalblog.ragbackend.rag.config.RagProperties;
import com.personalblog.ragbackend.service.SiliconFlowChatDemoService;
import com.personalblog.ragbackend.service.SiliconFlowEmbeddingDemoService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Demo endpoints for health checks, chat, and retrieval.
 */
@RestController
@RequestMapping("/luoluo/rag/demo")
public class RagDemoController {
    private final SiliconFlowChatDemoService siliconFlowChatDemoService;
    private final SiliconFlowEmbeddingDemoService siliconFlowEmbeddingDemoService;
    private final RagProperties ragProperties;

    public RagDemoController(
            SiliconFlowChatDemoService siliconFlowChatDemoService,
            SiliconFlowEmbeddingDemoService siliconFlowEmbeddingDemoService,
            RagProperties ragProperties
    ) {
        this.siliconFlowChatDemoService = siliconFlowChatDemoService;
        this.siliconFlowEmbeddingDemoService = siliconFlowEmbeddingDemoService;
        this.ragProperties = ragProperties;
    }

    @GetMapping("/health")
    public R<RagDemoHealthResponse> health() {
        return R.ok("rag demo is ready", new RagDemoHealthResponse(
                ragProperties.isEnabled(),
                ragProperties.getApiUrl(),
                ragProperties.getModel(),
                ragProperties.getEmbeddingApiUrl(),
                ragProperties.getEmbeddingModel(),
                ragProperties.getEmbeddingProvider(),
                ragProperties.getMilvus().isEnabled(),
                ragProperties.getRetrieval().getMode().name(),
                ragProperties.getRerank().isEnabled(),
                ragProperties.getRerank().getProvider(),
                ragProperties.getRerank().getModel()
        ));
    }

    @PostMapping("/chat")
    public R<RagDemoChatResponse> chat(@Valid @RequestBody RagDemoChatRequest request) {
        return R.ok("chat completed", siliconFlowChatDemoService.chat(request));
    }

    @PostMapping("/embedding/search")
    public R<RagEmbeddingSearchResponse> embeddingSearch(@Valid @RequestBody RagEmbeddingSearchRequest request) {
        return R.ok("embedding search completed", siliconFlowEmbeddingDemoService.search(request));
    }

    @PostMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@Valid @RequestBody RagDemoChatRequest request) {
        return siliconFlowChatDemoService.streamChat(request);
    }
}
