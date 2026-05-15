package com.personalblog.ragbackend.rag.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.personalblog.ragbackend.framework.exception.ClientException;
import com.personalblog.ragbackend.rag.dao.entity.QueryTermMappingEntity;
import com.personalblog.ragbackend.rag.dao.mapper.QueryTermMappingMapper;
import com.personalblog.ragbackend.rag.controller.request.QueryTermMappingCreateRequest;
import com.personalblog.ragbackend.rag.controller.request.QueryTermMappingPageRequest;
import com.personalblog.ragbackend.rag.controller.request.QueryTermMappingUpdateRequest;
import com.personalblog.ragbackend.rag.controller.vo.QueryTermMappingVO;
import com.personalblog.ragbackend.rag.service.QueryTermMappingAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class QueryTermMappingAdminServiceImpl implements QueryTermMappingAdminService {

    private final QueryTermMappingMapper queryTermMappingMapper;

    @Override
    public String create(QueryTermMappingCreateRequest requestParam) {
        Assert.notNull(requestParam, () -> new ClientException("璇锋眰涓嶈兘涓虹┖"));
        String sourceTerm = StrUtil.trimToNull(requestParam.getSourceTerm());
        String targetTerm = StrUtil.trimToNull(requestParam.getTargetTerm());
                Assert.notBlank(sourceTerm, () -> new ClientException("原始词不能为空"));
                Assert.notBlank(targetTerm, () -> new ClientException("目标词不能为空"));

        QueryTermMappingEntity record = QueryTermMappingEntity.builder()
                .sourceTerm(sourceTerm)
                .targetTerm(targetTerm)
                .matchType(requestParam.getMatchType() == null ? 1 : requestParam.getMatchType())
                .priority(requestParam.getPriority() == null ? 0 : requestParam.getPriority())
                .enabled(requestParam.getEnabled() == null ? 1 : (requestParam.getEnabled() ? 1 : 0))
                .remark(StrUtil.trimToNull(requestParam.getRemark()))
                .build();
        queryTermMappingMapper.insert(record);
        return String.valueOf(record.getId());
    }

    @Override
    public void update(String id, QueryTermMappingUpdateRequest requestParam) {
        Assert.notNull(requestParam, () -> new ClientException("璇锋眰涓嶈兘涓虹┖"));
        QueryTermMappingEntity record = loadById(id);

        if (requestParam.getSourceTerm() != null) {
            String sourceTerm = StrUtil.trimToNull(requestParam.getSourceTerm());
                    Assert.notBlank(sourceTerm, () -> new ClientException("原始词不能为空"));
            record.setSourceTerm(sourceTerm);
        }
        if (requestParam.getTargetTerm() != null) {
            String targetTerm = StrUtil.trimToNull(requestParam.getTargetTerm());
                    Assert.notBlank(targetTerm, () -> new ClientException("目标词不能为空"));
            record.setTargetTerm(targetTerm);
        }
        if (requestParam.getMatchType() != null) {
            record.setMatchType(requestParam.getMatchType());
        }
        if (requestParam.getPriority() != null) {
            record.setPriority(requestParam.getPriority());
        }
        if (requestParam.getEnabled() != null) {
            record.setEnabled(requestParam.getEnabled() ? 1 : 0);
        }
        if (requestParam.getRemark() != null) {
            record.setRemark(StrUtil.trimToNull(requestParam.getRemark()));
        }
        queryTermMappingMapper.updateById(record);
    }

    @Override
    public void delete(String id) {
        QueryTermMappingEntity record = loadById(id);
        queryTermMappingMapper.deleteById(record.getId());
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
                Wrappers.lambdaQuery(QueryTermMappingEntity.class)
                        .and(StrUtil.isNotBlank(keyword), wrapper -> wrapper
                                .like(QueryTermMappingEntity::getSourceTerm, keyword)
                                .or()
                                .like(QueryTermMappingEntity::getTargetTerm, keyword))
                        .orderByAsc(QueryTermMappingEntity::getPriority)
                        .orderByDesc(QueryTermMappingEntity::getUpdatedAt)
        );
        return result.convert(this::toVO);
    }

    private QueryTermMappingEntity loadById(String id) {
        QueryTermMappingEntity record = queryTermMappingMapper.selectOne(
                Wrappers.lambdaQuery(QueryTermMappingEntity.class)
                        .eq(QueryTermMappingEntity::getId, id)
        );
                Assert.notNull(record, () -> new ClientException("映射规则不存在"));
        return record;
    }

    private QueryTermMappingVO toVO(QueryTermMappingEntity record) {
        return QueryTermMappingVO.builder()
                .id(String.valueOf(record.getId()))
                .sourceTerm(record.getSourceTerm())
                .targetTerm(record.getTargetTerm())
                .matchType(record.getMatchType())
                .priority(record.getPriority())
                .enabled(record.getEnabled() != null && record.getEnabled() == 1)
                .remark(record.getRemark())
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


