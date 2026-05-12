package com.personalblog.ragbackend.rag.controller.vo;

import java.util.Date;

public record ConversationVO(String conversationId, String title, Date lastTime) {
}
