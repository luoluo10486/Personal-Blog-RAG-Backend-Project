package com.personalblog.ragbackend.knowledge.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.knowledge.dao.entity.SampleQuestionEntity;
import com.personalblog.ragbackend.knowledge.mapper.SampleQuestionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SampleQuestionAdminService {
    private final SampleQuestionMapper sampleQuestionMapper;

    public SampleQuestionAdminService(SampleQuestionMapper sampleQuestionMapper) {
        this.sampleQuestionMapper = sampleQuestionMapper;
    }

    public List<SampleQuestionEntity> listRandomQuestions() {
        return sampleQuestionMapper.selectList(new QueryWrapper<SampleQuestionEntity>()
                .eq("enabled", 1)
                .last("order by rand() limit 8"));
    }

    public IPage<SampleQuestionEntity> pageQuery(IPage<SampleQuestionEntity> page, String keyword) {
        return sampleQuestionMapper.selectPage(page, new QueryWrapper<SampleQuestionEntity>()
                .eq("enabled", 1)
                .and(keyword != null && !keyword.isBlank(), wrapper -> wrapper
                        .like("title", keyword)
                        .or()
                        .like("description", keyword)
                        .or()
                        .like("question", keyword))
                .orderByAsc("sort_order")
                .orderByDesc("update_time"));
    }

    public SampleQuestionEntity queryById(Long id) {
        SampleQuestionEntity entity = sampleQuestionMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("示例问题不存在");
        }
        return entity;
    }

    public Long create(String title, String description, String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("示例问题不能为空");
        }
        SampleQuestionEntity entity = new SampleQuestionEntity();
        entity.title = blankToNull(title);
        entity.description = blankToNull(description);
        entity.question = question.trim();
        entity.sortOrder = 0;
        entity.enabled = 1;
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = LocalDateTime.now();
        sampleQuestionMapper.insert(entity);
        return entity.id;
    }

    public void update(Long id, String title, String description, String question) {
        SampleQuestionEntity entity = queryById(id);
        if (title != null) {
            entity.title = blankToNull(title);
        }
        if (description != null) {
            entity.description = blankToNull(description);
        }
        if (question != null) {
            entity.question = question.trim();
        }
        entity.updatedAt = LocalDateTime.now();
        sampleQuestionMapper.updateById(entity);
    }

    public void delete(Long id) {
        sampleQuestionMapper.deleteById(id);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
