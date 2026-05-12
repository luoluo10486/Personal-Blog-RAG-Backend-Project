package com.personalblog.ragbackend.rag.controller.request;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public class SampleQuestionPageRequest extends Page<Object> {
    private String keyword;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
