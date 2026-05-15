package com.personalblog.ragbackend.rag.core.retrieve.channel;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalblog.ragbackend.infra.convention.RetrievedChunk;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeBaseEntity;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeBaseMapper;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceResolver;
import com.personalblog.ragbackend.rag.config.SearchChannelProperties;
import com.personalblog.ragbackend.rag.core.intent.NodeScore;
import com.personalblog.ragbackend.rag.core.retrieve.RetrieverService;
import com.personalblog.ragbackend.rag.core.retrieve.channel.strategy.CollectionParallelRetriever;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

@Component
public class VectorGlobalSearchChannel implements SearchChannel {
    private final SearchChannelProperties properties;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeVectorSpaceResolver knowledgeVectorSpaceResolver;
    private final CollectionParallelRetriever parallelRetriever;

    public VectorGlobalSearchChannel(RetrieverService retrieverService,
                                     SearchChannelProperties properties,
                                     KnowledgeBaseMapper knowledgeBaseMapper,
                                     KnowledgeVectorSpaceResolver knowledgeVectorSpaceResolver,
                                     @Qualifier("ragContextThreadPoolExecutor") Executor executor) {
        this.properties = properties;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeVectorSpaceResolver = knowledgeVectorSpaceResolver;
        this.parallelRetriever = new CollectionParallelRetriever(retrieverService, executor);
    }

    @Override
    public String getName() {
        return "VectorGlobalSearch";
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        if (!properties.getChannels().getVectorGlobal().isEnabled()) {
            return false;
        }
        List<NodeScore> allScores = context.getIntents() == null ? List.of() : context.getIntents().stream()
                .flatMap(subQuestionIntent -> subQuestionIntent.nodeScores().stream())
                .toList();
        if (CollUtil.isEmpty(allScores)) {
            return true;
        }

        double maxScore = allScores.stream().mapToDouble(NodeScore::score).max().orElse(0D);
        double confidenceThreshold = properties.getChannels().getVectorGlobal().getConfidenceThreshold();
        if (maxScore < confidenceThreshold) {
            return true;
        }

        double supplementThreshold = properties.getChannels().getVectorGlobal().getSingleIntentSupplementThreshold();
        return allScores.size() == 1 && maxScore < supplementThreshold;
    }

    @Override
    public SearchChannelResult search(SearchContext context) {
        long startTime = System.currentTimeMillis();
        try {
            List<String> collections = resolveCollections(context);
            if (collections.isEmpty()) {
                return SearchChannelResult.builder()
                        .channelType(getType())
                        .channelName(getName())
                        .chunks(List.of())
                        .latencyMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            int topK = Math.max(context.getTopK(), 1) * Math.max(properties.getChannels().getVectorGlobal().getTopKMultiplier(), 1);
            List<RetrievedChunk> chunks = parallelRetriever.executeParallelRetrieval(context.getMainQuestion(), collections, topK);
            return SearchChannelResult.builder()
                    .channelType(getType())
                    .channelName(getName())
                    .chunks(chunks)
                    .latencyMs(System.currentTimeMillis() - startTime)
                    .metadata(java.util.Map.of("collectionCount", collections.size()))
                    .build();
        } catch (Exception exception) {
            return SearchChannelResult.builder()
                    .channelType(getType())
                    .channelName(getName())
                    .chunks(List.of())
                    .latencyMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    @Override
    public SearchChannelType getType() {
        return SearchChannelType.VECTOR_GLOBAL;
    }

    private List<String> resolveCollections(SearchContext context) {
        String baseCode = firstNotBlank(context.getMetadataString("baseCode"), context.getMetadataString("collectionName"));
        if (StrUtil.isNotBlank(baseCode)) {
            String collectionName = knowledgeVectorSpaceResolver.resolve(baseCode).collectionName();
            return StrUtil.isNotBlank(collectionName) ? List.of(collectionName) : List.of();
        }

        Set<String> collections = new HashSet<>();
        List<KnowledgeBaseEntity> knowledgeBases = knowledgeBaseMapper.selectList(
                Wrappers.lambdaQuery(KnowledgeBaseEntity.class)
                        .select(KnowledgeBaseEntity::getCollectionName)
                        .eq(KnowledgeBaseEntity::getDeleted, 0)
                        .eq(KnowledgeBaseEntity::getStatus, "ACTIVE")
        );
        for (KnowledgeBaseEntity knowledgeBase : knowledgeBases) {
            if (knowledgeBase != null && StrUtil.isNotBlank(knowledgeBase.getCollectionName())) {
                collections.add(knowledgeBase.getCollectionName());
            }
        }
        return new ArrayList<>(collections);
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
