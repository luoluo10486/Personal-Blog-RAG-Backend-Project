package com.personalblog.ragbackend.knowledge.controller.request;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

@Data
public class KnowledgeBasePageRequest extends Page {
    private String name;
}
