package com.personalblog.ragbackend.rag.controller.request;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.EqualsAndHashCode;
import lombok.Data;

@Data
@EqualsAndHashCode(callSuper = false)
public class SampleQuestionPageRequest extends Page {

    private String keyword;
}
