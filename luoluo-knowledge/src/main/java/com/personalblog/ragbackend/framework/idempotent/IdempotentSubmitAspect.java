package com.personalblog.ragbackend.framework.idempotent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.google.gson.Gson;
import com.personalblog.ragbackend.common.context.UserContext;
import com.personalblog.ragbackend.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Aspect
@Component
@RequiredArgsConstructor
public final class IdempotentSubmitAspect {
    private static final String IDEMPOTENT_PREFIX = "idempotent-submit:";
    private final RedissonClient redissonClient;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final Gson gson = new Gson();

    @Around("@annotation(com.personalblog.ragbackend.framework.idempotent.IdempotentSubmit)")
    public Object idempotentSubmit(ProceedingJoinPoint joinPoint) throws Throwable {
        IdempotentSubmit idempotentSubmit = getIdempotentSubmitAnnotation(joinPoint);
        String lockKey = buildLockKey(joinPoint, idempotentSubmit);
        RLock lock = redissonClient.getLock(lockKey);
        if (!lock.tryLock()) {
            throw new ClientException(idempotentSubmit.message());
        }
        try {
            return joinPoint.proceed();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public static IdempotentSubmit getIdempotentSubmitAnnotation(ProceedingJoinPoint joinPoint) throws NoSuchMethodException {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method targetMethod = joinPoint.getTarget().getClass().getDeclaredMethod(methodSignature.getName(), methodSignature.getMethod().getParameterTypes());
        return targetMethod.getAnnotation(IdempotentSubmit.class);
    }

    private String getServletPath() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return Objects.requireNonNull(attributes).getRequest().getServletPath();
    }

    private String getCurrentUserId() {
        return UserContext.getUserId();
    }

    private String calcArgsMD5(ProceedingJoinPoint joinPoint) {
        return DigestUtil.md5Hex(gson.toJson(joinPoint.getArgs()).getBytes(StandardCharsets.UTF_8));
    }

    private String buildLockKey(ProceedingJoinPoint joinPoint, IdempotentSubmit idempotentSubmit) {
        if (idempotentSubmit != null && StrUtil.isNotBlank(idempotentSubmit.key())) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Object keyValue = parseKey(idempotentSubmit.key(), signature.getMethod(), joinPoint.getArgs());
            return IDEMPOTENT_PREFIX + (keyValue == null ? "" : keyValue);
        }
        return String.format("%spath:%s:currentUserId:%s:md5:%s",
                IDEMPOTENT_PREFIX,
                getServletPath(),
                getCurrentUserId(),
                calcArgsMD5(joinPoint));
    }

    private Object parseKey(String keyExpression, Method method, Object[] args) {
        try {
            EvaluationContext context = new StandardEvaluationContext();
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    context.setVariable("p" + i, args[i]);
                    context.setVariable("a" + i, args[i]);
                }
            }
            if (method != null) {
                String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
                if (parameterNames != null && args != null) {
                    for (int i = 0; i < parameterNames.length && i < args.length; i++) {
                        if (StrUtil.isNotBlank(parameterNames[i])) {
                            context.setVariable(parameterNames[i], args[i]);
                        }
                    }
                }
            }
            return expressionParser.parseExpression(keyExpression).getValue(context);
        } catch (Exception ignored) {
            return "";
        }
    }
}
