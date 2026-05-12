package com.personalblog.ragbackend.rag.service;

import com.personalblog.ragbackend.rag.controller.request.MessageFeedbackRequest;
import com.personalblog.ragbackend.rag.mq.event.MessageFeedbackEvent;

import java.util.List;
import java.util.Map;

public interface MessageFeedbackService {
    void submitFeedbackAsync(Long messageId, MessageFeedbackRequest request);

    void submitFeedback(Long messageId, MessageFeedbackRequest request);

    void submitFeedbackByEvent(MessageFeedbackEvent event);

    Map<Long, Integer> getUserVotes(Long userId, List<Long> messageIds);
}
