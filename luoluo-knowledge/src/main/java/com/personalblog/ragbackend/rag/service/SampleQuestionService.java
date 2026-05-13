package com.personalblog.ragbackend.rag.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.rag.controller.request.SampleQuestionCreateRequest;
import com.personalblog.ragbackend.rag.controller.request.SampleQuestionPageRequest;
import com.personalblog.ragbackend.rag.controller.request.SampleQuestionUpdateRequest;
import com.personalblog.ragbackend.rag.controller.vo.SampleQuestionVO;

import java.util.List;

public interface SampleQuestionService {
    String create(SampleQuestionCreateRequest requestParam);

    void update(String id, SampleQuestionUpdateRequest requestParam);

    void delete(String id);

    SampleQuestionVO queryById(String id);

    IPage<SampleQuestionVO> pageQuery(SampleQuestionPageRequest requestParam);

    List<SampleQuestionVO> listRandomQuestions();
}
