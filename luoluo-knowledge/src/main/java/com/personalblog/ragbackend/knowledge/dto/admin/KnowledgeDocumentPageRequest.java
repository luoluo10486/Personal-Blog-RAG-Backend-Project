package com.personalblog.ragbackend.knowledge.dto.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class KnowledgeDocumentPageRequest extends Page {
    private String status;
    private String keyword;
}
