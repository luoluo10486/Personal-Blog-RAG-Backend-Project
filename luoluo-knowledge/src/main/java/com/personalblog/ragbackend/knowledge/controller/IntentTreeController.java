package com.personalblog.ragbackend.knowledge.controller;

import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.knowledge.dao.entity.IntentNodeEntity;
import com.personalblog.ragbackend.knowledge.service.admin.IntentTreeAdminService;
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
public class IntentTreeController {
    private final IntentTreeAdminService intentTreeAdminService;

    public IntentTreeController(IntentTreeAdminService intentTreeAdminService) {
        this.intentTreeAdminService = intentTreeAdminService;
    }

    @GetMapping("/intent-tree/trees")
    public R<List<IntentNodeEntity>> tree() {
        return R.ok(intentTreeAdminService.tree());
    }

    @PostMapping("/intent-tree")
    public R<Long> createNode(@RequestBody IntentNodeEntity request) {
        return R.ok(intentTreeAdminService.create(request));
    }

    @PutMapping("/intent-tree/{id}")
    public R<Void> updateNode(@PathVariable Long id, @RequestBody IntentNodeEntity request) {
        intentTreeAdminService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/intent-tree/{id}")
    public R<Void> deleteNode(@PathVariable Long id) {
        intentTreeAdminService.delete(id);
        return R.ok();
    }

    @PostMapping("/intent-tree/batch/enable")
    public R<Void> batchEnable(@RequestBody List<Long> ids) {
        intentTreeAdminService.batchEnable(ids);
        return R.ok();
    }

    @PostMapping("/intent-tree/batch/disable")
    public R<Void> batchDisable(@RequestBody List<Long> ids) {
        intentTreeAdminService.batchDisable(ids);
        return R.ok();
    }

    @PostMapping("/intent-tree/batch/delete")
    public R<Void> batchDelete(@RequestBody List<Long> ids) {
        intentTreeAdminService.batchDelete(ids);
        return R.ok();
    }
}
