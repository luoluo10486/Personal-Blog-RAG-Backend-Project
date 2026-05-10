package com.personalblog.ragbackend.knowledge.dto.stream;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompletionPayload(String messageId, String title) {
}
