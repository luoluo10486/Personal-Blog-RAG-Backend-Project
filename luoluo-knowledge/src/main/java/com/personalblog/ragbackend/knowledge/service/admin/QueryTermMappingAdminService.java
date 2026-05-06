package com.personalblog.ragbackend.knowledge.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.personalblog.ragbackend.knowledge.dao.entity.QueryTermMappingEntity;
import com.personalblog.ragbackend.knowledge.mapper.QueryTermMappingMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class QueryTermMappingAdminService {
    private final QueryTermMappingMapper queryTermMappingMapper;

    public QueryTermMappingAdminService(QueryTermMappingMapper queryTermMappingMapper) {
        this.queryTermMappingMapper = queryTermMappingMapper;
    }

    public IPage<QueryTermMappingEntity> pageQuery(IPage<QueryTermMappingEntity> page, String domain, String keyword) {
        return queryTermMappingMapper.selectPage(page, new QueryWrapper<QueryTermMappingEntity>()
                .eq(domain != null && !domain.isBlank(), "domain", domain)
                .and(keyword != null && !keyword.isBlank(), wrapper -> wrapper
                        .like("source_term", keyword)
                        .or()
                        .like("target_term", keyword))
                .orderByAsc("priority")
                .orderByDesc("updated_at"));
    }

    public QueryTermMappingEntity queryById(Long id) {
        QueryTermMappingEntity entity = queryTermMappingMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("映射规则不存在");
        }
        return entity;
    }

    public Long create(String sourceTerm, String targetTerm, Integer matchType, Integer priority, Boolean enabled, String remark) {
        if (sourceTerm == null || sourceTerm.isBlank() || targetTerm == null || targetTerm.isBlank()) {
            throw new IllegalArgumentException("源词和目标词不能为空");
        }
        QueryTermMappingEntity entity = new QueryTermMappingEntity();
        entity.sourceTerm = sourceTerm.trim();
        entity.targetTerm = targetTerm.trim();
        entity.matchType = matchType == null ? 1 : matchType;
        entity.priority = priority == null ? 100 : priority;
        entity.enabled = enabled == null || enabled ? 1 : 0;
        entity.remark = remark;
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = LocalDateTime.now();
        queryTermMappingMapper.insert(entity);
        return entity.id;
    }

    public void update(Long id, String sourceTerm, String targetTerm, Integer matchType, Integer priority, Boolean enabled, String remark) {
        QueryTermMappingEntity entity = queryById(id);
        if (sourceTerm != null) {
            entity.sourceTerm = sourceTerm.trim();
        }
        if (targetTerm != null) {
            entity.targetTerm = targetTerm.trim();
        }
        if (matchType != null) {
            entity.matchType = matchType;
        }
        if (priority != null) {
            entity.priority = priority;
        }
        if (enabled != null) {
            entity.enabled = enabled ? 1 : 0;
        }
        if (remark != null) {
            entity.remark = remark;
        }
        entity.updatedAt = LocalDateTime.now();
        queryTermMappingMapper.updateById(entity);
    }

    public void delete(Long id) {
        queryTermMappingMapper.deleteById(id);
    }
}
