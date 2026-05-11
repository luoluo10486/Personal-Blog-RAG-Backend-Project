package com.personalblog.ragbackend.rag.aop;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.common.web.sse.SseEmitterSender;
import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import com.personalblog.ragbackend.rag.config.RagRateLimitProperties;
import com.personalblog.ragbackend.knowledge.dto.stream.CompletionPayload;
import com.personalblog.ragbackend.knowledge.dto.stream.MessageDelta;
import com.personalblog.ragbackend.knowledge.dto.stream.MetaPayload;
import com.personalblog.ragbackend.knowledge.enums.SseEventType;
import com.personalblog.ragbackend.rag.service.ConversationPersistResult;
import com.personalblog.ragbackend.rag.service.RagConversationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RScript;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatQueueLimiter {
    private static final String REJECT_MESSAGE = "系统繁忙，请稍后再试";
    private static final String RESPONSE_TYPE = "response";
    private static final String SEMAPHORE_NAME = "rag:global:chat";
    private static final String QUEUE_KEY = "rag:global:chat:queue";
    private static final String QUEUE_SEQ_KEY = "rag:global:chat:queue:seq";
    private static final String NOTIFY_TOPIC = "rag:global:chat:queue:notify";
    private static final String CLAIM_LUA_PATH = "lua/queue_claim_atomic.lua";

    private final RedissonClient redissonClient;
    private final RagRateLimitProperties rateLimitProperties;
    private final RagConversationService ragConversationService;
    private final KnowledgeProperties knowledgeProperties;
    @Qualifier("chatEntryExecutor")
    private final Executor chatEntryExecutor;
    private final String claimLua = loadLuaScript();
    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(
            1,
            r -> {
                Thread thread = new Thread(r);
                thread.setName("chat_queue_scheduler");
                thread.setDaemon(true);
                return thread;
            }
    );
    private volatile int notifyListenerId = -1;
    private volatile PollNotifier pollNotifier;

    @PostConstruct
    public void subscribeQueueNotify() {
        pollNotifier = new PollNotifier(this::availablePermits);
        pollNotifier.startCleanup();
        RTopic topic = redissonClient.getTopic(NOTIFY_TOPIC);
        notifyListenerId = topic.addListener(String.class, (channel, msg) -> {
            if (pollNotifier != null) {
                pollNotifier.fire();
            }
        });
    }

    public void enqueue(String question, String conversationId, SseEmitter emitter, Runnable onAcquire) {
        if (!isGlobalEnabled()) {
            chatEntryExecutor.execute(onAcquire);
            return;
        }

        String userId = resolveUserId();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicReference<String> permitRef = new AtomicReference<>();
        String requestId = IdUtil.getSnowflakeNextIdStr();
        RScoredSortedSet<String> queue = redissonClient.getScoredSortedSet(QUEUE_KEY, StringCodec.INSTANCE);
        long seq = nextQueueSeq();
        queue.add(seq, requestId);
        Runnable releaseOnce = () -> {
            cancelled.set(true);
            queue.remove(requestId);
            String permitId = permitRef.getAndSet(null);
            if (permitId != null) {
                redissonClient.getPermitExpirableSemaphore(SEMAPHORE_NAME).release(permitId);
                publishQueueNotify();
            }
        };

        emitter.onCompletion(releaseOnce);
        emitter.onTimeout(releaseOnce);
        emitter.onError(e -> releaseOnce.run());

        if (tryAcquireIfReady(queue, requestId, permitRef, cancelled, onAcquire)) {
            return;
        }

        scheduleQueuePoll(queue, requestId, permitRef, cancelled, question, conversationId, userId, emitter, onAcquire);
    }

    private void scheduleQueuePoll(RScoredSortedSet<String> queue,
                                   String requestId,
                                   AtomicReference<String> permitRef,
                                   AtomicBoolean cancelled,
                                   String question,
                                   String conversationId,
                                   String userId,
                                   SseEmitter emitter,
                                   Runnable onAcquire) {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(resolveMaxWaitSeconds());
        int intervalMs = Math.max(50, resolvePollIntervalMs());
        PollNotifier notifier = pollNotifier;
        ScheduledFuture<?>[] futureRef = new ScheduledFuture<?>[1];

        Runnable poller = () -> {
            if (cancelled.get()) {
                if (notifier != null) {
                    notifier.unregister(requestId);
                }
                cancelFuture(futureRef[0]);
                return;
            }
            if (System.currentTimeMillis() > deadline) {
                queue.remove(requestId);
                publishQueueNotify();
                if (notifier != null) {
                    notifier.unregister(requestId);
                }
                cancelFuture(futureRef[0]);
                if (!cancelled.get()) {
                    RejectedContext rejectedContext = recordRejectedConversation(question, conversationId, userId);
                    sendRejectEvents(emitter, rejectedContext);
                }
                return;
            }
            if (tryAcquireIfReady(queue, requestId, permitRef, cancelled, onAcquire)) {
                if (notifier != null) {
                    notifier.unregister(requestId);
                }
                cancelFuture(futureRef[0]);
            }
        };

        futureRef[0] = scheduler.scheduleAtFixedRate(poller, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        if (notifier != null) {
            notifier.register(requestId, poller);
        }
    }

    private boolean tryAcquireIfReady(RScoredSortedSet<String> queue,
                                      String requestId,
                                      AtomicReference<String> permitRef,
                                      AtomicBoolean cancelled,
                                      Runnable onAcquire) {
        if (cancelled.get()) {
            return false;
        }
        int availablePermits = availablePermits();
        if (availablePermits <= 0) {
            return false;
        }
        ClaimResult claimResult = claimIfReady(queue, requestId, availablePermits);
        if (!claimResult.claimed) {
            return false;
        }
        String permitId = tryAcquirePermit();
        if (permitId == null) {
            long newSeq = nextQueueSeq();
            queue.add(newSeq, requestId);
            publishQueueNotify();
            return false;
        }
        permitRef.set(permitId);
        if (cancelled.get()) {
            releasePermit(permitId, permitRef);
            return false;
        }
        publishQueueNotify();
        try {
            chatEntryExecutor.execute(() -> runOnAcquire(onAcquire));
        } catch (RuntimeException ex) {
            releasePermit(permitId, permitRef);
            if (!cancelled.get()) {
                long newSeq = nextQueueSeq();
                queue.add(newSeq, requestId);
                publishQueueNotify();
            }
            log.warn("排队后提交任务失败，已释放 permit 并重新入队", ex);
            return false;
        }
        return true;
    }

    private String tryAcquirePermit() {
        RPermitExpirableSemaphore semaphore = redissonClient.getPermitExpirableSemaphore(SEMAPHORE_NAME);
        semaphore.trySetPermits(resolveMaxConcurrent());
        try {
            return semaphore.tryAcquire(0, resolveLeaseSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private int availablePermits() {
        RPermitExpirableSemaphore semaphore = redissonClient.getPermitExpirableSemaphore(SEMAPHORE_NAME);
        semaphore.trySetPermits(resolveMaxConcurrent());
        return semaphore.availablePermits();
    }

    private ClaimResult claimIfReady(RScoredSortedSet<String> queue, String requestId, int availablePermits) {
        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        List<Object> result = script.eval(
                RScript.Mode.READ_WRITE,
                claimLua,
                RScript.ReturnType.LIST,
                List.of(queue.getName()),
                requestId,
                String.valueOf(availablePermits)
        );
        if (result == null || result.isEmpty()) {
            return ClaimResult.notClaimed();
        }
        long okValue = parseLong(result.get(0));
        if (okValue != 1L || result.size() < 2) {
            return ClaimResult.notClaimed();
        }
        return new ClaimResult(true, parseLong(result.get(1)));
    }

    private long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private long nextQueueSeq() {
        RAtomicLong seq = redissonClient.getAtomicLong(QUEUE_SEQ_KEY);
        return seq.incrementAndGet();
    }

    private void cancelFuture(ScheduledFuture<?> future) {
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
        }
    }

    private void publishQueueNotify() {
        redissonClient.getTopic(NOTIFY_TOPIC).publish("permit_released");
    }

    private RejectedContext recordRejectedConversation(String question, String conversationId, String userId) {
        if (StrUtil.isBlank(question)) {
            return null;
        }
        String actualConversationId = StrUtil.isBlank(conversationId)
                ? IdUtil.getSnowflakeNextIdStr()
                : conversationId;
        if (StrUtil.isBlank(userId)) {
            try {
                userId = StpUtil.getLoginIdAsString();
            } catch (Exception ignored) {
                return null;
            }
        }

        ConversationPersistResult result = ragConversationService.persistExchange(
                actualConversationId,
                question,
                REJECT_MESSAGE,
                knowledgeProperties.getDefaultBaseCode(),
                0
        );
        String title = result == null ? null : result.conversationTitle();
        String messageId = result == null ? null : result.assistantMessageId();
        return new RejectedContext(actualConversationId, IdUtil.getSnowflakeNextIdStr(), messageId, title);
    }

    private void sendRejectEvents(SseEmitter emitter, RejectedContext rejectedContext) {
        SseEmitterSender sender = new SseEmitterSender(emitter);
        if (rejectedContext != null) {
            sender.sendEvent(SseEventType.META.value(), new MetaPayload(rejectedContext.conversationId, rejectedContext.taskId));
            sender.sendEvent(SseEventType.REJECT.value(), new MessageDelta(RESPONSE_TYPE, REJECT_MESSAGE));
            sender.sendEvent(SseEventType.FINISH.value(), new CompletionPayload(rejectedContext.messageId, rejectedContext.title));
        }
        sender.sendEvent(SseEventType.DONE.value(), "[DONE]");
        sender.complete();
    }

    private boolean isGlobalEnabled() {
        return !Boolean.FALSE.equals(rateLimitProperties.getGlobalEnabled());
    }

    private int resolveMaxConcurrent() {
        return Math.max(1, Objects.requireNonNullElse(rateLimitProperties.getGlobalMaxConcurrent(), 50));
    }

    private int resolveMaxWaitSeconds() {
        return Math.max(1, Objects.requireNonNullElse(rateLimitProperties.getGlobalMaxWaitSeconds(), 20));
    }

    private int resolveLeaseSeconds() {
        return Math.max(1, Objects.requireNonNullElse(rateLimitProperties.getGlobalLeaseSeconds(), 600));
    }

    private int resolvePollIntervalMs() {
        return Math.max(50, Objects.requireNonNullElse(rateLimitProperties.getGlobalPollIntervalMs(), 200));
    }

    private void releasePermit(String permitId, AtomicReference<String> permitRef) {
        if (permitRef.compareAndSet(permitId, null)) {
            redissonClient.getPermitExpirableSemaphore(SEMAPHORE_NAME).release(permitId);
            publishQueueNotify();
        }
    }

    private String loadLuaScript() {
        try {
            ClassPathResource resource = new ClassPathResource(CLAIM_LUA_PATH);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load lua script: " + CLAIM_LUA_PATH, ex);
        }
    }

    private void runOnAcquire(Runnable onAcquire) {
        try {
            onAcquire.run();
        } catch (Exception ex) {
            log.warn("执行排队后入口失败", ex);
        }
    }

    private String resolveUserId() {
        String userId = UserContext.getUserId();
        if (StrUtil.isNotBlank(userId)) {
            return userId;
        }
        try {
            return StpUtil.getLoginIdAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    @PreDestroy
    public void shutdown() {
        if (notifyListenerId != -1) {
            redissonClient.getTopic(NOTIFY_TOPIC).removeListener(notifyListenerId);
        }
        scheduler.shutdown();
        awaitSchedulerShutdown();
        if (pollNotifier != null) {
            pollNotifier.shutdown();
        }
    }

    private void awaitSchedulerShutdown() {
        try {
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException ex) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private record RejectedContext(String conversationId, String taskId, String messageId, String title) {
    }

    private record ClaimResult(boolean claimed, long score) {
        static ClaimResult notClaimed() {
            return new ClaimResult(false, 0L);
        }
    }

    private static final class PollNotifier {
        private final IntSupplier permitSupplier;
        private final ScheduledExecutorService notifyExecutor = new ScheduledThreadPoolExecutor(
                1,
                r -> {
                    Thread thread = new Thread(r);
                    thread.setName("chat_queue_notify");
                    thread.setDaemon(true);
                    return thread;
                }
        );
        private final java.util.concurrent.ConcurrentHashMap<String, PollerEntry> pollers = new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.concurrent.atomic.AtomicBoolean firing = new java.util.concurrent.atomic.AtomicBoolean(false);
        private final ScheduledExecutorService cleanupExecutor = new ScheduledThreadPoolExecutor(
                1,
                r -> {
                    Thread thread = new Thread(r);
                    thread.setName("chat_queue_cleanup");
                    thread.setDaemon(true);
                    return thread;
                }
        );
        private final java.util.concurrent.atomic.AtomicInteger pendingNotifications = new java.util.concurrent.atomic.AtomicInteger(0);

        PollNotifier(IntSupplier permitSupplier) {
            this.permitSupplier = permitSupplier;
        }

        private record PollerEntry(Runnable poller, long registerTime) {
        }

        void register(String requestId, Runnable poller) {
            pollers.put(requestId, new PollerEntry(poller, System.currentTimeMillis()));
        }

        void unregister(String requestId) {
            pollers.remove(requestId);
        }

        void fire() {
            pendingNotifications.incrementAndGet();
            if (!firing.compareAndSet(false, true)) {
                return;
            }
            notifyExecutor.execute(() -> {
                do {
                    pendingNotifications.set(0);
                    try {
                        if (permitSupplier.getAsInt() <= 0) {
                            continue;
                        }
                        for (PollerEntry entry : pollers.values()) {
                            entry.poller().run();
                        }
                    } finally {
                        firing.set(false);
                    }
                } while (pendingNotifications.get() > 0 && firing.compareAndSet(false, true));
            });
        }

        void shutdown() {
            cleanupExecutor.shutdown();
            notifyExecutor.shutdown();
            awaitExecutorShutdown(cleanupExecutor);
            awaitExecutorShutdown(notifyExecutor);
            pollers.clear();
        }

        private void awaitExecutorShutdown(ScheduledExecutorService executor) {
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        private void startCleanup() {
            cleanupExecutor.scheduleAtFixedRate(() -> {
                long now = System.currentTimeMillis();
                pollers.entrySet().removeIf(entry ->
                        now - entry.getValue().registerTime() > TimeUnit.MINUTES.toMillis(5)
                );
            }, 1, 1, TimeUnit.MINUTES);
        }
    }
}
