package com.personalblog.ragbackend.knowledge.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.knowledge.dao.entity.QueryTermMappingEntity;
import com.personalblog.ragbackend.knowledge.service.admin.QueryTermMappingAdminService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class QueryTermMappingController {
    private final QueryTermMappingAdminService queryTermMappingAdminService;

    public QueryTermMappingController(QueryTermMappingAdminService queryTermMappingAdminService) {
        this.queryTermMappingAdminService = queryTermMappingAdminService;
    }

    @GetMapping("/mappings")
    public R<?> pageQuery(@RequestParam(value = "pageNo", defaultValue = "1") long pageNo,
                          @RequestParam(value = "pageSize", defaultValue = "10") long pageSize,
                          @RequestParam(value = "domain", required = false) String domain,
                          @RequestParam(value = "keyword", required = false) String keyword) {
        return R.ok(queryTermMappingAdminService.pageQuery(new Page<>(pageNo, pageSize), domain, keyword));
    }

    @GetMapping("/mappings/{id}")
    public R<QueryTermMappingEntity> queryById(@PathVariable Long id) {
        return R.ok(queryTermMappingAdminService.queryById(id));
    }

    @PostMapping("/mappings")
    public R<Long> create(@RequestBody QueryTermMappingEntity request) {
        return R.ok(queryTermMappingAdminService.create(
                request.domain, request.sourceTerm, request.targetTerm, request.matchType, request.priority,
                request.enabled == null ? null : request.enabled == 1, request.remark));
    }

    @PutMapping("/mappings/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody QueryTermMappingEntity request) {
        queryTermMappingAdminService.update(
                id, request.domain, request.sourceTerm, request.targetTerm, request.matchType, request.priority,
                request.enabled == null ? null : request.enabled == 1, request.remark);
        return R.ok();
    }

    @DeleteMapping("/mappings/{id}")
    public R<Void> delete(@PathVariable Long id) {
        queryTermMappingAdminService.delete(id);
        return R.ok();
    }
}
