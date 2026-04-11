package com.personalblog.ragbackend.controller;

import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.dto.rag.RagDemoChatRequest;
import com.personalblog.ragbackend.dto.rag.RagDemoChatResponse;
import com.personalblog.ragbackend.dto.rag.RagDemoHealthResponse;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchRequest;
import com.personalblog.ragbackend.dto.rag.RagEmbeddingSearchResponse;
import com.personalblog.ragbackend.dto.rag.RagEvaluationRequest;
import com.personalblog.ragbackend.dto.rag.RagEvaluationResponse;
import com.personalblog.ragbackend.dto.rag.RagGenerationRequest;
import com.personalblog.ragbackend.dto.rag.RagGenerationResponse;
import com.personalblog.ragbackend.rag.config.RagProperties;
import com.personalblog.ragbackend.service.RagEvaluationService;
import com.personalblog.ragbackend.service.RagGenerationDemoService;
import com.personalblog.ragbackend.service.SiliconFlowChatDemoService;
import com.personalblog.ragbackend.service.SiliconFlowEmbeddingDemoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * RAG 演示接口集合：健康检查、检索、生成、对话。
 */
@RestController
@RequestMapping("/luoluo/rag/demo")
@RequiredArgsConstructor
public class RagDemoController {
    private final SiliconFlowChatDemoService siliconFlowChatDemoService;
    private final SiliconFlowEmbeddingDemoService siliconFlowEmbeddingDemoService;
    private final RagGenerationDemoService ragGenerationDemoService;
    private final RagEvaluationService ragEvaluationService;
    private final RagProperties ragProperties;

    @GetMapping("/health")
    public R<RagDemoHealthResponse> health() {
        return R.ok("RAG 演示模块已就绪", new RagDemoHealthResponse(
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
        return R.ok("对话完成", siliconFlowChatDemoService.chat(request));
    }

    @PostMapping("/embedding/search")
    public R<RagEmbeddingSearchResponse> embeddingSearch(@Valid @RequestBody RagEmbeddingSearchRequest request) {
        return R.ok("检索完成", siliconFlowEmbeddingDemoService.search(request));
    }

    @PostMapping("/generate")
    public R<RagGenerationResponse> generate(@Valid @RequestBody RagGenerationRequest request) {
        return R.ok("RAG 生成完成", ragGenerationDemoService.generate(request));
    }

    @PostMapping("/evaluate")
    public R<RagEvaluationResponse> evaluate(@RequestBody(required = false) RagEvaluationRequest request) {
        RagEvaluationRequest actualRequest = request == null
                ? new RagEvaluationRequest(null, false, 3)
                : request;
        return R.ok("RAG 评测完成", ragEvaluationService.evaluate(actualRequest));
    }

    @PostMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@Valid @RequestBody RagDemoChatRequest request) {
        return siliconFlowChatDemoService.streamChat(request);
    }
}
