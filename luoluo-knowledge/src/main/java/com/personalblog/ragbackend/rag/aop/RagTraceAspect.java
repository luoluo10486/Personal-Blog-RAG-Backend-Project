package com.personalblog.ragbackend.rag.aop;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalblog.ragbackend.common.auth.service.AuthSessionService;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.ingestion.domain.result.IngestionResult;
import com.personalblog.ragbackend.knowledge.dao.entity.RagTraceNodeEntity;
import com.personalblog.ragbackend.knowledge.dao.entity.RagTraceRunEntity;
import com.personalblog.ragbackend.knowledge.dto.KnowledgeAskResponse;
import com.personalblog.ragbackend.knowledge.trace.RagTraceContext;
import com.personalblog.ragbackend.knowledge.trace.RagTraceNode;
import com.personalblog.ragbackend.knowledge.trace.RagTraceRoot;
import com.personalblog.ragbackend.rag.config.RagTraceProperties;
import com.personalblog.ragbackend.rag.service.RagTraceRecordService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RagTraceAspect {
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_ERROR = "ERROR";

    private final RagTraceRecordService traceRecordService;
    private final RagTraceProperties traceProperties;
    private final AuthSessionService authSessionService;
    private final ObjectMapper objectMapper;

    public RagTraceAspect(RagTraceRecordService traceRecordService,
                          RagTraceProperties traceProperties,
                          AuthSessionService authSessionService,
                          ObjectMapper objectMapper) {
        this.traceRecordService = traceRecordService;
        this.traceProperties = traceProperties;
        this.authSessionService = authSessionService;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(traceRoot)")
    public Object aroundRoot(ProceedingJoinPoint joinPoint, RagTraceRoot traceRoot) throws Throwable {
        if (!traceProperties.isEnabled()) {
            return joinPoint.proceed();
        }

        String existingTraceId = RagTraceContext.getTraceId();
        if (StrUtil.isNotBlank(existingTraceId)) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String traceId = IdUtil.getSnowflakeNextIdStr();
        String traceName = StrUtil.blankToDefault(traceRoot.name(), method.getName());
        String conversationId = resolveStringArg(signature, joinPoint.getArgs(), traceRoot.conversationIdArg());
        String taskId = resolveStringArg(signature, joinPoint.getArgs(), traceRoot.taskIdArg());
        Long userId = resolveCurrentUserId();
        LocalDateTime startedAt = LocalDateTime.now();
        long startMillis = System.currentTimeMillis();

        RagTraceRunEntity run = new RagTraceRunEntity();
        run.traceId = traceId;
        run.traceName = traceName;
        run.entryMethod = method.getDeclaringClass().getName() + "#" + method.getName();
        run.conversationId = conversationId;
        run.taskId = taskId;
        run.userId = userId;
        run.status = STATUS_RUNNING;
        run.startedAt = startedAt;
        run.createdAt = startedAt;
        run.updatedAt = startedAt;
        run.deleted = 0;
        traceRecordService.startRun(run);

        RagTraceContext.setTraceId(traceId);
        RagTraceContext.setTaskId(taskId);
        try {
            Object result = joinPoint.proceed();
            String status = STATUS_SUCCESS;
            String errorMessage = null;
            if (result instanceof IngestionResult ingestionResult
                    && ingestionResult.getStatus() != null
                    && ingestionResult.getStatus().name().equalsIgnoreCase("FAILED")) {
                status = STATUS_ERROR;
                errorMessage = ingestionResult.getMessage();
            }
            traceRecordService.finishRun(
                    traceId,
                    status,
                    errorMessage,
                    LocalDateTime.now(),
                    System.currentTimeMillis() - startMillis,
                    buildRunExtraData(result)
            );
            return result;
        } catch (Throwable throwable) {
            traceRecordService.finishRun(
                    traceId,
                    STATUS_ERROR,
                    truncateError(throwable),
                    LocalDateTime.now(),
                    System.currentTimeMillis() - startMillis,
                    null
            );
            throw throwable;
        } finally {
            RagTraceContext.clear();
        }
    }

    @Around("@annotation(traceNode)")
    public Object aroundNode(ProceedingJoinPoint joinPoint, RagTraceNode traceNode) throws Throwable {
        if (!traceProperties.isEnabled()) {
            return joinPoint.proceed();
        }
        String traceId = RagTraceContext.getTraceId();
        if (StrUtil.isBlank(traceId)) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String nodeId = IdUtil.getSnowflakeNextIdStr();
        String parentNodeId = RagTraceContext.currentNodeId();
        int depth = RagTraceContext.depth();
        LocalDateTime startedAt = LocalDateTime.now();
        long startMillis = System.currentTimeMillis();

        RagTraceNodeEntity node = new RagTraceNodeEntity();
        node.traceId = traceId;
        node.nodeId = nodeId;
        node.parentNodeId = parentNodeId;
        node.depth = depth;
        node.nodeType = StrUtil.blankToDefault(traceNode.type(), "METHOD");
        node.nodeName = StrUtil.blankToDefault(traceNode.name(), method.getName());
        node.className = method.getDeclaringClass().getName();
        node.methodName = method.getName();
        node.status = STATUS_RUNNING;
        node.startedAt = startedAt;
        node.createdAt = startedAt;
        node.updatedAt = startedAt;
        node.deleted = 0;
        traceRecordService.startNode(node);

        RagTraceContext.pushNode(nodeId);
        try {
            Object result = joinPoint.proceed();
            traceRecordService.finishNode(
                    traceId,
                    nodeId,
                    STATUS_SUCCESS,
                    null,
                    LocalDateTime.now(),
                    System.currentTimeMillis() - startMillis,
                    null
            );
            return result;
        } catch (Throwable throwable) {
            traceRecordService.finishNode(
                    traceId,
                    nodeId,
                    STATUS_ERROR,
                    truncateError(throwable),
                    LocalDateTime.now(),
                    System.currentTimeMillis() - startMillis,
                    null
            );
            throw throwable;
        } finally {
            RagTraceContext.popNode();
        }
    }

    private String resolveStringArg(MethodSignature signature, Object[] args, String argName) {
        if (StrUtil.isBlank(argName) || args == null || args.length == 0) {
            return null;
        }
        String[] parameterNames = signature.getParameterNames();
        if (parameterNames != null && parameterNames.length == args.length) {
            for (int index = 0; index < parameterNames.length; index++) {
                if (!argName.equals(parameterNames[index])) {
                    continue;
                }
                Object arg = args[index];
                return arg == null ? null : String.valueOf(arg);
            }
        }
        for (Object arg : args) {
            String value = resolveFieldValue(arg, argName);
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String resolveFieldValue(Object target, String fieldName) {
        if (target == null || StrUtil.isBlank(fieldName)) {
            return null;
        }
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(target);
            return value == null ? null : String.valueOf(value);
        } catch (ReflectiveOperationException ignored) {
            try {
                Method getter = target.getClass().getMethod("get" + capitalize(fieldName));
                Object value = getter.invoke(target);
                return value == null ? null : String.valueOf(value);
            } catch (ReflectiveOperationException ignoredAgain) {
                return null;
            }
        }
    }

    private String capitalize(String value) {
        if (StrUtil.isBlank(value)) {
            return value;
        }
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    private Long resolveCurrentUserId() {
        try {
            Long currentSubjectId = authSessionService.getCurrentSubjectId();
            if (currentSubjectId != null) {
                return currentSubjectId;
            }
        } catch (RuntimeException ignored) {
        }
        String userId = UserContext.getUserId();
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String buildRunExtraData(Object result) {
        if (!(result instanceof KnowledgeAskResponse response)) {
            return null;
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("route", response.trace() == null ? null : response.trace().route());
        extra.put("collectionName", response.trace() == null ? null : response.trace().collectionName());
        extra.put("requestedTopK", response.trace() == null ? null : response.trace().requestedTopK());
        extra.put("citationCount", response.citations() == null ? 0 : response.citations().size());
        extra.put("rewrittenQuestion", response.trace() == null ? null : response.trace().rewrittenQuestion());
        extra.put("originalQuestion", response.trace() == null ? null : response.trace().question());
        try {
            return objectMapper.writeValueAsString(extra);
        } catch (Exception ignored) {
            return null;
        }
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
}
