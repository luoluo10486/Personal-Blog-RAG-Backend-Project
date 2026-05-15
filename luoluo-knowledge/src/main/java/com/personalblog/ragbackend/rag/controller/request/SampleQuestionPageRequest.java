package com.personalblog.ragbackend.rag.controller.request;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

@Data
public class SampleQuestionPageRequest extends Page {

    private String keyword;
}
