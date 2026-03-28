package com.personalblog.ragbackend.controller;

import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.dto.rag.RagDemoChatRequest;
import com.personalblog.ragbackend.dto.rag.RagDemoChatResponse;
import com.personalblog.ragbackend.dto.rag.RagDemoHealthResponse;
import com.personalblog.ragbackend.rag.config.RagProperties;
import com.personalblog.ragbackend.service.SiliconFlowChatDemoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/luoluo/rag/demo")
public class RagDemoController {
    private final SiliconFlowChatDemoService siliconFlowChatDemoService;
    private final RagProperties ragProperties;

    public RagDemoController(SiliconFlowChatDemoService siliconFlowChatDemoService, RagProperties ragProperties) {
        this.siliconFlowChatDemoService = siliconFlowChatDemoService;
        this.ragProperties = ragProperties;
    }

    @GetMapping("/health")
    public R<RagDemoHealthResponse> health() {
        return R.ok("rag demo is ready", new RagDemoHealthResponse(
                ragProperties.isEnabled(),
                ragProperties.getApiUrl(),
                ragProperties.getModel()
        ));
    }

    @PostMapping("/chat")
    public R<RagDemoChatResponse> chat(@Valid @RequestBody RagDemoChatRequest request) {
        return R.ok("chat completed", siliconFlowChatDemoService.chat(request));
    }
}
