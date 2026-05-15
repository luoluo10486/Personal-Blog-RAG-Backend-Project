package com.personalblog.ragbackend.rag.aop;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;

@Slf4j
@Aspect
@Component
public class IdempotentSubmitAspect {
    private static final String IDEMPOTENT_PREFIX = "rag:idempotent:";
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(5);

    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final StringRedisTemplate stringRedisTemplate;

    public IdempotentSubmitAspect(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Around("@annotation(idempotentSubmit)")
    public Object around(ProceedingJoinPoint joinPoint, IdempotentSubmit idempotentSubmit) throws Throwable {
        String key = resolveKey(joinPoint, idempotentSubmit);
        if (StrUtil.isBlank(key)) {
            return joinPoint.proceed();
        }
        String redisKey = IDEMPOTENT_PREFIX + key;
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(redisKey, "1", DEFAULT_TTL);
        if (Boolean.FALSE.equals(acquired)) {
            throw new IllegalStateException(idempotentSubmit.message());
        }
        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            throw throwable;
        }
    }

    private String resolveKey(ProceedingJoinPoint joinPoint, IdempotentSubmit idempotentSubmit) {
        if (idempotentSubmit == null || StrUtil.isBlank(idempotentSubmit.key())) {
            return "";
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodKey = method.getDeclaringClass().getName() + "#" + method.getName();
        String resolvedKey = evaluateKey(joinPoint, method, idempotentSubmit.key());
        if (StrUtil.isBlank(resolvedKey)) {
            return methodKey + ":" + idempotentSubmit.key();
        }
        return methodKey + ":" + resolvedKey;
    }

    private String evaluateKey(ProceedingJoinPoint joinPoint, Method method, String keyExpression) {
        try {
            EvaluationContext context = new StandardEvaluationContext(joinPoint.getTarget());
            if (method != null) {
                ((StandardEvaluationContext) context).setVariable("methodName", method.getName());
            }
            Object[] args = joinPoint.getArgs();
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    context.setVariable("p" + i, args[i]);
                    context.setVariable("a" + i, args[i]);
                }
            }
            if (method != null && parameterNameDiscoverer.getParameterNames(method) != null) {
                String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
                for (int i = 0; i < parameterNames.length && args != null && i < args.length; i++) {
                    String parameterName = parameterNames[i];
                    if (StrUtil.isNotBlank(parameterName)) {
                        context.setVariable(parameterName, args[i]);
                    }
                }
            }
            Object value = expressionParser.parseExpression(keyExpression).getValue(context);
            return value == null ? "" : String.valueOf(value);
        } catch (Exception exception) {
            log.debug("Failed to evaluate idempotent key expression [{}]", keyExpression, exception);
            return "";
        }
    }
}
