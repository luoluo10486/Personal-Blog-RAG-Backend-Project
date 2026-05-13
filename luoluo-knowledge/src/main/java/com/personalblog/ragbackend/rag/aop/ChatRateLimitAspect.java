package com.personalblog.ragbackend.rag.aop;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.knowledge.dao.entity.RagTraceRunEntity;
import com.personalblog.ragbackend.knowledge.trace.RagTraceContext;
import com.personalblog.ragbackend.rag.config.RagTraceProperties;
import com.personalblog.ragbackend.rag.service.RagTraceRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ChatRateLimitAspect {
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_ERROR = "ERROR";

    private final ChatQueueLimiter chatQueueLimiter;
    private final RagTraceProperties traceProperties;
    private final RagTraceRecordService traceRecordService;

    @Around("@annotation(com.personalblog.ragbackend.rag.aop.ChatRateLimit)")
    public Object limitStreamChat(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length < 4 || !(args[3] instanceof SseEmitter emitter)) {
            return joinPoint.proceed();
        }

        String question = args[0] instanceof String text ? text : "";
        String conversationId = args[1] instanceof String cid ? cid : null;
        String actualConversationId = StrUtil.isBlank(conversationId) ? IdUtil.getSnowflakeNextIdStr() : conversationId;
        args[1] = actualConversationId;

        Object target = joinPoint.getTarget();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        chatQueueLimiter.enqueue(question, actualConversationId, emitter, () ->
                invokeWithTrace(method, target, args, question, actualConversationId, emitter)
        );
        return null;
    }

    private void invokeWithTrace(Method method,
                                 Object target,
                                 Object[] args,
                                 String question,
                                 String conversationId,
                                 SseEmitter emitter) {
        if (!traceProperties.isEnabled()) {
            invokeTarget(method, target, args, emitter);
            return;
        }

        String traceId = IdUtil.getSnowflakeNextIdStr();
        String taskId = IdUtil.getSnowflakeNextIdStr();
        long startMillis = System.currentTimeMillis();
        RagTraceRunEntity run = new RagTraceRunEntity();
        run.traceId = traceId;
        run.traceName = "rag-stream-chat";
        run.entryMethod = method.getDeclaringClass().getName() + "#" + method.getName();
        run.conversationId = conversationId;
        run.taskId = taskId;
        run.userId = resolveCurrentUserId();
        run.status = STATUS_RUNNING;
        run.startedAt = LocalDateTime.now();
        run.createdAt = run.startedAt;
        run.updatedAt = run.startedAt;
        run.deleted = 0;
        traceRecordService.startRun(run);

        RagTraceContext.setTraceId(traceId);
        RagTraceContext.setTaskId(taskId);
        try {
            method.invoke(target, args);
            traceRecordService.finishRun(
                    traceId,
                    STATUS_SUCCESS,
                    null,
                    LocalDateTime.now(),
                    System.currentTimeMillis() - startMillis,
                    null
            );
        } catch (Throwable ex) {
            Throwable cause = unwrap(ex);
            traceRecordService.finishRun(
                    traceId,
                    STATUS_ERROR,
                    truncateError(cause),
                    LocalDateTime.now(),
                    System.currentTimeMillis() - startMillis,
                    null
            );
            log.warn("鎵ц娴佸紡瀵硅瘽澶辫触", cause);
            emitter.completeWithError(cause);
        } finally {
            RagTraceContext.clear();
        }
    }

    private void invokeTarget(Method method, Object target, Object[] args, SseEmitter emitter) {
        try {
            method.invoke(target, args);
        } catch (Throwable ex) {
            Throwable cause = unwrap(ex);
            log.warn("鎵ц娴佸紡瀵硅瘽澶辫触", cause);
            emitter.completeWithError(cause);
        }
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof InvocationTargetException invocationTargetException
                && invocationTargetException.getTargetException() != null) {
            return invocationTargetException.getTargetException();
        }
        return throwable;
    }

    private String truncateError(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        String message = throwable.getClass().getSimpleName() + ": " + StrUtil.blankToDefault(throwable.getMessage(), "");
        int maxLength = Math.max(1, traceProperties.getMaxErrorLength());
        if (message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength);
    }

    private Long resolveCurrentUserId() {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            return null;
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
