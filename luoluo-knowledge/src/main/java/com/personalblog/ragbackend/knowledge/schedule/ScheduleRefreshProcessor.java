package com.personalblog.ragbackend.knowledge.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentScheduleDO;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentScheduleMapper;
import com.personalblog.ragbackend.knowledge.service.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ScheduleRefreshProcessor {
    private final KnowledgeDocumentScheduleMapper scheduleMapper;
    private final ScheduleLockManager scheduleLockManager;
    private final KnowledgeDocumentService documentService;

    @Transactional
    public void refresh() {
        List<KnowledgeDocumentScheduleDO> dueSchedules = scheduleMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocumentScheduleDO>()
                        .eq(KnowledgeDocumentScheduleDO::getEnabled, 1)
                        .le(KnowledgeDocumentScheduleDO::getNextRunTime, LocalDateTime.now())
                        .orderByAsc(KnowledgeDocumentScheduleDO::getNextRunTime)
        );
        for (KnowledgeDocumentScheduleDO schedule : dueSchedules) {
            ScheduleLockLease lease = scheduleLockManager.tryAcquire(String.valueOf(schedule.getId()), new java.util.Date());
            if (lease == null) {
                continue;
            }
            try {
                documentService.startChunk(String.valueOf(schedule.getDocId()));
            } finally {
                scheduleLockManager.release(lease);
            }
        }
    }
}
