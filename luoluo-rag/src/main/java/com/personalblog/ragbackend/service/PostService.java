package com.personalblog.ragbackend.service;

import com.personalblog.ragbackend.dto.post.PostDetailResponse;
import com.personalblog.ragbackend.dto.post.PostSummaryResponse;
import com.personalblog.ragbackend.repository.PostRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * PostService 服务类，封装业务处理逻辑。
 */
@Service
public class PostService {
    private final PostRepository postRepository;

    public PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    public List<PostSummaryResponse> listPosts() {
        return postRepository.findAll().stream()
                .map(post -> new PostSummaryResponse(
                        post.id(),
                        post.slug(),
                        post.title(),
                        post.tags()
                ))
                .toList();
    }

    public Optional<PostDetailResponse> getPost(String slug) {
        return postRepository.findBySlug(slug)
                .map(post -> new PostDetailResponse(
                        post.id(),
                        post.slug(),
                        post.title(),
                        post.tags(),
                        post.content()
                ));
    }
}

