package com.personalblog.ragbackend.rag.controller;

import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.rag.service.RAGChatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 对齐 RAgent 入口形状的流式聊天控制器。
 */
@RestController
@RequestMapping
@MemberLoginRequired
public class RAGChatController {
    private final RAGChatService ragChatService;

    public RAGChatController(RAGChatService ragChatService) {
        this.ragChatService = ragChatService;
    }

    @GetMapping(value = "/rag/v3/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chat(@RequestParam String question,
                           @RequestParam(required = false) String conversationId,
                           @RequestParam(required = false, defaultValue = "false") Boolean deepThinking) {
        SseEmitter emitter = new SseEmitter(0L);
        ragChatService.streamChat(question, conversationId, deepThinking, emitter);
        return emitter;
    }

    @PostMapping("/rag/v3/stop")
    public R<Void> stop(@RequestParam String taskId) {
        ragChatService.stopTask(taskId);
        return R.ok();
    }
}
