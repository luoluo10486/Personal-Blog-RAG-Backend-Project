package com.personalblog.ragbackend.controller;

import com.personalblog.ragbackend.dto.post.PostDetailResponse;
import com.personalblog.ragbackend.dto.post.PostSummaryResponse;
import com.personalblog.ragbackend.service.PostService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("${app.api-prefix}/posts")
public class PostController {
    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<PostSummaryResponse> listPosts() {
        return postService.listPosts();
    }

    @GetMapping("/{slug}")
    @ResponseStatus(HttpStatus.OK)
    public PostDetailResponse getPost(@PathVariable String slug) {
        return postService.getPost(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
    }
}
