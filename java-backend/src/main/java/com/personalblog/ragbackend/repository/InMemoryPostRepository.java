package com.personalblog.ragbackend.repository;

import com.personalblog.ragbackend.model.Post;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * InMemoryPostRepository 仓储类，负责数据读写操作。
 */
@Repository
public class InMemoryPostRepository implements PostRepository {
    private final List<Post> posts = List.of(
            new Post(
                    1,
                    "why-i-built-this-blog",
                    "我为什么搭建这个博客",
                    List.of("blog", "productivity"),
                    "我想要一个专注的空间，用来分享编程与思考笔记。"
            ),
            new Post(
                    2,
                    "rag-notes-from-practice",
                    "RAG 实践笔记",
                    List.of("rag", "llm", "backend"),
                    "在真实项目中，很多时候检索质量比模型大小更重要。"
            ),
            new Post(
                    3,
                    "python-fastapi-for-content-projects",
                    "面向内容项目的 Python + FastAPI",
                    List.of("python", "fastapi", "architecture"),
                    "FastAPI 提供清晰的类型、快速的校验和整洁的路由组织。"
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

