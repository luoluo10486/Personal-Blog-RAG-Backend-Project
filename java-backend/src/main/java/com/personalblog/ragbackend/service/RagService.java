package com.personalblog.ragbackend.service;

import com.personalblog.ragbackend.client.RagApiClient;
import com.personalblog.ragbackend.config.AppProperties;
import com.personalblog.ragbackend.dto.rag.RagQueryResponse;
import com.personalblog.ragbackend.dto.rag.RagReferenceResponse;
import com.personalblog.ragbackend.model.Post;
import com.personalblog.ragbackend.model.RetrievedChunk;
import com.personalblog.ragbackend.repository.PostRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * RagService 服务类，封装业务处理逻辑。
 */
@Service
public class RagService {
    private final RagApiClient ragApiClient;
    private final PostRepository postRepository;
    private final AppProperties appProperties;

    public RagService(RagApiClient ragApiClient, PostRepository postRepository, AppProperties appProperties) {
        this.ragApiClient = ragApiClient;
        this.postRepository = postRepository;
        this.appProperties = appProperties;
    }

    public RagQueryResponse query(String question) {
        List<RetrievedChunk> chunks = ragApiClient.retrieve(question);
        if (chunks.isEmpty()) {
            chunks = localRetrieve(question);
        }

        String answer = ragApiClient.generate(question, chunks);
        if (answer.isBlank()) {
            answer = localAnswer(chunks);
        }

        List<RagReferenceResponse> references = chunks.stream()
                .map(chunk -> new RagReferenceResponse(chunk.id(), chunk.title(), chunk.score()))
                .toList();

        return new RagQueryResponse(answer, references);
    }

    private List<RetrievedChunk> localRetrieve(String question) {
        String[] tokens = Arrays.stream(question.toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(token -> !token.isBlank())
                .toArray(String[]::new);

        return postRepository.findAll().stream()
                .map(post -> toChunk(post, score(post, tokens)))
                .filter(chunk -> chunk.score() > 0)
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(appProperties.getRag().getTopK())
                .toList();
    }

    private RetrievedChunk toChunk(Post post, int score) {
        return new RetrievedChunk(
                post.slug(),
                post.title(),
                post.content(),
                score
        );
    }

    private int score(Post post, String[] tokens) {
        if (tokens.length == 0) {
            return 0;
        }
        String target = (post.title() + " " + post.content()).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String token : tokens) {
            if (target.contains(token)) {
                score++;
            }
        }
        return score;
    }

    private String localAnswer(List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return "在本地博客文章中未找到相关上下文。";
        }
        String titles = chunks.stream().map(RetrievedChunk::title).reduce((a, b) -> a + ", " + b).orElse("");
        return "在以下文章中找到了相关上下文：" + titles + "。";
    }
}

