package com.personalblog.ragbackend.repository;

import com.personalblog.ragbackend.model.Post;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class InMemoryPostRepository implements PostRepository {
    private final List<Post> posts = List.of(
            new Post(
                    1,
                    "why-i-built-this-blog",
                    "Why I Built This Blog",
                    List.of("blog", "productivity"),
                    "I wanted a focused place to share notes about coding and thinking."
            ),
            new Post(
                    2,
                    "rag-notes-from-practice",
                    "RAG Notes From Practice",
                    List.of("rag", "llm", "backend"),
                    "In real projects, retrieval quality matters more than model size in many cases."
            ),
            new Post(
                    3,
                    "python-fastapi-for-content-projects",
                    "Python + FastAPI for Content Projects",
                    List.of("python", "fastapi", "architecture"),
                    "FastAPI gives clear typing, quick validation, and clean route organization."
            )
    );

    @Override
    public List<Post> findAll() {
        return posts;
    }

    @Override
    public Optional<Post> findBySlug(String slug) {
        return posts.stream().filter(post -> post.slug().equals(slug)).findFirst();
    }
}
