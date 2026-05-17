package com.personalblog.ragbackend.knowledge.service.vector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.core.chunk.VectorChunk;
import com.personalblog.ragbackend.knowledge.service.vector.model.KnowledgeVectorDocument;
import com.personalblog.ragbackend.knowledge.service.vector.model.VectorSearchHit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "pg")
public class PgVectorStoreService implements VectorStoreService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private static final String SCHEMA = "public";
    private static final String TABLE_NAME = "t_knowledge_vector";
    private final ObjectMapper objectMapper;

    public PgVectorStoreService(JdbcTemplate jdbcTemplate,
                                ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void upsert(KnowledgeVectorSpace vectorSpace, List<KnowledgeVectorDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        String table = qualifiedTable();
        String sql = """
                insert into %s (
                    kb_id,
                    doc_id,
                    chunk_id,
                    collection_name,
                    vector_id,
                    embedding_model,
                    embedding_dim,
                    content,
                    metadata,
                    embedding,
                    deleted,
                    update_time
                ) values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as vector), 0, ?)
                on conflict (collection_name, vector_id, deleted)
                do update set
                    kb_id = excluded.kb_id,
                    doc_id = excluded.doc_id,
                    chunk_id = excluded.chunk_id,
                    embedding_model = excluded.embedding_model,
                    embedding_dim = excluded.embedding_dim,
                    content = excluded.content,
                    metadata = excluded.metadata,
                    embedding = excluded.embedding,
                    update_time = excluded.update_time
                """.formatted(table);
        LocalDateTime now = LocalDateTime.now();
        for (KnowledgeVectorDocument document : documents) {
            Map<String, Object> metadata = buildMetadata(
                    document.metadata(),
                    vectorSpace.collectionName(),
                    valueOrNull(document.metadata(), "documentId", "docId", "doc_id"),
                    valueOrNull(document.metadata(), "chunkId", "chunk_id"),
                    null,
                    document.vectorId()
            );
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(sql);
                statement.setObject(1, parseLong(metadata, "knowledgeBaseId", "kbId", "kb_id"));
                statement.setObject(2, parseLong(metadata, "documentId", "docId", "doc_id"));
                statement.setObject(3, parseLong(metadata, "chunkId", "chunk_id"));
                statement.setString(4, vectorSpace.collectionName());
                statement.setString(5, document.vectorId());
                statement.setString(6, vectorSpace.embeddingModel());
                statement.setInt(7, vectorSpace.dimension());
                statement.setString(8, document.content());
                statement.setString(9, writeJson(metadata));
                statement.setString(10, toVectorLiteral(document.embedding()));
                statement.setTimestamp(11, Timestamp.valueOf(now));
                return statement;
            });
        }
    }

    @Override
    public void deleteByIds(KnowledgeVectorSpace vectorSpace, List<String> vectorIds) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return;
        }
        String placeholders = vectorIds.stream().map(ignored -> "?").reduce((left, right) -> left + ", " + right).orElse("?");
        String sql = """
                update %s
                set deleted = 1, update_time = ?
                where collection_name = ? and deleted = 0 and vector_id in (%s)
                """.formatted(qualifiedTable(), placeholders);
        Object[] params = new Object[vectorIds.size() + 2];
        params[0] = Timestamp.valueOf(LocalDateTime.now());
        params[1] = vectorSpace.collectionName();
        for (int index = 0; index < vectorIds.size(); index++) {
            params[index + 2] = vectorIds.get(index);
        }
        jdbcTemplate.update(sql, params);
    }

    @Override
    public List<VectorSearchHit> search(KnowledgeVectorSpace vectorSpace,
                                        List<Float> queryVector,
                                        int topK,
                                        int candidateLimit) {
        if (queryVector == null || queryVector.isEmpty()) {
            return List.of();
        }
        String sql = """
                select
                    vector_id,
                    content,
                    metadata::text as metadata,
                    1 - (embedding <=> cast(? as vector)) as score
                from %s
                where collection_name = ? and deleted = 0
                order by embedding <=> cast(? as vector)
                limit ?
                """.formatted(qualifiedTable());
        String vectorLiteral = toVectorLiteral(queryVector);
        jdbcTemplate.execute("set hnsw.ef_search = 200");
        return jdbcTemplate.query(sql, (rs, rowNum) -> new VectorSearchHit(
                rs.getString("vector_id"),
                rs.getDouble("score"),
                rs.getString("content"),
                parseMetadata(rs.getString("metadata"))
        ), vectorLiteral, vectorSpace.collectionName(), vectorLiteral, Math.max(topK, candidateLimit));
    }

    @Override
    public void indexDocumentChunks(String collectionName, String docId, List<VectorChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        for (VectorChunk chunk : chunks) {
            Map<String, Object> metadata = buildMetadata(
                    chunk.getMetadata(),
                    collectionName,
                    docId,
                    chunk.getChunkId(),
                    chunk.getIndex(),
                    chunk.getChunkId()
            );
            long now = Timestamp.valueOf(LocalDateTime.now()).getTime();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        insert into %s (
                            kb_id,
                            doc_id,
                            chunk_id,
                            collection_name,
                            vector_id,
                            embedding_model,
                            embedding_dim,
                            content,
                            metadata,
                            embedding,
                            deleted,
                            update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as vector), 0, ?)
                        on conflict (collection_name, vector_id, deleted)
                        do update set
                            kb_id = excluded.kb_id,
                            doc_id = excluded.doc_id,
                            chunk_id = excluded.chunk_id,
                            embedding_model = excluded.embedding_model,
                            embedding_dim = excluded.embedding_dim,
                            content = excluded.content,
                            metadata = excluded.metadata,
                            embedding = excluded.embedding,
                            update_time = excluded.update_time
                        """.formatted(qualifiedTable()));
                statement.setObject(1, parseLong(metadata, "knowledgeBaseId", "kbId", "kb_id"));
                statement.setObject(2, parseLong(metadata, "documentId", "docId", "doc_id", docId));
                statement.setObject(3, parseLong(metadata, "chunkId", "chunk_id", chunk.getChunkId()));
                statement.setString(4, collectionName);
                statement.setString(5, chunk.getChunkId());
                statement.setString(6, String.valueOf(metadata.getOrDefault("embeddingModel", "")));
                statement.setInt(7, chunk.getEmbedding() == null ? 0 : chunk.getEmbedding().length);
                statement.setString(8, chunk.getContent());
                statement.setString(9, writeJson(metadata));
                statement.setString(10, toVectorLiteral(chunk.getEmbedding()));
                statement.setTimestamp(11, new Timestamp(now));
                return statement;
            });
        }
    }

    @Override
    public void updateChunk(String collectionName, String docId, VectorChunk chunk) {
        indexDocumentChunks(collectionName, docId, List.of(chunk));
    }

    @Override
    public void deleteDocumentVectors(String collectionName, String docId) {
        if (!StringUtils.hasText(docId)) {
            return;
        }
        String sql = """
                update %s
                set deleted = 1, update_time = ?
                where collection_name = ? and deleted = 0 and doc_id = ?
                """.formatted(qualifiedTable());
        jdbcTemplate.update(sql, Timestamp.valueOf(LocalDateTime.now()), collectionName, parseLong(docId));
    }

    @Override
    public void deleteChunkById(String collectionName, String chunkId) {
        if (!StringUtils.hasText(chunkId)) {
            return;
        }
        String sql = """
                update %s
                set deleted = 1, update_time = ?
                where collection_name = ? and deleted = 0 and vector_id = ?
                """.formatted(qualifiedTable());
        jdbcTemplate.update(sql, Timestamp.valueOf(LocalDateTime.now()), collectionName, chunkId);
    }

    @Override
    public void deleteChunksByIds(String collectionName, List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }
        String placeholders = chunkIds.stream().map(ignored -> "?").reduce((left, right) -> left + ", " + right).orElse("?");
        String sql = """
                update %s
                set deleted = 1, update_time = ?
                where collection_name = ? and deleted = 0 and vector_id in (%s)
                """.formatted(qualifiedTable(), placeholders);
        Object[] params = new Object[chunkIds.size() + 2];
        params[0] = Timestamp.valueOf(LocalDateTime.now());
        params[1] = collectionName;
        for (int index = 0; index < chunkIds.size(); index++) {
            params[index + 2] = chunkIds.get(index);
        }
        jdbcTemplate.update(sql, params);
    }

    private String qualifiedTable() {
        return identifier(SCHEMA)
                + "."
                + identifier(TABLE_NAME);
    }

    private String identifier(String value) {
        if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid PostgreSQL identifier: " + value);
        }
        return value;
    }

    private String toVectorLiteral(List<Float> embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < embedding.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(embedding.get(index));
        }
        builder.append(']');
        return builder.toString();
    }

    private String toVectorLiteral(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(embedding[index]);
        }
        builder.append(']');
        return builder.toString();
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize vector metadata", exception);
        }
    }

    private Map<String, Object> buildMetadata(Map<String, Object> source,
                                              String collectionName,
                                              Object docId,
                                              Object chunkId,
                                              Integer chunkIndex,
                                              String vectorId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (source != null && !source.isEmpty()) {
            metadata.putAll(source);
        }
        putIfAbsent(metadata, "collection_name", collectionName);
        putIfAbsent(metadata, "collectionName", collectionName);
        putIfAbsent(metadata, "doc_id", docId);
        putIfAbsent(metadata, "docId", docId);
        putIfAbsent(metadata, "documentId", docId);
        putIfAbsent(metadata, "chunk_id", chunkId);
        putIfAbsent(metadata, "chunkId", chunkId);
        putIfAbsent(metadata, "vector_id", vectorId);
        putIfAbsent(metadata, "vectorId", vectorId);
        if (chunkIndex != null) {
            putIfAbsent(metadata, "chunk_index", chunkIndex);
            putIfAbsent(metadata, "chunkIndex", chunkIndex);
        }
        return metadata;
    }

    private void putIfAbsent(Map<String, Object> metadata, String key, Object value) {
        if (metadata == null || key == null || value == null) {
            return;
        }
        if (!metadata.containsKey(key) || metadata.get(key) == null || String.valueOf(metadata.get(key)).isBlank()) {
            metadata.put(key, value);
        }
    }

    private Map<String, Object> parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadata, MAP_TYPE);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private Long parseLong(Map<String, Object> metadata, String... keys) {
        Object value = valueOrNull(metadata, keys);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Object valueOrNull(Map<String, Object> metadata, String... keys) {
        if (metadata == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            Object value = metadata.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return null;
    }
}
