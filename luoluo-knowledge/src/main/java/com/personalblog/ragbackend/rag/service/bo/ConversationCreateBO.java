package com.personalblog.ragbackend.rag.service.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationCreateBO {
    private String conversationId;
    private String userId;
    private String question;
    private Date lastTime;
}
