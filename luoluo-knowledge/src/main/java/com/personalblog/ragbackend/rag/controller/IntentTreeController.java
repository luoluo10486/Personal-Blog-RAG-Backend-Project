package com.personalblog.ragbackend.rag.controller;

import com.personalblog.ragbackend.common.satoken.annotation.MemberLoginRequired;
import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.knowledge.service.admin.IntentTreeAdminService;
import com.personalblog.ragbackend.rag.controller.request.IntentNodeBatchRequest;
import com.personalblog.ragbackend.rag.controller.request.IntentNodeCreateRequest;
import com.personalblog.ragbackend.rag.controller.request.IntentNodeUpdateRequest;
import com.personalblog.ragbackend.rag.controller.vo.IntentNodeTreeVO;
import com.personalblog.ragbackend.rag.core.mcp.McpToolDescriptor;
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
    public R<List<IntentNodeTreeVO>> tree() {
        return R.ok(intentTreeAdminService.tree());
    }

    @GetMapping("/intent-tree/mcp-tools")
    public R<List<McpToolDescriptor>> mcpTools() {
        return R.ok(intentTreeAdminService.listMcpTools());
    }

    @PostMapping("/intent-tree")
    public R<String> createNode(@RequestBody IntentNodeCreateRequest request) {
        return R.ok(intentTreeAdminService.create(request));
    }

    @PutMapping("/intent-tree/{id}")
    public R<Void> updateNode(@PathVariable String id, @RequestBody IntentNodeUpdateRequest request) {
        intentTreeAdminService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/intent-tree/{id}")
    public R<Void> deleteNode(@PathVariable String id) {
        intentTreeAdminService.delete(id);
        return R.ok();
    }

    @PostMapping("/intent-tree/batch/enable")
    public R<Void> batchEnable(@RequestBody IntentNodeBatchRequest request) {
        intentTreeAdminService.batchEnable(request == null ? null : request.getIds());
        return R.ok();
    }

    @PostMapping("/intent-tree/batch/disable")
    public R<Void> batchDisable(@RequestBody IntentNodeBatchRequest request) {
        intentTreeAdminService.batchDisable(request == null ? null : request.getIds());
        return R.ok();
    }

    @PostMapping("/intent-tree/batch/delete")
    public R<Void> batchDelete(@RequestBody IntentNodeBatchRequest request) {
        intentTreeAdminService.batchDelete(request == null ? null : request.getIds());
        return R.ok();
    }
}
