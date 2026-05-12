package com.personalblog.ragbackend.rag.controller.vo;

import java.util.Date;

public record ConversationMessageVO(String id,
                                    String conversationId,
                                    String role,
                                    String content,
                                    String thinkingContent,
                                    Integer thinkingDuration,
                                    Integer vote,
                                    Date createTime) {
}
