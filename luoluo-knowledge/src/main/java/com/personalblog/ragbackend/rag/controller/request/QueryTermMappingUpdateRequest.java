package com.personalblog.ragbackend.rag.controller.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryTermMappingUpdateRequest {
    private String sourceTerm;
    private String targetTerm;
    private Integer matchType;
    private Integer priority;
    private Boolean enabled;
    private String remark;
}
