package com.personalblog.ragbackend.rag.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.rag.controller.request.QueryTermMappingCreateRequest;
import com.personalblog.ragbackend.rag.controller.request.QueryTermMappingPageRequest;
import com.personalblog.ragbackend.rag.controller.request.QueryTermMappingUpdateRequest;
import com.personalblog.ragbackend.rag.controller.vo.QueryTermMappingVO;
import com.personalblog.ragbackend.rag.service.QueryTermMappingAdminService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@MemberLoginRequired
public class QueryTermMappingController {
    private final QueryTermMappingAdminService queryTermMappingAdminService;

    public QueryTermMappingController(QueryTermMappingAdminService queryTermMappingAdminService) {
        this.queryTermMappingAdminService = queryTermMappingAdminService;
    }

    @GetMapping("/mappings")
    public R<IPage<QueryTermMappingVO>> pageQuery(QueryTermMappingPageRequest requestParam) {
        return R.ok(queryTermMappingAdminService.pageQuery(requestParam));
    }

    @GetMapping("/mappings/{id}")
    public R<QueryTermMappingVO> queryById(@PathVariable String id) {
        return R.ok(queryTermMappingAdminService.queryById(id));
    }

    @PostMapping("/mappings")
    public R<String> create(@RequestBody QueryTermMappingCreateRequest requestParam) {
        return R.ok(queryTermMappingAdminService.create(requestParam));
    }

    @PutMapping("/mappings/{id}")
    public R<Void> update(@PathVariable String id, @RequestBody QueryTermMappingUpdateRequest requestParam) {
        queryTermMappingAdminService.update(id, requestParam);
        return R.ok();
    }

    @DeleteMapping("/mappings/{id}")
    public R<Void> delete(@PathVariable String id) {
        queryTermMappingAdminService.delete(id);
        return R.ok();
    }
}
