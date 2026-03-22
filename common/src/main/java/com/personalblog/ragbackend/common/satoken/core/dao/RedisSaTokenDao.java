package com.personalblog.ragbackend.common.satoken.core.dao;

import cn.dev33.satoken.dao.auto.SaTokenDaoBySessionFollowObject;
import cn.dev33.satoken.util.SaFoxUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 版 Sa-Token DAO，用于统一管理令牌和会话对象的存取。
 */
public class RedisSaTokenDao implements SaTokenDaoBySessionFollowObject {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisSaTokenDao(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void set(String key, String value, long timeout) {
        if (timeout == 0 || timeout <= NOT_VALUE_EXPIRE) {
            return;
        }
        if (timeout == NEVER_EXPIRE) {
            redisTemplate.opsForValue().set(key, value);
            return;
        }
        redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(timeout));
    }

    @Override
    public void update(String key, String value) {
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return;
        }
        long timeout = getTimeout(key);
        set(key, value, timeout == NEVER_EXPIRE ? NEVER_EXPIRE : timeout);
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public long getTimeout(String key) {
        Long timeout = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return timeout == null ? NOT_VALUE_EXPIRE : timeout;
    }

    @Override
    public void updateTimeout(String key, long timeout) {
        if (timeout == NEVER_EXPIRE) {
            redisTemplate.persist(key);
            return;
        }
        redisTemplate.expire(key, Duration.ofSeconds(timeout));
    }

    @Override
    public Object getObject(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        try {
            StoredObject storedObject = objectMapper.readValue(value, StoredObject.class);
            if (storedObject.getClassName() == null || storedObject.getData() == null) {
                return null;
            }
            Class<?> clazz = Class.forName(storedObject.getClassName());
            return objectMapper.treeToValue(storedObject.getData(), clazz);
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("读取 Sa-Token 对象失败", e);
        }
    }

    @Override
    public <T> T getObject(String key, Class<T> classType) {
        Object object = getObject(key);
        if (object == null) {
            return null;
        }
        return classType.cast(object);
    }

    @Override
    public void setObject(String key, Object object, long timeout) {
        if (timeout == 0 || timeout <= NOT_VALUE_EXPIRE || object == null) {
            return;
        }
        try {
            StoredObject storedObject = new StoredObject(object.getClass().getName(), objectMapper.valueToTree(object));
            String json = objectMapper.writeValueAsString(storedObject);
            set(key, json, timeout);
        } catch (IOException e) {
            throw new IllegalStateException("写入 Sa-Token 对象失败", e);
        }
    }

    @Override
    public void updateObject(String key, Object object) {
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return;
        }
        long timeout = getObjectTimeout(key);
        setObject(key, object, timeout == NEVER_EXPIRE ? NEVER_EXPIRE : timeout);
    }

    @Override
    public void deleteObject(String key) {
        delete(key);
    }

    @Override
    public long getObjectTimeout(String key) {
        return getTimeout(key);
    }

    @Override
    public void updateObjectTimeout(String key, long timeout) {
        updateTimeout(key, timeout);
    }

    @Override
    public List<String> searchData(String prefix, String keyword, int start, int size, boolean sortType) {
        Collection<String> keys = redisTemplate.keys(prefix + "*" + keyword + "*");
        List<String> list = keys == null ? new ArrayList<>() : new ArrayList<>(keys);
        return SaFoxUtil.searchList(list, start, size, sortType);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static final class StoredObject {
        private String className;
        private JsonNode data;

        public StoredObject() {
        }

        private StoredObject(String className, JsonNode data) {
            this.className = className;
            this.data = data;
        }

        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public JsonNode getData() { return data; }
        public void setData(JsonNode data) { this.data = data; }
    }
}
