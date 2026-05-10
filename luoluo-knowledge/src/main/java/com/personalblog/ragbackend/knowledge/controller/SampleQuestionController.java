package com.personalblog.ragbackend.knowledge.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.knowledge.dao.entity.SampleQuestionEntity;
import com.personalblog.ragbackend.knowledge.service.admin.SampleQuestionAdminService;
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
    public R<List<SampleQuestionEntity>> listSampleQuestions() {
        return R.ok(sampleQuestionAdminService.listRandomQuestions());
    }

    @GetMapping("/sample-questions")
    public R<?> pageQuery(@RequestParam(value = "pageNo", defaultValue = "1") long pageNo,
                          @RequestParam(value = "pageSize", defaultValue = "10") long pageSize,
                          @RequestParam(value = "keyword", required = false) String keyword) {
        return R.ok(sampleQuestionAdminService.pageQuery(new Page<>(pageNo, pageSize), keyword));
    }

    @GetMapping("/sample-questions/{id}")
    public R<SampleQuestionEntity> queryById(@PathVariable Long id) {
        return R.ok(sampleQuestionAdminService.queryById(id));
    }

    @PostMapping("/sample-questions")
    public R<Long> create(@RequestBody SampleQuestionCreateRequest request) {
        return R.ok(sampleQuestionAdminService.create(request.title(), request.description(), request.question()));
    }

    @PutMapping("/sample-questions/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody SampleQuestionUpdateRequest request) {
        sampleQuestionAdminService.update(id, request.title(), request.description(), request.question());
        return R.ok();
    }

    @DeleteMapping("/sample-questions/{id}")
    public R<Void> delete(@PathVariable Long id) {
        sampleQuestionAdminService.delete(id);
        return R.ok();
    }

    public record SampleQuestionCreateRequest(String title, String description, String question) {}
    public record SampleQuestionUpdateRequest(String title, String description, String question) {}
}
