package com.personalblog.ragbackend.knowledge.service.rag;

import com.personalblog.ragbackend.common.web.sse.SseEmitterSender;
import com.personalblog.ragbackend.infra.ai.chat.StreamCancellationHandle;
import com.personalblog.ragbackend.knowledge.dto.stream.CompletionPayload;
import com.personalblog.ragbackend.knowledge.enums.SseEventType;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Component
public class StreamTaskManager {
    private final ConcurrentMap<String, StreamTaskInfo> tasks = new ConcurrentHashMap<>();

    public void register(String taskId) {
        tasks.put(taskId, new StreamTaskInfo());
    }

    public void register(String taskId, SseEmitterSender sender, Supplier<CompletionPayload> onCancelSupplier) {
        StreamTaskInfo taskInfo = tasks.computeIfAbsent(taskId, key -> new StreamTaskInfo());
        taskInfo.sender = sender;
        taskInfo.onCancelSupplier = onCancelSupplier;
        if (taskInfo.cancelled.get()) {
            sendCancelAndDone(sender, onCancelSupplier == null ? null : onCancelSupplier.get());
            sender.complete();
        }
    }

    public void bindHandle(String taskId, StreamCancellationHandle handle) {
        StreamTaskInfo taskInfo = tasks.computeIfAbsent(taskId, key -> new StreamTaskInfo());
        taskInfo.handle = handle;
        if (taskInfo.cancelled.get() && handle != null) {
            handle.cancel();
        }
    }

    public void unregister(String taskId) {
        tasks.remove(taskId);
    }

    public void cancel(String taskId) {
        StreamTaskInfo taskInfo = tasks.computeIfAbsent(taskId, key -> new StreamTaskInfo());
        if (!taskInfo.cancelled.compareAndSet(false, true)) {
            return;
        }
        if (taskInfo.handle != null) {
            taskInfo.handle.cancel();
        }
        if (taskInfo.sender != null) {
            CompletionPayload payload = taskInfo.onCancelSupplier == null ? null : taskInfo.onCancelSupplier.get();
            sendCancelAndDone(taskInfo.sender, payload);
            taskInfo.sender.complete();
        }
    }

    public boolean isCancelled(String taskId) {
        StreamTaskInfo taskInfo = tasks.get(taskId);
        return taskInfo != null && taskInfo.cancelled.get();
    }

    private void sendCancelAndDone(SseEmitterSender sender, CompletionPayload payload) {
        CompletionPayload actualPayload = payload == null ? new CompletionPayload(null, null) : payload;
        sender.sendEvent(SseEventType.CANCEL.value(), actualPayload);
        sender.sendEvent(SseEventType.DONE.value(), "[DONE]");
    }

    private static final class StreamTaskInfo {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile StreamCancellationHandle handle;
        private volatile SseEmitterSender sender;
        private volatile Supplier<CompletionPayload> onCancelSupplier;
    }
}
