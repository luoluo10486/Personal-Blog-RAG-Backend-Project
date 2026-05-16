package com.personalblog.ragbackend.knowledge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeBaseCreateRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeBasePageRequest;
import com.personalblog.ragbackend.knowledge.controller.request.KnowledgeBaseUpdateRequest;
import com.personalblog.ragbackend.knowledge.controller.vo.KnowledgeBaseVO;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeBaseEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentEntity;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeBaseMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentMapper;
import com.personalblog.ragbackend.knowledge.service.KnowledgeBaseService;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpace;
import com.personalblog.ragbackend.knowledge.service.vector.KnowledgeVectorSpaceId;
import com.personalblog.ragbackend.knowledge.service.vector.VectorStoreAdmin;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final VectorStoreAdmin vectorStoreAdmin;
    private final S3Client s3Client;

    public KnowledgeBaseServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper,
                                    KnowledgeDocumentMapper knowledgeDocumentMapper,
                                    VectorStoreAdmin vectorStoreAdmin,
                                    S3Client s3Client) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.vectorStoreAdmin = vectorStoreAdmin;
        this.s3Client = s3Client;
    }

    @Override
    @Transactional
    public String create(KnowledgeBaseCreateRequest requestParam) {
        String name = requireText(requestParam.getName(), "knowledge base name is required");
        String normalizedName = name.replaceAll("\\s+", "");
        long nameCount = knowledgeBaseMapper.selectCount(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getName, normalizedName)
                .eq(KnowledgeBaseEntity::getDeleted, 0));
        if (nameCount > 0) {
            throw new IllegalArgumentException("knowledge base name already exists");
        }
        String collectionName = requireText(requestParam.getCollectionName(), "collection name is required");
        assertCollectionAvailable(collectionName, null);

        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setName(requestParam.getName());
        entity.setEmbeddingModel(StringUtils.hasText(requestParam.getEmbeddingModel())
                ? requestParam.getEmbeddingModel().trim()
                : "Qwen/Qwen3-Embedding-8B");
        entity.setCollectionName(collectionName);
        entity.setOwnerUserId(parseUserId(UserContext.getUserId()));
        entity.setCreatedBy(parseUserId(UserContext.getUserId()));
        entity.setUpdatedBy(parseUserId(UserContext.getUserId()));
        entity.setDeleted(0);
        knowledgeBaseMapper.insert(entity);
        try {
            s3Client.createBucket(builder -> builder.bucket(collectionName));
        } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException exception) {
            throw new IllegalArgumentException("collection name already exists");
        }
        vectorStoreAdmin.ensureVectorSpace(new KnowledgeVectorSpace(
                new KnowledgeVectorSpaceId(collectionName, "public"),
                collectionName,
                "pg",
                entity.getEmbeddingModel(),
                1536
        ));
        return String.valueOf(entity.getId());
    }

    @Override
    @Transactional
    public void update(KnowledgeBaseUpdateRequest requestParam) {
        KnowledgeBaseEntity entity = requireKnowledgeBase(parseId(requestParam.getId()));
        if (StringUtils.hasText(requestParam.getName())) {
            String name = requestParam.getName().trim().replaceAll("\\s+", "");
            long nameCount = knowledgeBaseMapper.selectCount(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                    .eq(KnowledgeBaseEntity::getName, name)
                    .eq(KnowledgeBaseEntity::getDeleted, 0)
                    .ne(KnowledgeBaseEntity::getId, entity.getId()));
            if (nameCount > 0) {
                throw new IllegalArgumentException("knowledge base name already exists");
            }
            entity.setName(requestParam.getName().trim());
        }
        if (StringUtils.hasText(requestParam.getEmbeddingModel())) {
            String embeddingModel = requestParam.getEmbeddingModel().trim();
            if (!embeddingModel.equals(entity.getEmbeddingModel())) {
                long documentCount = knowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getKbId, entity.getId())
                        .gt(KnowledgeDocumentEntity::getChunkCount, 0)
                        .eq(KnowledgeDocumentEntity::getDeleted, 0));
                if (documentCount > 0) {
                    throw new IllegalArgumentException("knowledge base already has vectorized documents, embedding model cannot be changed");
                }
            }
            entity.setEmbeddingModel(embeddingModel);
        }
        entity.setUpdatedBy(parseUserId(UserContext.getUserId()));
        knowledgeBaseMapper.updateById(entity);
    }

    @Override
    @Transactional
    public void rename(String kbId, KnowledgeBaseUpdateRequest requestParam) {
        KnowledgeBaseEntity entity = requireKnowledgeBase(parseId(kbId));
        if (!StringUtils.hasText(requestParam.getName())) {
            throw new IllegalArgumentException("knowledge base name is required");
        }
        String name = requestParam.getName().trim().replaceAll("\\s+", "");
        long nameCount = knowledgeBaseMapper.selectCount(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getName, name)
                .eq(KnowledgeBaseEntity::getDeleted, 0)
                .ne(KnowledgeBaseEntity::getId, entity.getId()));
        if (nameCount > 0) {
            throw new IllegalArgumentException("knowledge base name already exists");
        }
        entity.setName(requestParam.getName());
        entity.setUpdatedBy(parseUserId(UserContext.getUserId()));
        knowledgeBaseMapper.updateById(entity);
    }

    @Override
    @Transactional
    public void delete(String kbId) {
        KnowledgeBaseEntity entity = requireKnowledgeBase(parseId(kbId));
        long documentCount = knowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getKbId, entity.getId())
                .eq(KnowledgeDocumentEntity::getDeleted, 0));
        if (documentCount > 0) {
            throw new IllegalArgumentException("knowledge base still has documents");
        }
        knowledgeBaseMapper.deleteById(entity.getId());
    }

    @Override
    public KnowledgeBaseVO queryById(String kbId) {
        KnowledgeBaseEntity entity = requireKnowledgeBase(parseId(kbId));
        KnowledgeBaseVO vo = toView(entity, knowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getKbId, entity.getId())
                .eq(KnowledgeDocumentEntity::getDeleted, 0)));
        return vo;
    }

    @Override
    public IPage<KnowledgeBaseVO> pageQuery(KnowledgeBasePageRequest requestParam) {
        Page<KnowledgeBaseEntity> page = new Page<>(requestParam.getCurrent(), requestParam.getSize());
        IPage<KnowledgeBaseEntity> result = knowledgeBaseMapper.selectPage(
                page,
                new LambdaQueryWrapper<KnowledgeBaseEntity>()
                        .like(StringUtils.hasText(requestParam.getName()), KnowledgeBaseEntity::getName, requestParam.getName())
                        .orderByDesc(KnowledgeBaseEntity::getUpdatedAt)
        );
        List<Long> kbIds = result.getRecords().stream().map(KnowledgeBaseEntity::getId).toList();
        Map<Long, Long> documentCountMap = kbIds.isEmpty()
                ? Map.of()
                : knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .in(KnowledgeDocumentEntity::getKbId, kbIds)
                        .eq(KnowledgeDocumentEntity::getDeleted, 0))
                .stream()
                .collect(Collectors.groupingBy(KnowledgeDocumentEntity::getKbId, Collectors.counting()));
        return result.convert(entity -> {
            return toView(entity, documentCountMap.getOrDefault(entity.getId(), 0L));
        });
    }

    private KnowledgeBaseVO toView(KnowledgeBaseEntity entity, Long documentCount) {
        KnowledgeBaseVO vo = BeanUtil.toBean(entity, KnowledgeBaseVO.class);
        vo.setId(String.valueOf(entity.getId()));
        vo.setDocumentCount(documentCount);
        vo.setCreatedBy(entity.getCreatedBy() == null ? null : String.valueOf(entity.getCreatedBy()));
        vo.setCreateTime(entity.getCreatedAt() == null ? null : java.util.Date.from(entity.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant()));
        vo.setUpdateTime(entity.getUpdatedAt() == null ? null : java.util.Date.from(entity.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant()));
        return vo;
    }

    private KnowledgeBaseEntity requireKnowledgeBase(Long kbId) {
        KnowledgeBaseEntity entity = knowledgeBaseMapper.selectById(kbId);
        if (entity == null) {
            throw new IllegalArgumentException("knowledge base not found");
        }
        return entity;
    }

    private Long parseId(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("id must not be blank");
        }
        return Long.valueOf(value.trim());
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private void assertCollectionAvailable(String collectionName, Long currentKbId) {
        KnowledgeBaseEntity existing = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBaseEntity>()
                .eq(KnowledgeBaseEntity::getCollectionName, collectionName)
                .last("limit 1"));
        if (existing != null && !existing.getId().equals(currentKbId)) {
            throw new IllegalArgumentException("collection name already exists");
        }
    }

    private Long parseUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        try {
            return Long.valueOf(userId.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
