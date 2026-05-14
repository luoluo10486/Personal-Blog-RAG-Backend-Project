package com.personalblog.ragbackend.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentScheduleEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentScheduleExecEntity;
import com.personalblog.ragbackend.knowledge.domain.enums.SourceType;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentScheduleExecMapper;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentScheduleMapper;
import com.personalblog.ragbackend.knowledge.schedule.CronScheduleHelper;
import com.personalblog.ragbackend.knowledge.service.KnowledgeDocumentScheduleService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;

@Service
public class KnowledgeDocumentScheduleServiceImpl implements KnowledgeDocumentScheduleService {
    private final KnowledgeDocumentScheduleMapper scheduleMapper;
    private final KnowledgeDocumentScheduleExecMapper scheduleExecMapper;

    @Value("${rag.knowledge.schedule.min-interval-seconds:60}")
    private long scheduleMinIntervalSeconds;

    public KnowledgeDocumentScheduleServiceImpl(KnowledgeDocumentScheduleMapper scheduleMapper,
                                                KnowledgeDocumentScheduleExecMapper scheduleExecMapper) {
        this.scheduleMapper = scheduleMapper;
        this.scheduleExecMapper = scheduleExecMapper;
    }

    @Override
    public void upsertSchedule(KnowledgeDocumentEntity documentDO) {
        syncSchedule(documentDO, true);
    }

    @Override
    public void syncScheduleIfExists(KnowledgeDocumentEntity documentDO) {
        syncSchedule(documentDO, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByDocId(String docId) {
        if (!StringUtils.hasText(docId)) {
            return;
        }
        scheduleExecMapper.delete(new LambdaQueryWrapper<KnowledgeDocumentScheduleExecEntity>()
                .eq(KnowledgeDocumentScheduleExecEntity::getDocId, Long.valueOf(docId)));
        scheduleMapper.delete(new LambdaQueryWrapper<KnowledgeDocumentScheduleEntity>()
                .eq(KnowledgeDocumentScheduleEntity::getDocId, Long.valueOf(docId)));
    }

    private void syncSchedule(KnowledgeDocumentEntity documentDO, boolean allowCreate) {
        if (documentDO == null) {
            return;
        }
        if (documentDO.getId() == null || documentDO.getKbId() == null) {
            return;
        }
        if (!SourceType.URL.getValue().equalsIgnoreCase(documentDO.getSourceType())) {
            return;
        }
        boolean docEnabled = documentDO.getEnabled() == null || documentDO.getEnabled() == 1;
        String cron = documentDO.getScheduleCron();
        boolean enabled = documentDO.getScheduleEnabled() != null && documentDO.getScheduleEnabled() == 1;
        if (!StringUtils.hasText(cron)) {
            enabled = false;
        }
        if (!docEnabled) {
            enabled = false;
        }

        Date nextRunTime = null;
        if (enabled) {
            try {
                if (CronScheduleHelper.isIntervalLessThan(cron, new Date(), scheduleMinIntervalSeconds)) {
                    throw new IllegalArgumentException("schedule interval is too short");
                }
                nextRunTime = CronScheduleHelper.nextRunTime(cron, new Date());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "schedule interval is too short".equals(exception.getMessage())
                                ? exception.getMessage()
                                : "schedule cron is invalid",
                        exception
                );
            }
        }

        KnowledgeDocumentScheduleEntity existing = scheduleMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeDocumentScheduleEntity>()
                        .eq(KnowledgeDocumentScheduleEntity::getDocId, documentDO.getId())
                        .last("LIMIT 1")
        );
        if (existing == null) {
            if (!allowCreate) {
                return;
            }
            KnowledgeDocumentScheduleEntity entity = new KnowledgeDocumentScheduleEntity();
            entity.setDocId(documentDO.getId());
            entity.setKbId(documentDO.getKbId());
            entity.setCronExpr(cron);
            entity.setEnabled(enabled ? 1 : 0);
            entity.setNextRunTime(nextRunTime == null ? null : nextRunTime.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
            scheduleMapper.insert(entity);
            return;
        }

        scheduleMapper.update(
                null,
                new LambdaUpdateWrapper<KnowledgeDocumentScheduleEntity>()
                        .eq(KnowledgeDocumentScheduleEntity::getId, existing.getId())
                        .set(KnowledgeDocumentScheduleEntity::getCronExpr, cron)
                        .set(KnowledgeDocumentScheduleEntity::getEnabled, enabled ? 1 : 0)
                        .set(KnowledgeDocumentScheduleEntity::getNextRunTime, nextRunTime == null ? null : nextRunTime.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime())
        );
    }
}
