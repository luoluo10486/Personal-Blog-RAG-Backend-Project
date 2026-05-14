package com.personalblog.ragbackend.rag.core.retrieve;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceResolver;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@ConditionalOnBean(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "app.knowledge.jdbc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JdbcKnowledgeRetriever implements KnowledgeRetriever, KnowledgeCandidateRetriever {
    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[^\\p{IsHan}a-zA-Z0-9]+");
    private static final int MAX_QUERY_TOKENS = 8;

    private final JdbcTemplate jdbcTemplate;
    private final KnowledgeProperties knowledgeProperties;
    private final KnowledgeVectorSpaceResolver vectorSpaceResolver;

    public JdbcKnowledgeRetriever(JdbcTemplate jdbcTemplate,
                                  KnowledgeProperties knowledgeProperties,
                                  KnowledgeVectorSpaceResolver vectorSpaceResolver) {
        this.jdbcTemplate = jdbcTemplate;
        this.knowledgeProperties = knowledgeProperties;
        this.vectorSpaceResolver = vectorSpaceResolver;
    }

    @Override
    public String getName() {
        return "jdbc";
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public boolean isEnabled(RetrieveRequest request) {
        return knowledgeProperties.isEnabled();
    }

    @Override
    public List<RetrievedChunk> retrieve(String baseCode, String question, int topK) {
        return retrieveCandidates(new RetrieveRequest(baseCode, question, topK));
    }

    @Override
    @RagTraceNode(name = "jdbc-retriever", type = "RETRIEVE_CHANNEL")
    public List<RetrievedChunk> retrieveCandidates(RetrieveRequest request) {
        String baseCode = request.baseCode();
        String question = request.question();
        int topK = request.topK();
        if (!knowledgeProperties.isEnabled() || question == null || question.isBlank()) {
            return List.of();
        }

        List<String> tokens = tokenize(question);
        if (tokens.isEmpty()) {
            return List.of();
        }

        try {
            List<RetrievedChunk> candidates = queryCandidates(baseCode, tokens, topK);
            return candidates.stream()
                    .map((RetrievedChunk chunk) -> withScore(chunk, score(question, tokens, chunk)))
                    .sorted(Comparator.comparingDouble((RetrievedChunk chunk) -> chunk.getScore() == null ? 0D : chunk.getScore()).reversed())
                    .toList();
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    private List<RetrievedChunk> queryCandidates(String baseCode, List<String> tokens, int topK) {
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
                from t_knowledge_chunk c
                join t_knowledge_document d on d.id = c.doc_id and d.deleted = 0 and d.enabled = 1
                join t_knowledge_base kb on kb.id = c.kb_id and kb.deleted = 0 and kb.status = 'ACTIVE'
                where c.deleted = 0 and c.enabled = 1
                """);
        List<Object> params = new ArrayList<>();

        appendBaseFilter(sql, params, baseCode);
        appendTokenFilter(sql, params, tokens);
        sql.append(" order by c.update_time desc limit ?");
        params.add(Math.max(topK * Math.max(knowledgeProperties.getSearch().getTopKMultiplier(), 1), topK));

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> RetrievedChunk.builder()
                .id(String.valueOf(rs.getLong("chunk_id")))
                .text(rs.getString("content"))
                .score(0F)
                .build(), params.toArray());
    }

    private void appendBaseFilter(StringBuilder sql, List<Object> params, String baseCode) {
        String normalizedBaseCode = normalizeBaseCode(baseCode);
        String collectionName = vectorSpaceResolver.resolve(normalizedBaseCode).collectionName();
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

    private RetrievedChunk withScore(RetrievedChunk chunk, double score) {
        return RetrievedChunk.builder()
                .id(chunk.getId())
                .text(chunk.getText())
                .score((float) score)
                .build();
    }

    private double score(String question, List<String> tokens, RetrievedChunk chunk) {
        String content = (chunk.getText() == null ? "" : chunk.getText()).toLowerCase(Locale.ROOT);
        double hits = 0D;
        for (String token : tokens) {
            String normalized = token.toLowerCase(Locale.ROOT);
            if (content.contains(normalized)) {
                hits += 1D;
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
