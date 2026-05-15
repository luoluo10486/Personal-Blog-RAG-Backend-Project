package com.personalblog.ragbackend.rag.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personalblog.ragbackend.framework.exception.ClientException;
import com.personalblog.ragbackend.rag.dao.entity.SampleQuestionEntity;
import com.personalblog.ragbackend.rag.dao.mapper.SampleQuestionMapper;
import com.personalblog.ragbackend.rag.controller.request.SampleQuestionCreateRequest;
import com.personalblog.ragbackend.rag.controller.request.SampleQuestionPageRequest;
import com.personalblog.ragbackend.rag.controller.request.SampleQuestionUpdateRequest;
import com.personalblog.ragbackend.rag.controller.vo.SampleQuestionVO;
import com.personalblog.ragbackend.rag.service.SampleQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SampleQuestionServiceImpl implements SampleQuestionService {

    private static final int DEFAULT_LIMIT = 3;

    private final SampleQuestionMapper sampleQuestionMapper;

    @Override
    public String create(SampleQuestionCreateRequest requestParam) {
        Assert.notNull(requestParam, () -> new ClientException("璇锋眰涓嶈兘涓虹┖"));
        String question = StrUtil.trimToNull(requestParam.getQuestion());
        Assert.notBlank(question, () -> new ClientException("绀轰緥闂鍐呭涓嶈兘涓虹┖"));

        SampleQuestionEntity record = SampleQuestionEntity.builder()
                .title(StrUtil.trimToNull(requestParam.getTitle()))
                .description(StrUtil.trimToNull(requestParam.getDescription()))
                .question(question)
                .build();
        sampleQuestionMapper.insert(record);
        return record.getId();
    }

    @Override
    public void update(String id, SampleQuestionUpdateRequest requestParam) {
        Assert.notNull(requestParam, () -> new ClientException("璇锋眰涓嶈兘涓虹┖"));
        SampleQuestionEntity record = loadById(id);

        if (requestParam.getQuestion() != null) {
            String question = StrUtil.trimToNull(requestParam.getQuestion());
            Assert.notBlank(question, () -> new ClientException("绀轰緥闂鍐呭涓嶈兘涓虹┖"));
            record.setQuestion(question);
        }
        if (requestParam.getTitle() != null) {
            record.setTitle(StrUtil.trimToNull(requestParam.getTitle()));
        }
        if (requestParam.getDescription() != null) {
            record.setDescription(StrUtil.trimToNull(requestParam.getDescription()));
        }

        sampleQuestionMapper.updateById(record);
    }

    @Override
    public void delete(String id) {
        SampleQuestionEntity record = loadById(id);
        sampleQuestionMapper.deleteById(record.getId());
    }

    @Override
    public SampleQuestionVO queryById(String id) {
        return toVO(loadById(id));
    }

    @Override
    public IPage<SampleQuestionVO> pageQuery(SampleQuestionPageRequest requestParam) {
        String keyword = requestParam == null ? null : StrUtil.trimToNull(requestParam.getKeyword());
        Page<SampleQuestionEntity> page = new Page<>(
                requestParam == null ? 1 : Math.max(requestParam.getCurrent(), 1),
                requestParam == null ? 10 : Math.max(requestParam.getSize(), 1)
        );
        IPage<SampleQuestionEntity> result = sampleQuestionMapper.selectPage(
                page,
                Wrappers.lambdaQuery(SampleQuestionEntity.class)
                        .eq(SampleQuestionEntity::getDeleted, 0)
                        .and(StrUtil.isNotBlank(keyword), wrapper -> wrapper
                                .like(SampleQuestionEntity::getTitle, keyword)
                                .or()
                                .like(SampleQuestionEntity::getDescription, keyword)
                                .or()
                                .like(SampleQuestionEntity::getQuestion, keyword))
                        .orderByDesc(SampleQuestionEntity::getUpdatedAt)
        );
        return result.convert(this::toVO);
    }

    @Override
    public List<SampleQuestionVO> listRandomQuestions() {
        List<SampleQuestionEntity> records = sampleQuestionMapper.selectList(
                Wrappers.lambdaQuery(SampleQuestionEntity.class)
                        .eq(SampleQuestionEntity::getDeleted, 0)
                        .last("ORDER BY RANDOM() LIMIT " + DEFAULT_LIMIT)
        );
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream().map(this::toVO).toList();
    }

    private SampleQuestionEntity loadById(String id) {
        SampleQuestionEntity record = sampleQuestionMapper.selectOne(
                Wrappers.lambdaQuery(SampleQuestionEntity.class)
                        .eq(SampleQuestionEntity::getId, id)
                        .eq(SampleQuestionEntity::getDeleted, 0)
        );
                Assert.notNull(record, () -> new ClientException("示例问题不存在"));
        return record;
    }

    private SampleQuestionVO toVO(SampleQuestionEntity record) {
        return SampleQuestionVO.builder()
                .id(record.getId())
                .title(record.getTitle())
                .description(record.getDescription())
                .question(record.getQuestion())
                .createTime(toDate(record.getCreatedAt()))
                .updateTime(toDate(record.getUpdatedAt()))
                .build();
    }

    private Date toDate(java.time.LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
    }
}


