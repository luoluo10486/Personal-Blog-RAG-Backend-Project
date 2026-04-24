package com.personalblog.ragbackend.knowledge.application;

import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeAskRequest;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeAskResponse;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeCitation;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeHealthResponse;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeTrace;
import com.personalblog.ragbackend.knowledge.service.generation.KnowledgeAnswerGenerator;
import com.personalblog.ragbackend.knowledge.service.retrieval.KnowledgeRetriever;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeCollectionNameResolver;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeRagApplicationService {
    private final KnowledgeProperties knowledgeProperties;
    private final KnowledgeCollectionNameResolver collectionNameResolver;
    private final KnowledgeRetriever knowledgeRetriever;
    private final KnowledgeAnswerGenerator answerGenerator;

    public KnowledgeRagApplicationService(
            KnowledgeProperties knowledgeProperties,
            KnowledgeCollectionNameResolver collectionNameResolver,
            KnowledgeRetriever knowledgeRetriever,
            KnowledgeAnswerGenerator answerGenerator
    ) {
        this.knowledgeProperties = knowledgeProperties;
        this.collectionNameResolver = collectionNameResolver;
        this.knowledgeRetriever = knowledgeRetriever;
        this.answerGenerator = answerGenerator;
    }

    public KnowledgeHealthResponse health() {
        return new KnowledgeHealthResponse(
                knowledgeProperties.isEnabled(),
                knowledgeProperties.getDefaultBaseCode(),
                knowledgeProperties.getVector().getType(),
                knowledgeProperties.getDefaults().getCollectionName(),
                knowledgeProperties.getDefaults().getEmbeddingModel(),
                knowledgeProperties.getDefaults().getChatModel()
        );
    }

    public KnowledgeAskResponse ask(KnowledgeAskRequest request) {
        String baseCode = normalizeBaseCode(request.baseCode());
        int topK = normalizeTopK(request.topK());
        String collectionName = collectionNameResolver.resolve(baseCode);
        List<KnowledgeChunk> chunks = knowledgeRetriever.retrieve(baseCode, request.question(), topK);
        String answer = answerGenerator.generate(request.question(), chunks);
        List<KnowledgeCitation> citations = chunks.stream()
                .map(chunk -> new KnowledgeCitation(
                        chunk.documentId(),
                        chunk.title(),
                        chunk.sourceUrl(),
                        chunk.chunkIndex(),
                        chunk.score(),
                        chunk.content()
                ))
                .toList();
        KnowledgeTrace trace = new KnowledgeTrace(
                "knowledge-rag",
                knowledgeProperties.getVector().getType(),
                collectionName,
                topK,
                List.of("normalize-request", "resolve-collection", "retrieve", "generate")
        );
        return new KnowledgeAskResponse(answer, baseCode, citations, trace);
    }

    private String normalizeBaseCode(String baseCode) {
        if (baseCode == null || baseCode.isBlank()) {
            return knowledgeProperties.getDefaultBaseCode();
        }
        return baseCode.trim();
    }

    private int normalizeTopK(Integer topK) {
        if (topK == null || topK <= 0) {
            return knowledgeProperties.getSearch().getTopK();
        }
        return topK;
    }
}
