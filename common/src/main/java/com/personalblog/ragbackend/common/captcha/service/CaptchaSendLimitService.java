package com.personalblog.ragbackend.common.captcha.service;

import com.personalblog.ragbackend.common.redis.RedisClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * 验证码发送限流服务，负责控制同一目标在固定时间窗口内的发送频率。
 */
@Service
public class CaptchaSendLimitService {
    private static final String SEND_LIMIT_KEY_PREFIX = "auth:send_limit:";

    private final RedisClient redisClient;

    public CaptchaSendLimitService(RedisClient redisClient) {
        this.redisClient = redisClient;
    }

    public boolean tryAcquire(String namespace, String targetType, String targetValue, long intervalSeconds) {
        validate(namespace, targetType, targetValue, intervalSeconds);
        return redisClient.setIfAbsent(
                buildKey(namespace, targetType, targetValue),
                "1",
                Duration.ofSeconds(intervalSeconds)
        );
    }

    public long getRemainingSeconds(String namespace, String targetType, String targetValue) {
        if (namespace == null || namespace.isBlank()
                || targetType == null || targetType.isBlank()
                || targetValue == null || targetValue.isBlank()) {
            return 0L;
        }
        long ttl = redisClient.getExpireSeconds(buildKey(namespace, targetType, targetValue));
        return Math.max(ttl, 0L);
    }

    public void release(String namespace, String targetType, String targetValue) {
        if (namespace == null || namespace.isBlank()
                || targetType == null || targetType.isBlank()
                || targetValue == null || targetValue.isBlank()) {
            return;
        }
        redisClient.delete(buildKey(namespace, targetType, targetValue));
    }

    private void validate(String namespace, String targetType, String targetValue, long intervalSeconds) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("验证码限流命名空间不能为空");
        }
        if (targetType == null || targetType.isBlank()) {
            throw new IllegalArgumentException("验证码限流目标类型不能为空");
        }
        if (targetValue == null || targetValue.isBlank()) {
            throw new IllegalArgumentException("验证码限流目标值不能为空");
        }
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("验证码发送间隔必须大于 0");
        }
    }

    private String buildKey(String namespace, String targetType, String targetValue) {
        return SEND_LIMIT_KEY_PREFIX
                + namespace.trim().toLowerCase(Locale.ROOT)
                + ":" + targetType.trim().toLowerCase(Locale.ROOT)
                + ":" + targetValue.trim().toLowerCase(Locale.ROOT);
    }
}
