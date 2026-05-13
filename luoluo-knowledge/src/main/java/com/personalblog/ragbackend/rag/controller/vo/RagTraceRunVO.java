package com.personalblog.ragbackend.rag.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagTraceRunVO {
    private String traceId;
    private String traceName;
    private String entryMethod;
    private String conversationId;
    private String taskId;
    private String userId;
    private String username;
    private String status;
    private String errorMessage;
    private Long durationMs;
    private Date startTime;
    private Date endTime;
}
