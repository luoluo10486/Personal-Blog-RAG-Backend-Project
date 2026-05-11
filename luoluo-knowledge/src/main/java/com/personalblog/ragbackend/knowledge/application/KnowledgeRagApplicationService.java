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
import com.personalblog.ragbackend.knowledge.service.rag.pipeline.RagQueryPipeline;
import com.personalblog.ragbackend.knowledge.service.rag.pipeline.RagQueryPlan;
import com.personalblog.ragbackend.knowledge.service.retrieval.KnowledgeRetrievalEngine;
import com.personalblog.ragbackend.knowledge.service.retrieval.RetrieveRequest;
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
    private final RagConversationService ragConversationService;
    private final RagQueryPipeline ragQueryPipeline;

    public KnowledgeRagApplicationService(
            KnowledgeProperties knowledgeProperties,
            KnowledgeVectorSpaceResolver vectorSpaceResolver,
            KnowledgeRetrievalEngine knowledgeRetrievalEngine,
            KnowledgeAnswerGenerator answerGenerator,
            RagConversationService ragConversationService,
            RagQueryPipeline ragQueryPipeline
    ) {
        this.knowledgeProperties = knowledgeProperties;
        this.vectorSpaceResolver = vectorSpaceResolver;
        this.knowledgeRetrievalEngine = knowledgeRetrievalEngine;
        this.answerGenerator = answerGenerator;
        this.ragConversationService = ragConversationService;
        this.ragQueryPipeline = ragQueryPipeline;
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
        List<ChatMessage> memory = ragConversationService.loadMemory(request.conversationId());
        steps.add("memory:" + memory.size());
        RagQueryPlan plan = ragQueryPipeline.prepare(baseCode, request.question(), memory);
        steps.addAll(plan.steps());

        if (plan.guidanceDecision() != null && plan.guidanceDecision().prompt()) {
            KnowledgeTrace trace = new KnowledgeTrace(
                    RagTraceContext.getTraceId(),
                    request.conversationId(),
                    "knowledge-rag",
                    vectorSpace.vectorType(),
                    vectorSpace.collectionName(),
                    plan.topK(),
                    plan.originalQuestion(),
                    plan.rewrittenQuestion(),
                    steps
            );
            return new KnowledgeAskResponse(plan.directAnswer(), baseCode, List.of(), trace);
        }

        String effectiveBaseCode = normalizeBaseCode(plan.baseCode());
        int effectiveTopK = plan.topK() > 0 ? plan.topK() : topK;
        if (plan.hasDirectAnswer()) {
            if (request.conversationId() != null && !request.conversationId().isBlank()) {
                ragConversationService.persistExchange(
                        request.conversationId(),
                        request.question(),
                        plan.directAnswer(),
                        effectiveBaseCode,
                        0
                );
            }
            KnowledgeTrace trace = new KnowledgeTrace(
                    RagTraceContext.getTraceId(),
                    request.conversationId(),
                    "knowledge-rag",
                    vectorSpace.vectorType(),
                    vectorSpace.collectionName(),
                    effectiveTopK,
                    plan.originalQuestion(),
                    plan.rewrittenQuestion(),
                    steps
            );
            return new KnowledgeAskResponse(plan.directAnswer(), effectiveBaseCode, List.of(), trace);
        }
        stopWatch.start("retrieve");
        List<KnowledgeChunk> chunks = knowledgeRetrievalEngine.retrieve(
                new RetrieveRequest(effectiveBaseCode, plan.rewrittenQuestion(), effectiveTopK)
        );
        stopWatch.stop();
        steps.add("retrieve:" + stopWatch.lastTaskInfo().getTimeMillis() + "ms");

        stopWatch.start("generate");
        String answer = plan.hasDirectAnswer()
                ? plan.directAnswer()
                : answerGenerator.generate(plan.rewrittenQuestion(), memory, chunks);
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

        if (!plan.hasDirectAnswer()) {
            ragConversationService.persistExchange(
                    request.conversationId(),
                    request.question(),
                    answer,
                    effectiveBaseCode,
                    citations.size()
            );
        }

        KnowledgeTrace trace = new KnowledgeTrace(
                RagTraceContext.getTraceId(),
                request.conversationId(),
                "knowledge-rag",
                vectorSpace.vectorType(),
                vectorSpace.collectionName(),
                effectiveTopK,
                plan.originalQuestion(),
                plan.rewrittenQuestion(),
                steps
        );
        return new KnowledgeAskResponse(answer, effectiveBaseCode, citations, trace);
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
