package com.personalblog.ragbackend.knowledge.service.rag;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 本地流式任务管理器。
 */
@Component
public class StreamTaskManager {
    private final ConcurrentMap<String, AtomicBoolean> cancelledTasks = new ConcurrentHashMap<>();

    public void register(String taskId) {
        cancelledTasks.put(taskId, new AtomicBoolean(false));
    }

    public void unregister(String taskId) {
        cancelledTasks.remove(taskId);
    }

    public void cancel(String taskId) {
        cancelledTasks.computeIfAbsent(taskId, key -> new AtomicBoolean(false)).set(true);
    }

    public boolean isCancelled(String taskId) {
        AtomicBoolean cancelled = cancelledTasks.get(taskId);
        return cancelled != null && cancelled.get();
    }
}
