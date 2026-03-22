package com.personalblog.ragbackend.common.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类，封装项目内通用的字符串和对象读写操作。
 */
@Component
public class RedisClient {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisClient(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    public <T> void setObject(String key, T value) {
        set(key, writeValue(value));
    }

    public <T> void setObject(String key, T value, Duration ttl) {
        set(key, writeValue(value), ttl);
    }

    public <T> Optional<T> getObject(String key, Class<T> clazz) {
        return get(key).map(json -> readValue(json, clazz));
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public boolean expire(String key, Duration ttl) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, ttl));
    }

    public long getExpireSeconds(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl == null ? -2L : ttl;
    }

    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    public void putHash(String key, String hashKey, String value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    public Optional<String> getHash(String key, String hashKey) {
        Object value = redisTemplate.opsForHash().get(key, hashKey);
        return Optional.ofNullable(value).map(Object::toString);
    }

    public Map<Object, Object> entries(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化 Redis 值失败", e);
        }
    }

    private <T> T readValue(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("反序列化 Redis 值失败", e);
        }
    }
}
