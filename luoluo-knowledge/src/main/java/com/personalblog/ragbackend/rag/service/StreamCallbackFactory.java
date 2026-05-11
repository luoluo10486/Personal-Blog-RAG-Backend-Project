package com.personalblog.ragbackend.rag.service;

import org.springframework.stereotype.Component;

@Component
public class StreamCallbackFactory {
    public StreamChatEventHandler createChatEventHandler(StreamChatHandlerParams params) {
        return new StreamChatEventHandler(
                params.sender(),
                params.taskId(),
                params.conversationId(),
                params.ragConversationService(),
                params.loginUser(),
                params.question(),
                params.baseCode(),
                params.citationCount(),
                params.chunkSize(),
                params.taskManager(),
                params.userQuestionPrePersisted()
        );
    }
}
