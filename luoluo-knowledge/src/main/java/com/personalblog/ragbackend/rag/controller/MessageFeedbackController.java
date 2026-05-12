package com.personalblog.ragbackend.rag.controller;

import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.rag.controller.request.MessageFeedbackRequest;
import com.personalblog.ragbackend.rag.service.MessageFeedbackService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@MemberLoginRequired
public class MessageFeedbackController {
    private final MessageFeedbackService feedbackService;

    public MessageFeedbackController(MessageFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/conversations/messages/{messageId}/feedback")
    public R<Void> submitFeedback(@PathVariable Long messageId,
                                  @RequestBody MessageFeedbackRequest request) {
        feedbackService.submitFeedbackAsync(messageId, request);
        return R.ok();
    }
}
