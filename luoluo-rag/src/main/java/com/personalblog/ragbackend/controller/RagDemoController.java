那】net package com.personalblog.ragbackend.controller;

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
 * RAG 演示控制器，提供健康检查、普通问答和流式问答接口。
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

    /**
     * 返回当前 RAG 模块的基础运行状态和关键配置。
     */
    @GetMapping("/health")
    public R<RagDemoHealthResponse> health() {
        return R.ok("rag demo is ready", new RagDemoHealthResponse(
                ragProperties.isEnabled(),
                ragProperties.getApiUrl(),
                ragProperties.getModel(),
                ragProperties.getEmbeddingApiUrl(),
                ragProperties.getEmbeddingModel(),
                ragProperties.getEmbeddingProvider(),
                ragProperties.getMilvus().isEnabled()
        ));
    }

    /**
     * 以非流式方式调用模型，并在结果完整后统一返回。
     */
    @PostMapping("/chat")
    public R<RagDemoChatResponse> chat(@Valid @RequestBody RagDemoChatRequest request) {
        return R.ok("chat completed", siliconFlowChatDemoService.chat(request));
    }

    @PostMapping("/embedding/search")
    public R<RagEmbeddingSearchResponse> embeddingSearch(@Valid @RequestBody RagEmbeddingSearchRequest request) {
        return R.ok("embedding search completed", siliconFlowEmbeddingDemoService.search(request));
    }

    /**
     * 以 SSE 方式输出模型生成过程，适合前端实时展示。
     */
    @PostMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@Valid @RequestBody RagDemoChatRequest request) {
        return siliconFlowChatDemoService.streamChat(request);
    }
}
