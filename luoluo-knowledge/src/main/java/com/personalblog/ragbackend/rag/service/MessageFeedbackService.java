package com.personalblog.ragbackend.rag.service;

import com.personalblog.ragbackend.rag.controller.request.MessageFeedbackRequest;
import com.personalblog.ragbackend.rag.mq.event.MessageFeedbackEvent;

import java.util.List;
import java.util.Map;

public interface MessageFeedbackService {
    void submitFeedbackAsync(String messageId, MessageFeedbackRequest request);

    void submitFeedback(String messageId, MessageFeedbackRequest request);

    void submitFeedbackByEvent(MessageFeedbackEvent event);

    Map<String, Integer> getUserVotes(String userId, List<String> messageIds);
}
