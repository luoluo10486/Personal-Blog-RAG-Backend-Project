package com.personalblog.ragbackend.knowledge.service.retrieval;

import com.personalblog.ragbackend.infra.ai.convention.RetrievedChunk;
import com.personalblog.ragbackend.infra.ai.rerank.RerankService;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeCollectionNameResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@Primary
@ConditionalOnBean(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "app.knowledge.jdbc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JdbcKnowledgeRetriever implements KnowledgeRetriever {
    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[^\\p{IsHan}a-zA-Z0-9]+");
    private static final int MAX_QUERY_TOKENS = 8;

    private final JdbcTemplate jdbcTemplate;
    private final KnowledgeProperties knowledgeProperties;
    private final KnowledgeCollectionNameResolver collectionNameResolver;
    private final ObjectProvider<RerankService> rerankServiceProvider;

    public JdbcKnowledgeRetriever(JdbcTemplate jdbcTemplate,
                                  KnowledgeProperties knowledgeProperties,
                                  KnowledgeCollectionNameResolver collectionNameResolver,
                                  ObjectProvider<RerankService> rerankServiceProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.knowledgeProperties = knowledgeProperties;
        this.collectionNameResolver = collectionNameResolver;
        this.rerankServiceProvider = rerankServiceProvider;
    }

    @Override
    public List<KnowledgeChunk> retrieve(String baseCode, String question, int topK) {
        if (!knowledgeProperties.isEnabled() || question == null || question.isBlank()) {
            return List.of();
        }

        List<String> tokens = tokenize(question);
        if (tokens.isEmpty()) {
            return List.of();
        }

        try {
            List<KnowledgeChunk> candidates = queryCandidates(baseCode, tokens, topK);
            List<KnowledgeChunk> scoredCandidates = candidates.stream()
                    .map(chunk -> withScore(chunk, score(question, tokens, chunk)))
                    .sorted(Comparator.comparingDouble(KnowledgeChunk::score).reversed())
                    .toList();
            List<KnowledgeChunk> scored = scoredCandidates.stream()
                    .filter(chunk -> chunk.score() >= knowledgeProperties.getSearch().getConfidenceThreshold())
                    .toList();
            if (scored.isEmpty()) {
                scored = scoredCandidates.stream().limit(topK).toList();
            }
            return rerank(question, scored, topK);
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    private List<KnowledgeChunk> queryCandidates(String baseCode, List<String> tokens, int topK) {
        StringBuilder sql = new StringBuilder("""
                select
                    c.id as chunk_id,
                    c.doc_id as doc_id,
                    c.chunk_index as chunk_index,
                    c.content as content,
                    d.doc_name as title,
                    d.file_url as source_url,
                    kb.collection_name as collection_name,
                    kb.name as kb_name
                from rag_knowledge_chunk c
                join rag_knowledge_document d on d.id = c.doc_id and d.deleted = 0 and d.enabled = 1
                join rag_knowledge_base kb on kb.id = c.kb_id and kb.deleted = 0 and kb.status = 'ACTIVE'
                where c.deleted = 0 and c.enabled = 1
                """);
        List<Object> params = new ArrayList<>();

        appendBaseFilter(sql, params, baseCode);
        appendTokenFilter(sql, params, tokens);
        sql.append(" order by c.updated_at desc limit ?");
        params.add(Math.max(topK * Math.max(knowledgeProperties.getSearch().getTopKMultiplier(), 1), topK));

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new KnowledgeChunk(
                String.valueOf(rs.getLong("chunk_id")),
                rs.getString("collection_name"),
                String.valueOf(rs.getLong("doc_id")),
                rs.getString("title"),
                rs.getString("source_url"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                0D
        ), params.toArray());
    }

    private void appendBaseFilter(StringBuilder sql, List<Object> params, String baseCode) {
        String normalizedBaseCode = normalizeBaseCode(baseCode);
        String collectionName = collectionNameResolver.resolve(normalizedBaseCode);
        sql.append(" and (kb.collection_name = ? or kb.name = ?");
        params.add(collectionName);
        params.add(normalizedBaseCode);
        Long numericId = parseLong(normalizedBaseCode);
        if (numericId != null) {
            sql.append(" or kb.id = ?");
            params.add(numericId);
        }
        sql.append(")");
    }

    private void appendTokenFilter(StringBuilder sql, List<Object> params, List<String> tokens) {
        sql.append(" and (");
        for (int index = 0; index < tokens.size(); index++) {
            if (index > 0) {
                sql.append(" or ");
            }
            sql.append("lower(c.content) like ? or lower(d.doc_name) like ?");
            String pattern = "%" + tokens.get(index).toLowerCase(Locale.ROOT) + "%";
            params.add(pattern);
            params.add(pattern);
        }
        sql.append(")");
    }

    private List<KnowledgeChunk> rerank(String question, List<KnowledgeChunk> chunks, int topK) {
        if (chunks.isEmpty()) {
            return chunks;
        }
        RerankService rerankService = rerankServiceProvider.getIfAvailable();
        if (!knowledgeProperties.getSearch().getRerank().isEnabled() || rerankService == null) {
            return chunks.stream().limit(topK).toList();
        }
        try {
            Map<String, KnowledgeChunk> byId = new LinkedHashMap<>();
            List<RetrievedChunk> retrievedChunks = chunks.stream()
                    .peek(chunk -> byId.put(chunk.id(), chunk))
                    .map(chunk -> new RetrievedChunk(chunk.id(), chunk.content(), (float) chunk.score()))
                    .toList();
            return rerankService.rerank(question, retrievedChunks, topK).stream()
                    .map(result -> toRerankedChunk(byId.get(result.getId()), result.getScore()))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (RuntimeException ex) {
            return chunks.stream().limit(topK).toList();
        }
    }

    private KnowledgeChunk toRerankedChunk(KnowledgeChunk source, Float rerankScore) {
        if (source == null) {
            return null;
        }
        return withScore(source, rerankScore == null ? source.score() : rerankScore);
    }

    private KnowledgeChunk withScore(KnowledgeChunk chunk, double score) {
        return new KnowledgeChunk(
                chunk.id(),
                chunk.baseCode(),
                chunk.documentId(),
                chunk.title(),
                chunk.sourceUrl(),
                chunk.chunkIndex(),
                chunk.content(),
                score
        );
    }

    private double score(String question, List<String> tokens, KnowledgeChunk chunk) {
        String content = (chunk.content() == null ? "" : chunk.content()).toLowerCase(Locale.ROOT);
        String title = (chunk.title() == null ? "" : chunk.title()).toLowerCase(Locale.ROOT);
        double hits = 0D;
        for (String token : tokens) {
            String normalized = token.toLowerCase(Locale.ROOT);
            if (content.contains(normalized)) {
                hits += 1D;
            }
            if (title.contains(normalized)) {
                hits += 0.5D;
            }
        }
        double coverage = hits / Math.max(tokens.size(), 1);
        double lengthPenalty = Math.min(1D, Math.max(0.25D, 900D / Math.max(content.length(), 1)));
        double exactBonus = content.contains(question.toLowerCase(Locale.ROOT)) ? 0.2D : 0D;
        return Math.min(1D, coverage * lengthPenalty + exactBonus);
    }

    private List<String> tokenize(String question) {
        String[] parts = TOKEN_SPLITTER.split(question.trim());
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            tokens.add(part);
            if (tokens.size() >= MAX_QUERY_TOKENS) {
                break;
            }
        }
        return tokens;
    }

    private String normalizeBaseCode(String baseCode) {
        if (baseCode == null || baseCode.isBlank()) {
            return knowledgeProperties.getDefaultBaseCode();
        }
        return baseCode.trim();
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
