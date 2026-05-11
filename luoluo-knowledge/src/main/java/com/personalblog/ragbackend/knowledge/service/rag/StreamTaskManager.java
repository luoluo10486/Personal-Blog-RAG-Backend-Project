package com.personalblog.ragbackend.knowledge.service.rag;

import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.common.redis.RedisClient;
import com.personalblog.ragbackend.common.web.sse.SseEmitterSender;
import com.personalblog.ragbackend.infra.ai.chat.StreamCancellationHandle;
import com.personalblog.ragbackend.knowledge.dto.stream.CompletionPayload;
import com.personalblog.ragbackend.knowledge.enums.SseEventType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Component
public class StreamTaskManager {
    private static final String CANCEL_TOPIC = "ragent:stream:cancel";
    private static final String CANCEL_KEY_PREFIX = "ragent:stream:cancel:";
    private static final Duration CANCEL_TTL = Duration.ofMinutes(30);

    private final ConcurrentMap<String, StreamTaskInfo> tasks = new ConcurrentHashMap<>();
    private final RedisClient redisClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final ChannelTopic cancelTopic = new ChannelTopic(CANCEL_TOPIC);
    private final MessageListener cancelListener = new CancelMessageListener();

    public StreamTaskManager(RedisClient redisClient,
                             StringRedisTemplate stringRedisTemplate,
                             RedisMessageListenerContainer redisMessageListenerContainer) {
        this.redisClient = redisClient;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisMessageListenerContainer = redisMessageListenerContainer;
    }

    @PostConstruct
    public void subscribe() {
        redisMessageListenerContainer.addMessageListener(cancelListener, cancelTopic);
    }

    @PreDestroy
    public void unsubscribe() {
        redisMessageListenerContainer.removeMessageListener(cancelListener, cancelTopic);
    }

    public void register(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            return;
        }
        StreamTaskInfo taskInfo = tasks.computeIfAbsent(taskId, key -> new StreamTaskInfo());
        if (isTaskCancelledInRedis(taskId, taskInfo)) {
            taskInfo.cancelled.set(true);
        }
    }

    public void register(String taskId, SseEmitterSender sender, Supplier<CompletionPayload> onCancelSupplier) {
        if (StrUtil.isBlank(taskId)) {
            return;
        }
        StreamTaskInfo taskInfo = tasks.computeIfAbsent(taskId, key -> new StreamTaskInfo());
        taskInfo.sender = sender;
        taskInfo.onCancelSupplier = onCancelSupplier;
        if (isTaskCancelledInRedis(taskId, taskInfo)) {
            sendCancelAndDone(sender, onCancelSupplier == null ? null : onCancelSupplier.get());
            sender.complete();
        }
    }

    public void bindHandle(String taskId, StreamCancellationHandle handle) {
        if (StrUtil.isBlank(taskId)) {
            return;
        }
        StreamTaskInfo taskInfo = tasks.computeIfAbsent(taskId, key -> new StreamTaskInfo());
        taskInfo.handle = handle;
        if (taskInfo.cancelled.get() && handle != null) {
            handle.cancel();
        }
    }

    public void unregister(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            return;
        }
        tasks.remove(taskId);
        redisClient.delete(cancelKey(taskId));
    }

    public void cancel(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            return;
        }
        redisClient.set(cancelKey(taskId), Boolean.TRUE.toString(), CANCEL_TTL);
        stringRedisTemplate.convertAndSend(CANCEL_TOPIC, taskId);
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

    private boolean isTaskCancelledInRedis(String taskId, StreamTaskInfo taskInfo) {
        if (taskInfo.cancelled.get()) {
            return true;
        }
        boolean cancelled = Boolean.parseBoolean(redisClient.get(cancelKey(taskId)).orElse(Boolean.FALSE.toString()));
        if (cancelled) {
            taskInfo.cancelled.set(true);
        }
        return cancelled;
    }

    private void cancelLocal(String taskId) {
        StreamTaskInfo taskInfo = tasks.get(taskId);
        if (taskInfo == null) {
            return;
        }
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

    private String cancelKey(String taskId) {
        return CANCEL_KEY_PREFIX + taskId;
    }

    private static final class StreamTaskInfo {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile StreamCancellationHandle handle;
        private volatile SseEmitterSender sender;
        private volatile Supplier<CompletionPayload> onCancelSupplier;
    }

    private final class CancelMessageListener implements MessageListener {
        @Override
        public void onMessage(Message message, byte[] pattern) {
            String taskId = new String(message.getBody(), StandardCharsets.UTF_8);
            if (StrUtil.isBlank(taskId)) {
                return;
            }
            cancelLocal(taskId);
        }
    }
}
