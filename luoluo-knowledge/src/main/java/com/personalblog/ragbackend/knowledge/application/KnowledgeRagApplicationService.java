package com.personalblog.ragbackend.knowledge.application;

import com.personalblog.ragbackend.common.context.LoginUser;
import com.personalblog.ragbackend.infra.ai.convention.ChatMessage;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.knowledge.domain.KnowledgeChunk;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeAskRequest;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeAskResponse;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeCitation;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeHealthResponse;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeTrace;
import com.personalblog.ragbackend.knowledge.service.generation.KnowledgeAnswerGenerator;
import com.personalblog.ragbackend.knowledge.service.generation.TemplateKnowledgeAnswerGenerator;
import com.personalblog.ragbackend.knowledge.service.rag.ConversationPersistResult;
import com.personalblog.ragbackend.knowledge.service.rag.PreparedRagAnswer;
import com.personalblog.ragbackend.knowledge.service.rag.RagConversationService;
import com.personalblog.ragbackend.knowledge.service.rag.intent.NodeScore;
import com.personalblog.ragbackend.knowledge.service.rag.mcp.McpToolOrchestrator;
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
    private final McpToolOrchestrator mcpToolOrchestrator;

    public KnowledgeRagApplicationService(
            KnowledgeProperties knowledgeProperties,
            KnowledgeVectorSpaceResolver vectorSpaceResolver,
            KnowledgeRetrievalEngine knowledgeRetrievalEngine,
            KnowledgeAnswerGenerator answerGenerator,
            RagConversationService ragConversationService,
            RagQueryPipeline ragQueryPipeline,
            McpToolOrchestrator mcpToolOrchestrator
    ) {
        this.knowledgeProperties = knowledgeProperties;
        this.vectorSpaceResolver = vectorSpaceResolver;
        this.knowledgeRetrievalEngine = knowledgeRetrievalEngine;
        this.answerGenerator = answerGenerator;
        this.ragConversationService = ragConversationService;
        this.ragQueryPipeline = ragQueryPipeline;
        this.mcpToolOrchestrator = mcpToolOrchestrator;
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
        PreparedRagAnswer prepared = prepare(request);
        if (prepared.hasDirectAnswer()) {
            ConversationPersistResult persistResult = shouldPersistDirectAnswer(prepared)
                    ? ragConversationService.persistExchange(
                    request.conversationId(),
                    request.question(),
                    prepared.directAnswer(),
                    prepared.baseCode(),
                    prepared.citations().size()
            )
                    : new ConversationPersistResult(null, null);
            return new KnowledgeAskResponse(
                    prepared.directAnswer(),
                    prepared.baseCode(),
                    prepared.citations(),
                    prepared.trace(),
                    persistResult.assistantMessageId(),
                    persistResult.conversationTitle()
            );
        }

        String answer = generateAnswer(prepared, Boolean.TRUE.equals(request.deepThinking()));
        ConversationPersistResult persistResult = ragConversationService.persistExchange(
                request.conversationId(),
                request.question(),
                answer,
                prepared.baseCode(),
                prepared.citations().size()
        );
        List<String> steps = new ArrayList<>(prepared.trace().steps());
        steps.add("generate");
        KnowledgeTrace trace = new KnowledgeTrace(
                prepared.trace().traceId(),
                prepared.trace().conversationId(),
                prepared.trace().route(),
                prepared.trace().vectorType(),
                prepared.trace().collectionName(),
                prepared.trace().requestedTopK(),
                prepared.trace().question(),
                prepared.trace().rewrittenQuestion(),
                steps
        );
        return new KnowledgeAskResponse(
                answer,
                prepared.baseCode(),
                prepared.citations(),
                trace,
                persistResult.assistantMessageId(),
                persistResult.conversationTitle()
        );
    }

    public String generateAnswer(PreparedRagAnswer prepared, boolean deepThinking) {
        return doGenerateAnswer(prepared, deepThinking);
    }

    public PreparedRagAnswer prepare(KnowledgeAskRequest request) {
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
            return new PreparedRagAnswer(
                    request.question(),
                    plan.rewrittenQuestion(),
                    baseCode,
                    plan.topK(),
                    memory,
                    plan,
                    plan.intentGroup(),
                    List.of(),
                    List.of(),
                    "",
                    trace
            );
        }

        String effectiveBaseCode = normalizeBaseCode(plan.baseCode());
        int effectiveTopK = plan.topK() > 0 ? plan.topK() : topK;
        if (plan.hasDirectAnswer()) {
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
            return new PreparedRagAnswer(
                    request.question(),
                    plan.rewrittenQuestion(),
                    effectiveBaseCode,
                    effectiveTopK,
                    memory,
                    plan,
                    plan.intentGroup(),
                    List.of(),
                    List.of(),
                    "",
                    trace
            );
        }

        stopWatch.start("retrieve");
        List<KnowledgeChunk> chunks = shouldRetrieveKnowledge(plan)
                ? knowledgeRetrievalEngine.retrieve(new RetrieveRequest(effectiveBaseCode, plan.rewrittenQuestion(), effectiveTopK))
                : List.of();
        stopWatch.stop();
        steps.add("retrieve:" + stopWatch.lastTaskInfo().getTimeMillis() + "ms");

        stopWatch.start("mcp");
        String mcpContext = mcpToolOrchestrator.executeAndFormat(
                plan.subIntents(),
                effectiveBaseCode,
                effectiveTopK,
                request.conversationId()
        );
        stopWatch.stop();
        steps.add("mcp:" + stopWatch.lastTaskInfo().getTimeMillis() + "ms");

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
        return new PreparedRagAnswer(
                request.question(),
                plan.rewrittenQuestion(),
                effectiveBaseCode,
                effectiveTopK,
                memory,
                plan,
                plan.intentGroup(),
                chunks,
                citations,
                mcpContext,
                trace
        );
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

    private boolean shouldRetrieveKnowledge(RagQueryPlan plan) {
        return plan.intentGroup() == null
                || plan.intentGroup().kbIntents() == null
                || !plan.intentGroup().kbIntents().isEmpty();
    }

    private boolean shouldPersistDirectAnswer(PreparedRagAnswer prepared) {
        return prepared != null
                && prepared.plan() != null
                && prepared.plan().guidanceDecision() != null
                && !prepared.plan().guidanceDecision().prompt();
    }

    private String doGenerateAnswer(PreparedRagAnswer prepared, boolean deepThinking) {
        if (prepared.hasDirectAnswer()) {
            return prepared.directAnswer();
        }
        if (answerGenerator instanceof TemplateKnowledgeAnswerGenerator templateGenerator) {
            List<NodeScore> kbIntents = prepared.intentGroup() == null ? List.of() : prepared.intentGroup().kbIntents();
            List<NodeScore> mcpIntents = prepared.intentGroup() == null ? List.of() : prepared.intentGroup().mcpIntents();
            return templateGenerator.generate(
                    prepared.rewrittenQuestion(),
                    prepared.memory(),
                    prepared.chunks(),
                    prepared.mcpContext(),
                    kbIntents,
                    mcpIntents,
                    deepThinking
            );
        }
        return answerGenerator.generate(prepared.rewrittenQuestion(), prepared.memory(), prepared.chunks(), prepared.mcpContext());
    }
}
