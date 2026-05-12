package com.personalblog.ragbackend.knowledge.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personalblog.ragbackend.knowledge.dao.entity.SampleQuestionEntity;
import com.personalblog.ragbackend.knowledge.mapper.SampleQuestionMapper;
import com.personalblog.ragbackend.rag.controller.request.SampleQuestionCreateRequest;
import com.personalblog.ragbackend.rag.controller.request.SampleQuestionPageRequest;
import com.personalblog.ragbackend.rag.controller.request.SampleQuestionUpdateRequest;
import com.personalblog.ragbackend.rag.controller.vo.SampleQuestionVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SampleQuestionAdminService {
    private static final int DEFAULT_LIMIT = 3;

    private final SampleQuestionMapper sampleQuestionMapper;

    public SampleQuestionAdminService(SampleQuestionMapper sampleQuestionMapper) {
        this.sampleQuestionMapper = sampleQuestionMapper;
    }

    public String create(SampleQuestionCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null");
        }
        String question = trimToNull(request.getQuestion());
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("Sample question content must not be blank");
        }

        SampleQuestionEntity entity = new SampleQuestionEntity();
        entity.title = trimToNull(request.getTitle());
        entity.description = trimToNull(request.getDescription());
        entity.question = question;
        entity.sortOrder = 0;
        entity.enabled = 1;
        entity.deleted = 0;
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = LocalDateTime.now();
        sampleQuestionMapper.insert(entity);
        return entity.id == null ? null : String.valueOf(entity.id);
    }

    public void update(String id, SampleQuestionUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null");
        }
        SampleQuestionEntity entity = loadById(id);
        if (request.getQuestion() != null) {
            String question = trimToNull(request.getQuestion());
            if (!StringUtils.hasText(question)) {
                throw new IllegalArgumentException("Sample question content must not be blank");
            }
            entity.question = question;
        }
        if (request.getTitle() != null) {
            entity.title = trimToNull(request.getTitle());
        }
        if (request.getDescription() != null) {
            entity.description = trimToNull(request.getDescription());
        }
        entity.updatedAt = LocalDateTime.now();
        sampleQuestionMapper.updateById(entity);
    }

    public void delete(String id) {
        SampleQuestionEntity entity = loadById(id);
        sampleQuestionMapper.deleteById(entity.id);
    }

    public SampleQuestionVO queryById(String id) {
        return toVO(loadById(id));
    }

    public IPage<SampleQuestionVO> pageQuery(SampleQuestionPageRequest request) {
        SampleQuestionPageRequest resolved = request == null ? new SampleQuestionPageRequest() : request;
        String keyword = trimToNull(resolved.getKeyword());
        Page<SampleQuestionEntity> page = new Page<>(Math.max(resolved.getCurrent(), 1), Math.max(resolved.getSize(), 1));
        IPage<SampleQuestionEntity> result = sampleQuestionMapper.selectPage(
                page,
                new QueryWrapper<SampleQuestionEntity>()
                        .eq("deleted", 0)
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like("title", keyword)
                                .or()
                                .like("description", keyword)
                                .or()
                                .like("question", keyword))
                        .orderByDesc("update_time")
        );
        return result.convert(this::toVO);
    }

    public List<SampleQuestionVO> listRandomQuestions() {
        List<SampleQuestionEntity> records = sampleQuestionMapper.selectList(
                new QueryWrapper<SampleQuestionEntity>()
                        .eq("deleted", 0)
                        .last("order by rand() limit " + DEFAULT_LIMIT)
        );
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream().map(this::toVO).toList();
    }

    private SampleQuestionEntity loadById(String id) {
        Long parsedId = parseRequiredLong(id);
        SampleQuestionEntity entity = sampleQuestionMapper.selectOne(
                new QueryWrapper<SampleQuestionEntity>()
                        .eq("id", parsedId)
                        .eq("deleted", 0)
                        .last("limit 1")
        );
        if (entity == null) {
            throw new IllegalArgumentException("Sample question does not exist");
        }
        return entity;
    }

    private SampleQuestionVO toVO(SampleQuestionEntity entity) {
        SampleQuestionVO vo = new SampleQuestionVO();
        vo.setId(entity.id == null ? null : String.valueOf(entity.id));
        vo.setTitle(entity.title);
        vo.setDescription(entity.description);
        vo.setQuestion(entity.question);
        vo.setCreateTime(entity.createdAt);
        vo.setUpdateTime(entity.updatedAt);
        return vo;
    }

    private Long parseRequiredLong(String id) {
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("Sample question does not exist");
        }
        try {
            return Long.valueOf(id.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Sample question does not exist", exception);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
