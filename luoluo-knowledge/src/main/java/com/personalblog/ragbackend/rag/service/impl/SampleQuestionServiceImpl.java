package com.personalblog.ragbackend.rag.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personalblog.ragbackend.knowledge.dao.entity.SampleQuestionEntity;
import com.personalblog.ragbackend.knowledge.mapper.SampleQuestionMapper;
import com.personalblog.ragbackend.rag.controller.request.SampleQuestionCreateRequest;
import com.personalblog.ragbackend.rag.controller.request.SampleQuestionPageRequest;
import com.personalblog.ragbackend.rag.controller.request.SampleQuestionUpdateRequest;
import com.personalblog.ragbackend.rag.controller.vo.SampleQuestionVO;
import com.personalblog.ragbackend.rag.service.SampleQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SampleQuestionServiceImpl implements SampleQuestionService {
    private static final int DEFAULT_LIMIT = 3;

    private final SampleQuestionMapper sampleQuestionMapper;

    @Override
    public String create(SampleQuestionCreateRequest requestParam) {
        Assert.notNull(requestParam, () -> new IllegalArgumentException("请求不能为空"));
        String question = StrUtil.trimToNull(requestParam.getQuestion());
        Assert.notBlank(question, () -> new IllegalArgumentException("示例问题内容不能为空"));

        SampleQuestionEntity record = new SampleQuestionEntity();
        record.title = StrUtil.trimToNull(requestParam.getTitle());
        record.description = StrUtil.trimToNull(requestParam.getDescription());
        record.question = question;
        sampleQuestionMapper.insert(record);
        return record.id;
    }

    @Override
    public void update(String id, SampleQuestionUpdateRequest requestParam) {
        Assert.notNull(requestParam, () -> new IllegalArgumentException("请求不能为空"));
        SampleQuestionEntity record = loadById(id);
        if (requestParam.getQuestion() != null) {
            String question = StrUtil.trimToNull(requestParam.getQuestion());
            Assert.notBlank(question, () -> new IllegalArgumentException("示例问题内容不能为空"));
            record.question = question;
        }
        if (requestParam.getTitle() != null) {
            record.title = StrUtil.trimToNull(requestParam.getTitle());
        }
        if (requestParam.getDescription() != null) {
            record.description = StrUtil.trimToNull(requestParam.getDescription());
        }
        sampleQuestionMapper.updateById(record);
    }

    @Override
    public void delete(String id) {
        SampleQuestionEntity record = loadById(id);
        sampleQuestionMapper.deleteById(record.id);
    }

    @Override
    public SampleQuestionVO queryById(String id) {
        return toVO(loadById(id));
    }

    @Override
    public IPage<SampleQuestionVO> pageQuery(SampleQuestionPageRequest requestParam) {
        String keyword = StrUtil.trimToNull(requestParam.getKeyword());
        Page<SampleQuestionEntity> page = new Page<>(requestParam.getCurrent(), requestParam.getSize());
        IPage<SampleQuestionEntity> result = sampleQuestionMapper.selectPage(
                page,
                new QueryWrapper<SampleQuestionEntity>()
                        .eq("deleted", 0)
                        .and(StrUtil.isNotBlank(keyword), wrapper -> wrapper
                                .like("title", keyword)
                                .or()
                                .like("description", keyword)
                                .or()
                                .like("question", keyword))
                        .orderByDesc("update_time")
        );
        return result.convert(this::toVO);
    }

    @Override
    public List<SampleQuestionVO> listRandomQuestions() {
        List<SampleQuestionEntity> records = sampleQuestionMapper.selectList(
                new QueryWrapper<SampleQuestionEntity>()
                        .eq("deleted", 0)
                        .last("ORDER BY RANDOM() LIMIT " + DEFAULT_LIMIT)
        );
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream().map(this::toVO).toList();
    }

    private SampleQuestionEntity loadById(String id) {
        SampleQuestionEntity record = sampleQuestionMapper.selectOne(
                new QueryWrapper<SampleQuestionEntity>()
                        .eq("id", id)
                        .eq("deleted", 0)
                        .last("limit 1")
        );
        Assert.notNull(record, () -> new IllegalArgumentException("示例问题不存在"));
        return record;
    }

    private SampleQuestionVO toVO(SampleQuestionEntity record) {
        return SampleQuestionVO.builder()
                .id(record.id)
                .title(record.title)
                .description(record.description)
                .question(record.question)
                .createTime(record.createdAt == null ? null : Date.from(record.createdAt.atZone(java.time.ZoneId.systemDefault()).toInstant()))
                .updateTime(record.updatedAt == null ? null : Date.from(record.updatedAt.atZone(java.time.ZoneId.systemDefault()).toInstant()))
                .build();
    }
}
