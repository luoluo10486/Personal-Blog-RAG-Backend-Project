package com.personalblog.ragbackend.rag.core.intent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntentTreeCacheManager {
    private static final String INTENT_TREE_CACHE_KEY = "ragent:intent:tree";
    private static final long CACHE_EXPIRE_DAYS = 7;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public List<IntentNode> getIntentTreeFromCache() {
        try {
            String cacheJson = stringRedisTemplate.opsForValue().get(INTENT_TREE_CACHE_KEY);
            if (cacheJson == null) {
                return null;
            }
            return objectMapper.readValue(cacheJson, new TypeReference<List<IntentNode>>() {});
        } catch (Exception e) {
            log.warn("Failed to read intent tree cache", e);
            return null;
        }
    }

    public void saveIntentTreeToCache(List<IntentNode> roots) {
        try {
            stringRedisTemplate.opsForValue().set(
                    INTENT_TREE_CACHE_KEY,
                    objectMapper.writeValueAsString(roots),
                    CACHE_EXPIRE_DAYS,
                    TimeUnit.DAYS
            );
        } catch (Exception e) {
            log.warn("Failed to save intent tree cache", e);
        }
    }

    public void clearIntentTreeCache() {
        try {
            stringRedisTemplate.delete(INTENT_TREE_CACHE_KEY);
        } catch (Exception e) {
            log.warn("Failed to clear intent tree cache", e);
        }
    }
}
