package com.personalblog.ragbackend.repository;

import com.personalblog.ragbackend.model.Post;

import java.util.List;
import java.util.Optional;

public interface PostRepository {
    List<Post> findAll();

    Optional<Post> findBySlug(String slug);
}
