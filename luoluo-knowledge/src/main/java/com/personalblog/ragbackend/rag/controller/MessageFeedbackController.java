package com.personalblog.ragbackend.rag.controller;

import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.Result;
import com.personalblog.ragbackend.common.web.domain.Results;
import com.personalblog.ragbackend.rag.controller.request.MessageFeedbackRequest;
import com.personalblog.ragbackend.rag.service.MessageFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@MemberLoginRequired
@RequiredArgsConstructor
public class MessageFeedbackController {
    private final MessageFeedbackService feedbackService;

    @PostMapping("/conversations/messages/{messageId}/feedback")
    public Result<Void> submitFeedback(@PathVariable String messageId,
                                       @RequestBody MessageFeedbackRequest request) {
        feedbackService.submitFeedbackAsync(messageId, request);
        return Results.success();
    }
}
