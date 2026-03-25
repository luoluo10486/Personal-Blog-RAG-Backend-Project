package com.personalblog.ragbackend.repository;

import com.personalblog.ragbackend.model.Post;

import java.util.List;
import java.util.Optional;

/**
 * PostRepository 定义仓储层数据访问能力。
 */
public interface PostRepository {
    List<Post> findAll();

    Optional<Post> findBySlug(String slug);
}

