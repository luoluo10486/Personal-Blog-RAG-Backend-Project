package com.personalblog.ragbackend.knowledge.application;

import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeAskRequest;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeAskResponse;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeCitation;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeHealthResponse;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeTrace;
import com.personalblog.ragbackend.knowledge.service.generation.KnowledgeAnswerGenerator;
import com.personalblog.ragbackend.knowledge.service.retrieval.KnowledgeRetrievalEngine;
import com.personalblog.ragbackend.knowledge.service.retrieval.RetrieveRequest;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpace;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeRagApplicationService {
    private final KnowledgeProperties knowledgeProperties;
    private final KnowledgeVectorSpaceResolver vectorSpaceResolver;
    private final KnowledgeRetrievalEngine knowledgeRetrievalEngine;
    private final KnowledgeAnswerGenerator answerGenerator;

    public KnowledgeRagApplicationService(
            KnowledgeProperties knowledgeProperties,
            KnowledgeVectorSpaceResolver vectorSpaceResolver,
            KnowledgeRetrievalEngine knowledgeRetrievalEngine,
            KnowledgeAnswerGenerator answerGenerator
    ) {
        this.knowledgeProperties = knowledgeProperties;
        this.vectorSpaceResolver = vectorSpaceResolver;
        this.knowledgeRetrievalEngine = knowledgeRetrievalEngine;
        this.answerGenerator = answerGenerator;
    }

    public KnowledgeHealthResponse health() {
        KnowledgeVectorSpace vectorSpace = vectorSpaceResolver.resolve(knowledgeProperties.getDefaultBaseCode());
        return new KnowledgeHealthResponse(
                knowledgeProperties.isEnabled(),
                knowledgeProperties.getDefaultBaseCode(),
                vectorSpace.vectorType(),
                vectorSpace.collectionName(),
                vectorSpace.embeddingModel(),
                knowledgeProperties.getDefaults().getChatModel()
        );
    }

    public KnowledgeAskResponse ask(KnowledgeAskRequest request) {
        StopWatch stopWatch = new StopWatch("knowledge-rag");
        List<String> steps = new ArrayList<>();
        String baseCode = normalizeBaseCode(request.baseCode());
        steps.add("normalize-request");
        int topK = normalizeTopK(request.topK());
        KnowledgeVectorSpace vectorSpace = vectorSpaceResolver.resolve(baseCode);
        steps.add("resolve-collection");
        stopWatch.start("retrieve");
        List<KnowledgeChunk> chunks = knowledgeRetrievalEngine.retrieve(new RetrieveRequest(baseCode, request.question(), topK));
        stopWatch.stop();
        steps.add("retrieve:" + stopWatch.lastTaskInfo().getTimeMillis() + "ms");
        stopWatch.start("generate");
        String answer = answerGenerator.generate(request.question(), chunks);
        stopWatch.stop();
        steps.add("generate:" + stopWatch.lastTaskInfo().getTimeMillis() + "ms");
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
                vectorSpace.vectorType(),
                vectorSpace.collectionName(),
                topK,
                steps
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
