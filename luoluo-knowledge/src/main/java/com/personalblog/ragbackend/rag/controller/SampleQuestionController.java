package com.personalblog.ragbackend.rag.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.rag.controller.request.SampleQuestionCreateRequest;
import com.personalblog.ragbackend.rag.controller.request.SampleQuestionPageRequest;
import com.personalblog.ragbackend.rag.controller.request.SampleQuestionUpdateRequest;
import com.personalblog.ragbackend.rag.controller.vo.SampleQuestionVO;
import com.personalblog.ragbackend.rag.service.SampleQuestionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping
@MemberLoginRequired
public class SampleQuestionController {
    private final SampleQuestionService sampleQuestionService;

    public SampleQuestionController(SampleQuestionService sampleQuestionService) {
        this.sampleQuestionService = sampleQuestionService;
    }

    @GetMapping("/rag/sample-questions")
    public R<List<SampleQuestionVO>> listSampleQuestions() {
        return R.ok(sampleQuestionService.listRandomQuestions());
    }

    @GetMapping("/sample-questions")
    public R<IPage<SampleQuestionVO>> pageQuery(SampleQuestionPageRequest request) {
        return R.ok(sampleQuestionService.pageQuery(request));
    }

    @GetMapping("/sample-questions/{id}")
    public R<SampleQuestionVO> queryById(@PathVariable String id) {
        return R.ok(sampleQuestionService.queryById(id));
    }

    @PostMapping("/sample-questions")
    public R<String> create(@RequestBody SampleQuestionCreateRequest request) {
        return R.ok(sampleQuestionService.create(request));
    }

    @PutMapping("/sample-questions/{id}")
    public R<Void> update(@PathVariable String id, @RequestBody SampleQuestionUpdateRequest request) {
        sampleQuestionService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/sample-questions/{id}")
    public R<Void> delete(@PathVariable String id) {
        sampleQuestionService.delete(id);
        return R.ok();
    }
}
