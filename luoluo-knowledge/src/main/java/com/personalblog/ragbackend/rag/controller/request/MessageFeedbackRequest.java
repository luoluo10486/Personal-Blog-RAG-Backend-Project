package com.personalblog.ragbackend.rag.controller.request;

import lombok.Data;

@Data
public class MessageFeedbackRequest {
    private Integer vote;
    private String reason;
    private String comment;
}
