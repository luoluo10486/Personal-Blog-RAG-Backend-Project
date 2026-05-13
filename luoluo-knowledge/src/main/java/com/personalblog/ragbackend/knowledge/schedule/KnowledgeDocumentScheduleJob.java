package com.personalblog.ragbackend.knowledge.schedule;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KnowledgeDocumentScheduleJob {
    private final ScheduleRefreshProcessor scheduleRefreshProcessor;

    @Scheduled(fixedDelayString = "${rag.knowledge.schedule.scan-delay-ms:10000}")
    public void refresh() {
        scheduleRefreshProcessor.refresh();
    }
}
