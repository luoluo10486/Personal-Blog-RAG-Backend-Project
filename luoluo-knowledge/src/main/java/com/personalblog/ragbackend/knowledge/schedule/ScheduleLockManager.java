package com.personalblog.ragbackend.knowledge.schedule;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.personalblog.ragbackend.knowledge.config.KnowledgeScheduleProperties;
import com.personalblog.ragbackend.knowledge.dao.entity.KnowledgeDocumentScheduleDO;
import com.personalblog.ragbackend.knowledge.mapper.KnowledgeDocumentScheduleMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.util.Date;
import java.util.UUID;

@Component
public class ScheduleLockManager {
    private final KnowledgeDocumentScheduleMapper scheduleMapper;
    private final KnowledgeScheduleProperties scheduleProperties;
    private final String instancePrefix = resolveInstancePrefix();

    public ScheduleLockManager(KnowledgeDocumentScheduleMapper scheduleMapper,
                               KnowledgeScheduleProperties scheduleProperties) {
        this.scheduleMapper = scheduleMapper;
        this.scheduleProperties = scheduleProperties;
    }

    public ScheduleLockLease tryAcquire(String scheduleId, Date now) {
        if (!StringUtils.hasText(scheduleId)) {
            return null;
        }
        ScheduleLockLease lease = new ScheduleLockLease(scheduleId, nextLockToken());
        int updated = scheduleMapper.update(
                Wrappers.lambdaUpdate(KnowledgeDocumentScheduleDO.class)
                        .set(KnowledgeDocumentScheduleDO::getLockOwner, lease.lockToken())
                        .set(KnowledgeDocumentScheduleDO::getLockUntil, computeLockUntilAsLocalDateTime())
                        .eq(KnowledgeDocumentScheduleDO::getId, Long.valueOf(scheduleId))
                        .and(wrapper -> wrapper.isNull(KnowledgeDocumentScheduleDO::getLockUntil)
                                .or()
                                .lt(KnowledgeDocumentScheduleDO::getLockUntil, now))
        );
        return updated > 0 ? lease : null;
    }

    public boolean renew(ScheduleLockLease lease) {
        if (lease == null) {
            return false;
        }
        return scheduleMapper.update(
                Wrappers.lambdaUpdate(KnowledgeDocumentScheduleDO.class)
                        .set(KnowledgeDocumentScheduleDO::getLockUntil, computeLockUntilAsLocalDateTime())
                        .eq(KnowledgeDocumentScheduleDO::getId, Long.valueOf(lease.scheduleId()))
                        .eq(KnowledgeDocumentScheduleDO::getLockOwner, lease.lockToken())
        ) > 0;
    }

    public boolean release(ScheduleLockLease lease) {
        if (lease == null) {
            return false;
        }
        return scheduleMapper.update(
                Wrappers.lambdaUpdate(KnowledgeDocumentScheduleDO.class)
                        .set(KnowledgeDocumentScheduleDO::getLockOwner, null)
                        .set(KnowledgeDocumentScheduleDO::getLockUntil, null)
                        .eq(KnowledgeDocumentScheduleDO::getId, Long.valueOf(lease.scheduleId()))
                        .eq(KnowledgeDocumentScheduleDO::getLockOwner, lease.lockToken())
        ) > 0;
    }

    public Date computeLockUntil() {
        return new Date(System.currentTimeMillis() + effectiveLockSeconds() * 1000);
    }

    private java.time.LocalDateTime computeLockUntilAsLocalDateTime() {
        return java.time.LocalDateTime.ofInstant(computeLockUntil().toInstant(), java.time.ZoneId.systemDefault());
    }

    private long effectiveLockSeconds() {
        return Math.max(scheduleProperties.getLockSeconds(), 60L);
    }

    private String nextLockToken() {
        return instancePrefix + ":" + UUID.randomUUID();
    }

    private static String resolveInstancePrefix() {
        try {
            return "kb-schedule-" + InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID();
        } catch (Exception ignored) {
            return "kb-schedule-unknown-" + UUID.randomUUID();
        }
    }

    @PreDestroy
    public void shutdown() {
        // no-op
    }
}
