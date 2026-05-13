package com.personalblog.ragbackend.rag.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personalblog.ragbackend.knowledge.dao.entity.QueryTermMappingEntity;
import com.personalblog.ragbackend.knowledge.mapper.QueryTermMappingMapper;
import com.personalblog.ragbackend.rag.controller.request.QueryTermMappingCreateRequest;
import com.personalblog.ragbackend.rag.controller.request.QueryTermMappingPageRequest;
import com.personalblog.ragbackend.rag.controller.request.QueryTermMappingUpdateRequest;
import com.personalblog.ragbackend.rag.controller.vo.QueryTermMappingVO;
import com.personalblog.ragbackend.rag.service.QueryTermMappingAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class QueryTermMappingAdminServiceImpl implements QueryTermMappingAdminService {
    private final QueryTermMappingMapper queryTermMappingMapper;

    @Override
    public String create(QueryTermMappingCreateRequest requestParam) {
        Assert.notNull(requestParam, () -> new IllegalArgumentException("请求不能为空"));
        String sourceTerm = StrUtil.trimToNull(requestParam.getSourceTerm());
        String targetTerm = StrUtil.trimToNull(requestParam.getTargetTerm());
        Assert.notBlank(sourceTerm, () -> new IllegalArgumentException("原始词不能为空"));
        Assert.notBlank(targetTerm, () -> new IllegalArgumentException("目标词不能为空"));

        QueryTermMappingEntity record = new QueryTermMappingEntity();
        record.sourceTerm = sourceTerm;
        record.targetTerm = targetTerm;
        record.matchType = requestParam.getMatchType() == null ? 1 : requestParam.getMatchType();
        record.priority = requestParam.getPriority() == null ? 0 : requestParam.getPriority();
        record.enabled = requestParam.getEnabled() == null ? 1 : (requestParam.getEnabled() ? 1 : 0);
        record.remark = StrUtil.trimToNull(requestParam.getRemark());
        queryTermMappingMapper.insert(record);
        return String.valueOf(record.id);
    }

    @Override
    public void update(String id, QueryTermMappingUpdateRequest requestParam) {
        Assert.notNull(requestParam, () -> new IllegalArgumentException("请求不能为空"));
        QueryTermMappingEntity record = loadById(id);
        if (requestParam.getSourceTerm() != null) {
            String sourceTerm = StrUtil.trimToNull(requestParam.getSourceTerm());
            Assert.notBlank(sourceTerm, () -> new IllegalArgumentException("原始词不能为空"));
            record.sourceTerm = sourceTerm;
        }
        if (requestParam.getTargetTerm() != null) {
            String targetTerm = StrUtil.trimToNull(requestParam.getTargetTerm());
            Assert.notBlank(targetTerm, () -> new IllegalArgumentException("目标词不能为空"));
            record.targetTerm = targetTerm;
        }
        if (requestParam.getMatchType() != null) {
            record.matchType = requestParam.getMatchType();
        }
        if (requestParam.getPriority() != null) {
            record.priority = requestParam.getPriority();
        }
        if (requestParam.getEnabled() != null) {
            record.enabled = requestParam.getEnabled() ? 1 : 0;
        }
        if (requestParam.getRemark() != null) {
            record.remark = StrUtil.trimToNull(requestParam.getRemark());
        }
        queryTermMappingMapper.updateById(record);
    }

    @Override
    public void delete(String id) {
        QueryTermMappingEntity record = loadById(id);
        queryTermMappingMapper.deleteById(record.id);
    }

    @Override
    public QueryTermMappingVO queryById(String id) {
        return toVO(loadById(id));
    }

    @Override
    public IPage<QueryTermMappingVO> pageQuery(QueryTermMappingPageRequest requestParam) {
        String keyword = requestParam == null ? null : StrUtil.trimToNull(requestParam.getKeyword());
        Page<QueryTermMappingEntity> page = new Page<>(
                requestParam == null ? 1 : Math.max(requestParam.getCurrent(), 1),
                requestParam == null ? 10 : Math.max(requestParam.getSize(), 1)
        );
        IPage<QueryTermMappingEntity> result = queryTermMappingMapper.selectPage(
                page,
                new QueryWrapper<QueryTermMappingEntity>()
                        .and(StrUtil.isNotBlank(keyword), wrapper -> wrapper
                                .like("source_term", keyword)
                                .or()
                                .like("target_term", keyword))
                        .orderByAsc("priority")
                        .orderByDesc("update_time")
        );
        return result.convert(this::toVO);
    }

    private QueryTermMappingEntity loadById(String id) {
        QueryTermMappingEntity record = queryTermMappingMapper.selectOne(
                new QueryWrapper<QueryTermMappingEntity>()
                        .eq("id", id)
        );
        Assert.notNull(record, () -> new IllegalArgumentException("映射规则不存在"));
        return record;
    }

    private QueryTermMappingVO toVO(QueryTermMappingEntity record) {
        return QueryTermMappingVO.builder()
                .id(String.valueOf(record.id))
                .sourceTerm(record.sourceTerm)
                .targetTerm(record.targetTerm)
                .matchType(record.matchType)
                .priority(record.priority)
                .enabled(record.enabled != null && record.enabled == 1)
                .remark(record.remark)
                .createTime(record.createdAt == null ? null : Date.from(record.createdAt.atZone(java.time.ZoneId.systemDefault()).toInstant()))
                .updateTime(record.updatedAt == null ? null : Date.from(record.updatedAt.atZone(java.time.ZoneId.systemDefault()).toInstant()))
                .build();
    }
}
