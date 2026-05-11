package com.personalblog.ragbackend.knowledge.service.rag;

import com.personalblog.ragbackend.common.context.LoginUser;
import com.personalblog.ragbackend.common.web.sse.SseEmitterSender;

public record StreamChatHandlerParams(
        SseEmitterSender sender,
        String taskId,
        String conversationId,
        RagConversationService ragConversationService,
        LoginUser loginUser,
        String question,
        String baseCode,
        int citationCount,
        int chunkSize,
        StreamTaskManager taskManager
) {
}
