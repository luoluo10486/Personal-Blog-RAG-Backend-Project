package com.personalblog.ragbackend.knowledge.application;

import com.personalblog.ragbackend.infra.ai.convention.ChatMessage;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeAskRequest;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeAskResponse;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeCitation;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeHealthResponse;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeQueryRewriteResult;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeTrace;
import com.personalblog.ragbackend.knowledge.service.generation.KnowledgeAnswerGenerator;
import com.personalblog.ragbackend.knowledge.service.rag.RagConversationService;
import com.personalblog.ragbackend.knowledge.service.retrieval.KnowledgeRetrievalEngine;
import com.personalblog.ragbackend.knowledge.service.retrieval.RetrieveRequest;
import com.personalblog.ragbackend.knowledge.service.rewrite.QueryTermRewriteService;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpace;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceResolver;
import com.personalblog.ragbackend.knowledge.trace.RagTraceContext;
import com.personalblog.ragbackend.knowledge.trace.RagTraceRoot;
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
    private final QueryTermRewriteService queryTermRewriteService;
    private final RagConversationService ragConversationService;

    public KnowledgeRagApplicationService(
            KnowledgeProperties knowledgeProperties,
            KnowledgeVectorSpaceResolver vectorSpaceResolver,
            KnowledgeRetrievalEngine knowledgeRetrievalEngine,
            KnowledgeAnswerGenerator answerGenerator,
            QueryTermRewriteService queryTermRewriteService,
            RagConversationService ragConversationService
    ) {
        this.knowledgeProperties = knowledgeProperties;
        this.vectorSpaceResolver = vectorSpaceResolver;
        this.knowledgeRetrievalEngine = knowledgeRetrievalEngine;
        this.answerGenerator = answerGenerator;
        this.queryTermRewriteService = queryTermRewriteService;
        this.ragConversationService = ragConversationService;
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

    @RagTraceRoot(name = "knowledge-ask")
    public KnowledgeAskResponse ask(KnowledgeAskRequest request) {
        StopWatch stopWatch = new StopWatch("knowledge-rag");
        List<String> steps = new ArrayList<>();
        String baseCode = normalizeBaseCode(request.baseCode());
        steps.add("normalize-request");
        int topK = normalizeTopK(request.topK());
        KnowledgeVectorSpace vectorSpace = vectorSpaceResolver.resolve(baseCode);
        steps.add("resolve-collection");
        KnowledgeQueryRewriteResult rewriteResult = queryTermRewriteService.rewrite(request.question(), baseCode);
        steps.add("rewrite:" + rewriteResult.appliedMappings().size());
        List<ChatMessage> memory = ragConversationService.loadMemory(request.conversationId());
        steps.add("memory:" + memory.size());

        stopWatch.start("retrieve");
        List<KnowledgeChunk> chunks = knowledgeRetrievalEngine.retrieve(
                new RetrieveRequest(baseCode, rewriteResult.rewrittenQuestion(), topK)
        );
        stopWatch.stop();
        steps.add("retrieve:" + stopWatch.lastTaskInfo().getTimeMillis() + "ms");

        stopWatch.start("generate");
        String answer = answerGenerator.generate(rewriteResult.rewrittenQuestion(), memory, chunks);
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

        ragConversationService.persistExchange(
                request.conversationId(),
                request.question(),
                answer,
                baseCode,
                citations.size()
        );

        KnowledgeTrace trace = new KnowledgeTrace(
                RagTraceContext.getTraceId(),
                request.conversationId(),
                "knowledge-rag",
                vectorSpace.vectorType(),
                vectorSpace.collectionName(),
                topK,
                rewriteResult.originalQuestion(),
                rewriteResult.rewrittenQuestion(),
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
