package com.personalblog.ragbackend.rag.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.knowledge.service.admin.SampleQuestionAdminService;
import com.personalblog.ragbackend.rag.controller.request.SampleQuestionCreateRequest;
import com.personalblog.ragbackend.rag.controller.request.SampleQuestionPageRequest;
import com.personalblog.ragbackend.rag.controller.request.SampleQuestionUpdateRequest;
import com.personalblog.ragbackend.rag.controller.vo.SampleQuestionVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping
@MemberLoginRequired
public class SampleQuestionController {
    private final SampleQuestionAdminService sampleQuestionAdminService;

    public SampleQuestionController(SampleQuestionAdminService sampleQuestionAdminService) {
        this.sampleQuestionAdminService = sampleQuestionAdminService;
    }

    @GetMapping("/rag/sample-questions")
    public R<List<SampleQuestionVO>> listSampleQuestions() {
        return R.ok(sampleQuestionAdminService.listRandomQuestions());
    }

    @GetMapping("/sample-questions")
    public R<IPage<SampleQuestionVO>> pageQuery(SampleQuestionPageRequest request) {
        return R.ok(sampleQuestionAdminService.pageQuery(request));
    }

    @GetMapping("/sample-questions/{id}")
    public R<SampleQuestionVO> queryById(@PathVariable String id) {
        return R.ok(sampleQuestionAdminService.queryById(id));
    }

    @PostMapping("/sample-questions")
    public R<String> create(@RequestBody SampleQuestionCreateRequest request) {
        return R.ok(sampleQuestionAdminService.create(request));
    }

    @PutMapping("/sample-questions/{id}")
    public R<Void> update(@PathVariable String id, @RequestBody SampleQuestionUpdateRequest request) {
        sampleQuestionAdminService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/sample-questions/{id}")
    public R<Void> delete(@PathVariable String id) {
        sampleQuestionAdminService.delete(id);
        return R.ok();
    }
}
