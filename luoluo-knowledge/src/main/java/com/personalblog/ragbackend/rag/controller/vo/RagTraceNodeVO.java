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
public class RagTraceNodeVO {
    private String traceId;
    private String nodeId;
    private String parentNodeId;
    private Integer depth;
    private String nodeType;
    private String nodeName;
    private String className;
    private String methodName;
    private String status;
    private String errorMessage;
    private Long durationMs;
    private Date startTime;
    private Date endTime;
}
