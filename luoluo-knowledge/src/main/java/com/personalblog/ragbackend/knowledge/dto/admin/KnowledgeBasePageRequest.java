package com.personalblog.ragbackend.knowledge.dto.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.EqualsAndHashCode;
import lombok.Data;

@Data
@EqualsAndHashCode(callSuper = false)
public class KnowledgeBasePageRequest extends Page {
    private String name;
}
