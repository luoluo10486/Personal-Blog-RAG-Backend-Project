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
public class QueryTermMappingVO {
    private String id;
    private String sourceTerm;
    private String targetTerm;
    private Integer matchType;
    private Integer priority;
    private Boolean enabled;
    private String remark;
    private Date createTime;
    private Date updateTime;
}
