package com.personalblog.ragbackend.controller;

import com.personalblog.ragbackend.common.web.domain.R;
import com.personalblog.ragbackend.dto.post.PostDetailResponse;
import com.personalblog.ragbackend.dto.post.PostSummaryResponse;
import com.personalblog.ragbackend.service.PostService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * PostController 控制器，负责处理对外 HTTP 请求。
 */
@RestController
@RequestMapping("${app.api-prefix}/posts")
public class PostController {
    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public R<List<PostSummaryResponse>> listPosts() {
        return R.ok("查询成功", postService.listPosts());
    }

    @GetMapping("/{slug}")
    public R<PostDetailResponse> getPost(@PathVariable String slug) {
        PostDetailResponse post = postService.getPost(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在"));
        return R.ok("查询成功", post);
    }
}

