package com.personalblog.ragbackend.rag.core.retrieve;

import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.infra.embedding.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnExpression("'${rag.vector.type:pg}'.toLowerCase() == 'pgvector' or '${rag.vector.type:pg}'.toLowerCase() == 'pg'")
public class PgRetrieverService implements RetrieverService {
    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;

    @Override
    public List<RetrievedChunk> retrieve(RetrieveRequest request) {
        List<Float> embedding = embeddingService.embed(request.getQuery());
        float[] vector = normalize(toArray(embedding));
        return retrieveByVector(vector, request);
    }

    @Override
    public List<RetrievedChunk> retrieveByVector(float[] vector, RetrieveRequest request) {
        jdbcTemplate.execute("SET hnsw.ef_search = 200");
        String vectorLiteral = toVectorLiteral(vector);
        return jdbcTemplate.query(
                "SELECT id, content, 1 - (embedding <=> ?::vector) AS score FROM t_knowledge_vector WHERE collection_name = ? AND deleted = 0 ORDER BY embedding <=> ?::vector LIMIT ?",
                (rs, rowNum) -> RetrievedChunk.builder()
                        .id(String.valueOf(rs.getLong("id")))
                        .text(rs.getString("content"))
                        .score(rs.getFloat("score"))
                        .build(),
                vectorLiteral,
                request.getCollectionName(),
                vectorLiteral,
                request.getTopK()
        );
    }

    private float[] normalize(float[] vector) {
        float norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
        return vector;
    }

    private float[] toArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(embedding[i]);
        }
        return sb.append("]").toString();
    }
}
